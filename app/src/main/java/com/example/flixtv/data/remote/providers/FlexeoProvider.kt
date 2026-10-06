package com.example.flixtv.data.remote.providers

import android.util.Log
import com.example.flixtv.data.remote.HttpFetcher
import com.example.flixtv.domain.models.MediaItem
import com.example.flixtv.domain.models.StreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flexeo provider (movies & TV shows only - anime comes exclusively from [HiAnimeProvider]).
 *
 * Structured like the HiAnime provider: shared [HttpFetcher] (timeouts, retries, cache), real
 * catalog/search results only, and stream resolution that walks the site's mirrors with hedged
 * parallel fallbacks. Nothing here ever substitutes demo content: when no stream can be
 * extracted the real embed page is returned for the web player, and when even that is impossible
 * [SourceUnavailableException] is thrown.
 */
@Singleton
class FlexeoProvider @Inject constructor(
    private val http: HttpFetcher
) {

    companion object {
        private const val TAG = "FlexeoProvider"
        const val PROVIDER_NAME = "Flexeo"
        const val BASE_URL = "https://flexeo.tv"
        const val MIRROR_URL = "https://flexeo.site"
        const val ID_PREFIX = "flexeo_"

        private val MIRRORS = listOf(BASE_URL, MIRROR_URL)
        private const val PAGE_TTL_MS = 5 * 60 * 1000L
        private const val HEDGE_DELAY_MS = 2_500L
        private const val CATALOG_LIMIT = 40
        private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
        private const val TMDB_GENRE_ANIMATION = 16

        private val M3U8_REGEX = Regex("""https?://[^\s"'<>\\]+\.m3u8[^\s"'<>\\]*""")
        private val MP4_REGEX = Regex("""https?://[^\s"'<>\\]+\.mp4[^\s"'<>\\]*""")
        private val PATH_ID_REGEX = Regex("""/(movie|tv|series|film)s?/(?:[a-z0-9-]*-)?(\d+)""", RegexOption.IGNORE_CASE)

        /** Whether [item] is something this provider can resolve (anything that isn't anime). */
        fun handles(item: MediaItem): Boolean = !HiAnimeProvider.isAnime(item)

        internal fun looksLikeAnime(text: String): Boolean = text.lowercase(Locale.US).contains("anime")
    }

    private fun embedUrl(origin: String, tmdbId: Int, isTv: Boolean, season: Int, episode: Int) =
        if (isTv) "$origin/embed/tv/$tmdbId/$season/$episode" else "$origin/embed/movie/$tmdbId"

    // ------------------------------------------------------------- catalog

    /** Featured/trending movies & shows from the site. Empty when no mirror can be reached. */
    suspend fun getCatalog(): List<MediaItem> = withContext(Dispatchers.IO) {
        val html = fetchFromMirrors("/") ?: return@withContext emptyList()
        try {
            parseCards(html).take(CATALOG_LIMIT)
        } catch (e: Exception) {
            Log.w(TAG, "Flexeo catalog parse failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val body = fetchFromMirrors(
            "/api/search?q=${URLEncoder.encode(query.trim().lowercase(Locale.US), "UTF-8")}",
            json = true
        ) ?: return@withContext emptyList()
        try {
            parseSearch(body)
        } catch (e: Exception) {
            Log.w(TAG, "Flexeo search parse failed: ${e.message}")
            emptyList()
        }
    }

    /** First mirror (in order) that answers. */
    private suspend fun fetchFromMirrors(path: String, json: Boolean = false): String? {
        val headers = if (json) mapOf("Accept" to "application/json") else mapOf("Accept" to "text/html,application/xhtml+xml")
        for (origin in MIRRORS) {
            try {
                return http.getText("$origin$path", headers + ("Referer" to "$origin/"), ttlMs = PAGE_TTL_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$origin$path failed: ${e.message}")
            }
        }
        return null
    }

    /** Parses the site's title cards; entries without a TMDB id, or that are anime, are dropped. */
    internal fun parseCards(html: String): List<MediaItem> {
        val doc = Jsoup.parse(html, BASE_URL)
        val seen = HashSet<Int>()
        val items = ArrayList<MediaItem>()
        for (card in doc.select(".movie-card, .film-item, article.item, .flx-card")) {
            val title = card.selectFirst(".title, h3, .film-name")?.text()?.trim().orEmpty()
            val href = (if (card.`is`("a[href]")) card else card.selectFirst("a[href]"))?.attr("href").orEmpty()
            val match = PATH_ID_REGEX.find(href) ?: continue
            val tmdbId = match.groupValues[2].toIntOrNull() ?: continue
            if (title.isBlank() || looksLikeAnime(href) || !seen.add(tmdbId)) continue

            val img = card.selectFirst("img")
            val poster = absolute(img?.attr("data-src").orEmpty().ifBlank { img?.attr("src").orEmpty() })
            if (!poster.startsWith("http")) continue

            val isTv = match.groupValues[1].lowercase(Locale.US).let { it == "tv" || it == "series" }
            items.add(
                MediaItem(
                    id = "$ID_PREFIX$tmdbId",
                    tmdbId = tmdbId,
                    title = title,
                    posterUrl = poster,
                    backdropUrl = poster,
                    // No stream here on purpose: real streams are resolved on play.
                    streamUrl = null,
                    embedUrl = embedUrl(BASE_URL, tmdbId, isTv, 1, 1),
                    synopsis = "Watch $title in HD.",
                    category = if (isTv) "TV Show" else "Movie",
                    provider = PROVIDER_NAME,
                    rating = "HD",
                    releaseYear = "",
                    durationOrEpisodes = if (isTv) "TV Series" else "Movie",
                    qualityTag = "HD"
                )
            )
        }
        return items
    }

    /** Parses the search API (TMDB-shaped JSON), skipping anime. */
    internal fun parseSearch(body: String): List<MediaItem> {
        val root = JSONObject(body)
        val arr: JSONArray = root.optJSONArray("results") ?: root.optJSONArray("data") ?: return emptyList()
        val seen = HashSet<Int>()
        val items = ArrayList<MediaItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val tmdbId = obj.optInt("id", obj.optInt("tmdb_id", 0))
            val title = obj.optString("title").ifBlank { obj.optString("name") }
            val mediaType = obj.optString("media_type")
            if (tmdbId <= 0 || title.isBlank() || mediaType == "person" || !seen.add(tmdbId)) continue
            if (isAnimeEntry(obj, mediaType)) continue

            val posterPath = obj.optString("poster_path")
            if (posterPath.isBlank() || posterPath == "null") continue
            val poster = if (posterPath.startsWith("http")) posterPath else "$TMDB_IMAGE_BASE$posterPath"
            val backdropPath = obj.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
            val isTv = mediaType == "tv" || obj.has("first_air_date")
            val date = obj.optString("release_date").ifBlank { obj.optString("first_air_date") }
            val score = obj.optDouble("vote_average", 0.0)

            items.add(
                MediaItem(
                    id = "$ID_PREFIX$tmdbId",
                    tmdbId = tmdbId,
                    title = title,
                    posterUrl = poster,
                    backdropUrl = backdropPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w1280$it" } ?: poster,
                    streamUrl = null,
                    embedUrl = embedUrl(BASE_URL, tmdbId, isTv, 1, 1),
                    synopsis = obj.optString("overview").ifBlank { "Watch $title in HD." },
                    category = if (isTv) "TV Show" else "Movie",
                    provider = PROVIDER_NAME,
                    rating = if (score > 0) "${(score * 10).toInt()}% Match" else "HD",
                    releaseYear = date.take(4),
                    durationOrEpisodes = if (isTv) "TV Series" else "Movie",
                    qualityTag = "HD"
                )
            )
        }
        return items
    }

    /** Anime is HiAnime's job: drop explicit anime and Japanese-language animation. */
    private fun isAnimeEntry(obj: JSONObject, mediaType: String): Boolean {
        if (mediaType == "anime" || looksLikeAnime(obj.optString("genre")) || looksLikeAnime(obj.optString("type"))) return true
        val genreIds = obj.optJSONArray("genre_ids")
        val animation = genreIds != null && (0 until genreIds.length()).any { genreIds.optInt(it) == TMDB_GENRE_ANIMATION }
        return animation && obj.optString("original_language") == "ja"
    }

    private fun absolute(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$BASE_URL$url"
        else -> url
    }

    // -------------------------------------------------------------- streams

    /**
     * Resolves a playable source for a movie or a TV episode. Mirrors are tried with hedged
     * parallel fallbacks. When no direct stream can be extracted, the real embed page URL is
     * returned (it plays in the web player); throws [SourceUnavailableException] if there is
     * nothing to resolve at all.
     */
    suspend fun resolveStream(item: MediaItem, season: Int = 1, episode: Int = 1): StreamSource =
        withContext(Dispatchers.IO) {
            val tmdbId = item.tmdbId
                ?: throw SourceUnavailableException("\"${item.title}\" has no Flexeo id")
            val isTv = item.category == "TV Show"

            val direct = resolveFirst(MIRRORS, HEDGE_DELAY_MS) { origin ->
                extract(embedUrl(origin, tmdbId, isTv, season, episode), origin)
            }
            direct ?: StreamSource(
                streamUrl = embedUrl(BASE_URL, tmdbId, isTv, season, episode),
                referer = "$BASE_URL/"
            )
        }

    /** Pulls an HLS/MP4 link out of the embed page, or out of its inner iframe. */
    private suspend fun extract(embed: String, origin: String): StreamSource? {
        val html = http.getText(embed, mapOf("Referer" to "$origin/"), retries = 0)
        findStream(html)?.let { return StreamSource(it, referer = "$origin/") }

        val iframeSrc = Jsoup.parse(html, embed).selectFirst("iframe[src]")?.attr("src").orEmpty()
        val frame = resolveWebUrl(embed, iframeSrc) ?: return null
        val innerHtml = http.getText(frame, mapOf("Referer" to embed), retries = 0)
        val inner = findStream(innerHtml) ?: return null
        val uri = URI(frame)
        return StreamSource(inner, referer = "${uri.scheme}://${uri.authority}/")
    }

    private fun findStream(html: String): String? =
        (M3U8_REGEX.find(html) ?: MP4_REGEX.find(html))?.value?.replace("\\/", "/")

    /** The iframe src comes from a third-party page: only ever follow plain web URLs. */
    private fun resolveWebUrl(base: String, src: String): String? {
        if (src.isBlank()) return null
        return try {
            val resolved = URI(base).resolve(if (src.startsWith("//")) "https:$src" else src)
            resolved.toString().takeIf { resolved.scheme == "https" || resolved.scheme == "http" }
        } catch (e: Exception) {
            null
        }
    }
}
