package com.example.flixtv.data.remote.providers

import android.util.Base64
import android.util.Log
import com.example.flixtv.data.remote.HttpFetcher
import com.example.flixtv.domain.models.EpisodeItem
import com.example.flixtv.domain.models.MediaItem
import com.example.flixtv.domain.models.SkipRange
import com.example.flixtv.domain.models.StreamSource
import com.example.flixtv.domain.models.SubtitleTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when no server can serve an episode right now. */
class SourceUnavailableException(message: String) : Exception(message)

/**
 * HiAnime provider: scrapes the catalog/search pages, lists episodes through the site's
 * theme API, then resolves a playable HLS stream by walking the episode's servers
 * (embed page -> sources endpoint -> optional AES token) with hedged, parallel fallbacks.
 */
@Singleton
class HiAnimeProvider @Inject constructor(
    private val http: HttpFetcher
) {

    companion object {
        private const val TAG = "HiAnimeProvider"
        const val PROVIDER_NAME = "HiAnime"
        const val BASE_URL = "https://hianime.at"
        const val ID_PREFIX = "hianime_"

        private const val PAGE_TTL_MS = 5 * 60 * 1000L
        private const val EPISODE_LIST_TTL_MS = 10 * 60 * 1000L
        private const val HEDGE_DELAY_MS = 2_500L
        private const val MAX_SERVERS = 4
        private const val CATALOG_LIMIT = 30

        // Site slugs end in the numeric title id, e.g. "solo-leveling-season-2-19413".
        private val SLUG_REGEX = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*-(\\d+)$")
        private val KNOWN_TYPES = listOf("TV", "Movie", "OVA", "ONA", "Special", "Music")

        // Token cipher of the embed player's sources endpoint (AES-CBC; key zero-padded to 256 bit).
        private const val TOKEN_KEY = "i?LMTAx0Q6,:}50U"
        private const val TOKEN_IV = "W0;27ToaUpl_P%'c"

        /** Site slug of a catalog item, or null if the item didn't come from the site's pages. */
        fun slugOf(item: MediaItem): String? {
            if (item.provider != PROVIDER_NAME || !item.id.startsWith(ID_PREFIX)) return null
            return item.id.removePrefix(ID_PREFIX).takeIf { SLUG_REGEX.matches(it) }
        }

        /** Items the app can resolve real streams for (anything categorised as Anime). */
        fun isAnime(item: MediaItem): Boolean = item.provider == PROVIDER_NAME || item.category == "Anime"

        internal fun normalizeTitle(title: String): String =
            title.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private fun siteHeaders(extra: Map<String, String> = emptyMap()) =
        mapOf("Referer" to "$BASE_URL/") + extra

    // ------------------------------------------------------------- catalog

    /** Trending titles from the site's home feed. Empty when the site can't be reached. */
    suspend fun getAnimeCatalog(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val html = http.getText(
                "$BASE_URL/home",
                siteHeaders(mapOf("Accept" to "text/html,application/xhtml+xml")),
                ttlMs = PAGE_TTL_MS
            )
            parseCards(html).take(CATALOG_LIMIT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "HiAnime catalog failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val html = http.getText(
                "$BASE_URL/search?keyword=${URLEncoder.encode(query, "UTF-8")}",
                siteHeaders(mapOf("Accept" to "text/html,application/xhtml+xml")),
                ttlMs = PAGE_TTL_MS
            )
            parseCards(html)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "HiAnime search failed: ${e.message}")
            emptyList()
        }
    }

    /** Parses the site's `.film-detail` cards (home feed, search, genre pages share the markup). */
    internal fun parseCards(html: String): List<MediaItem> {
        val doc: Document = Jsoup.parse(html, BASE_URL)
        doc.getElementById("main-sidebar")?.remove()

        val seen = HashSet<String>()
        val items = ArrayList<MediaItem>()
        for (detail in doc.select(".film-detail")) {
            val titleEl = detail.selectFirst(".film-name a") ?: continue
            val slug = titleEl.attr("href").trimEnd('/').substringAfterLast('/').substringBefore('?')
            val title = titleEl.attr("title").ifBlank { titleEl.text().trim() }
            val card = detail.closest(".flw-item")
            val posterEl = card?.selectFirst(".film-poster img")
            val poster = posterEl?.attr("data-src").orEmpty().ifBlank { posterEl?.attr("src").orEmpty() }
                .let { if (it.startsWith("//")) "https:$it" else it }

            // Drop ad slots / "load more" tiles / slider clones that reuse the card markup.
            if (!SLUG_REGEX.matches(slug) || title.length < 2 || !poster.startsWith("http") || !seen.add(slug)) continue

            val type = parseType(detail)
            val episodeCount = detail.selectFirst(".tick-eps")?.text()?.filter { it.isDigit() }?.toIntOrNull()
                ?: detail.selectFirst(".tick-sub")?.text()?.filter { it.isDigit() }?.toIntOrNull()
            items.add(
                MediaItem(
                    id = "$ID_PREFIX$slug",
                    title = title,
                    posterUrl = poster,
                    backdropUrl = poster,
                    // No stream here on purpose: real streams are resolved per episode on play.
                    streamUrl = null,
                    embedUrl = "$BASE_URL/$slug",
                    synopsis = "Watch $title subbed or dubbed.",
                    category = "Anime",
                    provider = PROVIDER_NAME,
                    rating = "HD",
                    releaseYear = "",
                    durationOrEpisodes = when {
                        type == "Movie" -> "Anime Movie"
                        episodeCount != null -> "$episodeCount Episodes"
                        else -> type
                    },
                    qualityTag = "HD",
                    genres = listOf("Anime", type)
                )
            )
        }
        return items
    }

    private fun parseType(detail: Element): String {
        for (item in detail.select(".fdi-item")) {
            val text = item.text().trim()
            KNOWN_TYPES.firstOrNull { it.equals(text, ignoreCase = true) }?.let { return it }
        }
        return "TV"
    }

    // ------------------------------------------------------------ episodes

    /**
     * Episode list for [item], oldest first. Whole-number episodes only (the app's episode
     * model is numeric); empty when the title can't be matched on the site.
     */
    suspend fun getEpisodes(item: MediaItem): List<EpisodeItem> = withContext(Dispatchers.IO) {
        val slug = slugFor(item) ?: return@withContext emptyList()
        try {
            loadEpisodes(slug, item.backdropUrl ?: item.posterUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Episode list failed for $slug: ${e.message}")
            emptyList()
        }
    }

    private suspend fun loadEpisodes(slug: String, still: String?): List<EpisodeItem> {
        var numericId = SLUG_REGEX.find(slug)!!.groupValues[1]
        val body = try {
            http.getText("$BASE_URL/api/theme/episode/list/$numericId", siteHeaders(), ttlMs = EPISODE_LIST_TTL_MS)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // The site may have re-numbered the title: find it again by name, but only accept
            // the very same slug - never another show that happens to be the first result.
            val base = slug.replace(Regex("-\\d+$"), "")
            val renumbered = search(base.replace('-', ' ')).asSequence()
                .mapNotNull { slugOf(it) }
                .firstOrNull { it.replace(Regex("-\\d+$"), "") == base }
                ?.let { SLUG_REGEX.find(it)!!.groupValues[1] }
            if (renumbered == null || renumbered == numericId) throw e
            numericId = renumbered
            http.getText("$BASE_URL/api/theme/episode/list/$numericId", siteHeaders(), ttlMs = EPISODE_LIST_TTL_MS)
        }
        val html = JSONObject(body).optString("html")
        if (html.isBlank()) return emptyList()

        return Jsoup.parse(html).select(".ep-item").mapNotNull { el ->
            val id = el.attr("data-id")
            val number = el.attr("data-number").toDoubleOrNull()
            if (id.isBlank() || number == null || number % 1.0 != 0.0) return@mapNotNull null
            EpisodeItem(
                episodeNumber = number.toInt(),
                title = el.attr("title").ifBlank { "Episode ${number.toInt()}" },
                overview = null,
                stillUrl = still,
                duration = "24m",
                providerEpisodeId = id
            )
        }.distinctBy { it.episodeNumber }.sortedBy { it.episodeNumber }
    }

    // -------------------------------------------------------------- streams

    /**
     * Resolves a playable stream for [episodeNumber] of [item] in [mode] ("sub" or "dub").
     * Throws [SourceUnavailableException] when no server can play it - it never substitutes
     * other content.
     */
    suspend fun resolveStream(item: MediaItem, episodeNumber: Int, mode: String): StreamSource =
        withContext(Dispatchers.IO) {
            val slug = slugFor(item)
                ?: throw SourceUnavailableException("\"${item.title}\" was not found on HiAnime")
            val episode = loadEpisodes(slug, null).firstOrNull { it.episodeNumber == episodeNumber }
                ?: throw SourceUnavailableException("Episode $episodeNumber not found")
            resolveEpisode(episode.providerEpisodeId!!, mode)
        }

    private suspend fun resolveEpisode(episodeId: String, mode: String): StreamSource {
        val serversBody = http.getText(
            "$BASE_URL/api/theme/episode/servers?episodeId=$episodeId",
            siteHeaders(),
            ttlMs = PAGE_TTL_MS
        )
        val serversHtml = JSONObject(serversBody).optString("html")
        val hashes = pickServerHashes(serversHtml, mode)
        val source = resolveFirst(hashes, HEDGE_DELAY_MS) { hash -> resolveEmbed(hash, mode) }
        return source ?: throw SourceUnavailableException("No $mode server could play this episode")
    }

    /**
     * Server hashes to try, best first: every server tagged with the requested audio type, or -
     * only when the episode has none of that type at all - whatever servers exist.
     */
    internal fun pickServerHashes(serversHtml: String, mode: String): List<String> {
        val doc = Jsoup.parse(serversHtml)
        fun hashesOf(selector: String) = doc.select(selector).map { it.attr("data-hash") }.filter { it.isNotBlank() }
        val preferred = hashesOf("[data-type=\"$mode\"][data-hash]")
        val list = preferred.ifEmpty { hashesOf("[data-hash]") }
        return list.distinct().take(MAX_SERVERS)
    }

    /** The hash is a base64 embed URL from a third-party page: only ever follow plain web URLs. */
    private fun decodeEmbedUrl(hash: String): String? = try {
        val url = String(Base64.decode(hash, Base64.DEFAULT), Charsets.UTF_8)
        val scheme = URI(url).scheme?.lowercase(Locale.US)
        url.takeIf { scheme == "https" || scheme == "http" }
    } catch (e: Exception) {
        null
    }

    private suspend fun resolveEmbed(hash: String, mode: String): StreamSource? {
        val embedUrl = decodeEmbedUrl(hash) ?: return null
        val embedHtml = http.getText(embedUrl, siteHeaders(), retries = 0)
        val dataId = Regex("data-id=\"(\\d+)\"", RegexOption.IGNORE_CASE).find(embedHtml)?.groupValues?.get(1)
            ?: return null

        val uri = URI(embedUrl)
        val origin = "${uri.scheme}://${uri.authority}"
        val sources = JSONObject(
            http.getText(
                "$origin/stream/getSourcesNew?id=$dataId",
                mapOf("Referer" to embedUrl, "X-Requested-With" to "XMLHttpRequest"),
                retries = 0
            )
        )

        var streamUrl = sources.optJSONArray("sources")?.optJSONObject(0)?.optString("file").orEmpty()
        var intro = parseSkipRange(sources.opt("intro"))
        var outro = parseSkipRange(sources.opt("outro"))
        var tracks = sources.optJSONArray("tracks")
        if (streamUrl.isBlank()) {
            val enc = sources.optString("enc")
            val decoded = if (enc.isNotBlank()) decryptToken(enc) else null
            if (decoded != null) {
                try {
                    val dec = JSONObject(decoded)
                    streamUrl = dec.optString("file").ifBlank { dec.optString("src") }
                    intro = intro ?: parseSkipRange(dec.opt("intro"))
                    outro = outro ?: parseSkipRange(dec.opt("outro"))
                    tracks = tracks ?: dec.optJSONArray("tracks")
                } catch (e: Exception) {
                    Log.w(TAG, "Decrypted token isn't JSON: ${e.message}")
                }
            }
        }
        if (streamUrl.isBlank()) return null

        return StreamSource(
            streamUrl = streamUrl,
            referer = "$origin/",
            subtitles = parseSubtitleTracks(tracks),
            intro = intro,
            outro = outro,
            mode = mode
        )
    }

    /** AES-CBC decrypt of the sources endpoint's `enc` token (URL-safe base64, no padding). */
    internal fun decryptToken(enc: String): String? = try {
        var b64 = enc.replace('-', '+').replace('_', '/')
        b64 += "=".repeat((4 - b64.length % 4) % 4)
        val key = ByteArray(32).also { TOKEN_KEY.toByteArray(Charsets.ISO_8859_1).copyInto(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(TOKEN_IV.toByteArray(Charsets.ISO_8859_1))
        )
        String(cipher.doFinal(Base64.decode(b64, Base64.DEFAULT)), Charsets.UTF_8)
    } catch (e: Exception) {
        Log.w(TAG, "decryptToken failed: ${e.message}")
        null
    }

    /**
     * Chapter markers arrive as {start,end} (seconds); some mirrors send [start,end] or numeric
     * strings. Untagged episodes come back as 0/0 - that means "no data".
     */
    internal fun parseSkipRange(raw: Any?): SkipRange? {
        val (start, end) = when (raw) {
            is JSONObject -> raw.opt("start").asSeconds() to raw.opt("end").asSeconds()
            is JSONArray -> raw.opt(0).asSeconds() to raw.opt(1).asSeconds()
            else -> return null
        }
        if (start < 0 || end <= start) return null
        return SkipRange((start * 1000).toLong(), (end * 1000).toLong())
    }

    private fun Any?.asSeconds(): Double = when (this) {
        is Number -> toDouble()
        is String -> toDoubleOrNull() ?: -1.0
        else -> -1.0
    }

    /** Caption tracks only: players also list a "thumbnails" sprite VTT in the same array. */
    private fun parseSubtitleTracks(tracks: JSONArray?): List<SubtitleTrack> {
        if (tracks == null) return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<SubtitleTrack>()
        for (i in 0 until tracks.length()) {
            val t = tracks.optJSONObject(i) ?: continue
            val src = t.optString("file").ifBlank { t.optString("src") }
            val kind = t.optString("kind", "captions").lowercase(Locale.US)
            if (src.isBlank() || (kind != "captions" && kind != "subtitles") || !seen.add(src)) continue
            out.add(
                SubtitleTrack(
                    src = src,
                    label = t.optString("label").ifBlank { t.optString("lang").ifBlank { "English" } },
                    isDefault = t.optBoolean("default", false)
                )
            )
        }
        return out
    }

    // ------------------------------------------------------------- matching

    /**
     * Site slug for [item]: its own slug when it came from the site, otherwise (AniList/curated
     * entries) the search result whose title matches - never just the first hit.
     */
    private suspend fun slugFor(item: MediaItem): String? {
        slugOf(item)?.let { return it }
        val wanted = normalizeTitle(item.title)
        if (wanted.isBlank()) return null
        val candidates = search(item.title).mapNotNull { c -> slugOf(c)?.let { it to normalizeTitle(c.title) } }
        return (candidates.firstOrNull { it.second == wanted }
            ?: candidates.firstOrNull { it.second.startsWith(wanted) || wanted.startsWith(it.second) })?.first
    }
}
