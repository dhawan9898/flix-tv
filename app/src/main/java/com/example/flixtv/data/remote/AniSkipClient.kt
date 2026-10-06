package com.example.flixtv.data.remote

import android.util.Log
import com.example.flixtv.domain.models.SkipRange
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the real per-episode opening/ending intervals. The anime site only carries
 * chapter markers for some episodes, so this looks the title up on MyAnimeList (Jikan API)
 * for a MAL id, then asks the community AniSkip database for that exact episode.
 * A null half means "no real data" - callers must not offer to skip it or guess a range.
 */
@Singleton
class AniSkipClient @Inject constructor(private val http: HttpFetcher) {

    companion object {
        private const val TAG = "AniSkipClient"
        private const val TIMEOUT_MS = 6_000L
    }

    data class Intervals(val opening: SkipRange?, val ending: SkipRange?)

    // Title -> MAL id; only definitive answers (found / not found) are cached.
    private val malIds = ConcurrentHashMap<String, Int>()
    private val unknownTitles = ConcurrentHashMap.newKeySet<String>()

    suspend fun getIntervals(title: String, episodeNumber: Int, episodeLengthSeconds: Long): Intervals {
        val none = Intervals(null, null)
        if (title.isBlank() || episodeNumber <= 0 || episodeLengthSeconds <= 0) return none
        val malId = resolveMalId(title) ?: return none
        return try {
            val url = "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber" +
                "?types[]=op&types[]=ed&episodeLength=$episodeLengthSeconds"
            val data = JSONObject(http.getText(url, mapOf("Accept" to "application/json"), timeoutMs = TIMEOUT_MS, retries = 0))
            Intervals(extract(data, "op"), extract(data, "ed"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpStatusException) {
            // 404 is AniSkip's "no timestamps for this episode".
            if (e.status != 404) Log.w(TAG, "Skip-time lookup failed: ${e.message}")
            none
        } catch (e: Exception) {
            Log.w(TAG, "Skip-time lookup failed: ${e.message}")
            none
        }
    }

    private suspend fun resolveMalId(title: String): Int? {
        val key = title.trim().lowercase(Locale.US)
        malIds[key]?.let { return it }
        if (key in unknownTitles) return null
        return try {
            val url = "https://api.jikan.moe/v4/anime?q=${URLEncoder.encode(title, "UTF-8")}&limit=1"
            val results = JSONObject(http.getText(url, mapOf("Accept" to "application/json"), timeoutMs = TIMEOUT_MS, retries = 0))
                .optJSONArray("data")
            val id = results?.optJSONObject(0)?.optInt("mal_id", -1) ?: -1
            if (id > 0) malIds[key] = id else unknownTitles.add(key)
            id.takeIf { it > 0 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Transient (timeout, Jikan 429, offline): don't cache, or skip markers stay off all session.
            Log.w(TAG, "MAL id lookup failed: ${e.message}")
            null
        }
    }

    private fun extract(data: JSONObject, skipType: String): SkipRange? {
        val results = data.optJSONArray("results") ?: return null
        for (i in 0 until results.length()) {
            val result = results.optJSONObject(i) ?: continue
            if (result.optString("skipType") != skipType) continue
            val interval = result.optJSONObject("interval") ?: return null
            val start = interval.optDouble("startTime", -1.0)
            val end = interval.optDouble("endTime", -1.0)
            if (start < 0 || end <= start) return null
            return SkipRange((start * 1000).toLong(), (end * 1000).toLong())
        }
        return null
    }
}
