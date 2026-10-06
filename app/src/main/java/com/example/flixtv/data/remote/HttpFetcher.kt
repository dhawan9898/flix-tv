package com.example.flixtv.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

class HttpStatusException(val status: Int, val url: String) : IOException("HTTP $status for $url")

/**
 * The one place provider network requests go through, so every provider gets the same
 * resilience: a hard timeout per attempt, retries with exponential backoff + jitter for
 * failures that can plausibly succeed on a second try (network errors, 408/425/429/5xx),
 * a cap on concurrent requests, and a short-lived response cache for GETs.
 */
@Singleton
class HttpFetcher @Inject constructor() {

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val DEFAULT_TIMEOUT_MS = 15_000L
        private const val DEFAULT_RETRIES = 2
        private const val BACKOFF_BASE_MS = 500L
        private const val BACKOFF_MAX_MS = 5_000L
        private const val MAX_CONCURRENT = 6
        private const val CACHE_MAX_ENTRIES = 150
    }

    private class CacheEntry(val body: String, val expiresAt: Long)

    private val slots = Semaphore(MAX_CONCURRENT)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /** GET [url] and return the body; throws [HttpStatusException] on a non-2xx status. */
    suspend fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        ttlMs: Long = 0,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        retries: Int = DEFAULT_RETRIES
    ): String {
        if (ttlMs > 0) {
            cache[url]?.let { if (it.expiresAt > System.currentTimeMillis()) return it.body }
        }
        var attempt = 0
        while (true) {
            try {
                val body = slots.withPermit { withTimeout(timeoutMs) { fetch(url, headers, timeoutMs) } }
                if (ttlMs > 0) store(url, body, ttlMs)
                return body
            } catch (e: CancellationException) {
                // withTimeout's TimeoutCancellationException is a CancellationException too:
                // only rethrow when the caller itself was cancelled.
                if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                if (attempt >= retries) throw IOException("request timed out after ${timeoutMs}ms: $url")
            } catch (e: HttpStatusException) {
                if (!isRetryable(e.status) || attempt >= retries) throw e
            } catch (e: IOException) {
                if (attempt >= retries) throw e
            }
            delay(backoff(attempt))
            attempt++
        }
    }

    fun invalidate(fragment: String) {
        cache.keys.filter { it.contains(fragment) }.forEach { cache.remove(it) }
    }

    private fun store(url: String, body: String, ttlMs: Long) {
        cache[url] = CacheEntry(body, System.currentTimeMillis() + ttlMs)
        if (cache.size > CACHE_MAX_ENTRIES) {
            cache.entries.minByOrNull { it.value.expiresAt }?.let { cache.remove(it.key) }
        }
    }

    private fun isRetryable(status: Int) = status == 408 || status == 425 || status == 429 || status >= 500

    private fun backoff(attempt: Int): Long {
        val exp = minOf(BACKOFF_BASE_MS * (1L shl attempt), BACKOFF_MAX_MS)
        return exp / 2 + Random.nextLong(exp / 2 + 1)
    }

    private suspend fun fetch(url: String, headers: Map<String, String>, timeoutMs: Long): String =
        withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs.toInt()
                readTimeout = timeoutMs.toInt()
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw HttpStatusException(status, url)
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
}
