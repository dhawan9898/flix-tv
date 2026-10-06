package com.example.flixtv.data.remote

import android.util.Log
import com.example.flixtv.domain.models.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaScraperDataSource @Inject constructor() {

    companion object {
        private const val TAG = "MediaScraper"
        private const val TMDB_API_KEY = "4e44d9029b1270a757cddc766a1bcb63"
        private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"
        private const val ANILIST_URL = "https://graphql.anilist.co"

        private const val SAMPLE_STREAM_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        private const val SAMPLE_STREAM_TEARS =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
        private const val SAMPLE_STREAM_SINTEL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
    }

    /**
     * Performs a network GET request and returns the response body string
     */
    private fun httpGet(urlString: String, timeoutMs: Int = 8000): String {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            setRequestProperty("Accept", "application/json")
        }

        val code = connection.responseCode
        if (code !in 200..299) {
            throw Exception("HTTP request failed with code $code: $urlString")
        }

        val reader = BufferedReader(InputStreamReader(connection.inputStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }

    /**
     * Performs a network POST request with JSON body (used for AniList GraphQL)
     */
    private fun httpPostJson(urlString: String, jsonBody: String, timeoutMs: Int = 8000): String {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }

        connection.outputStream.use { os ->
            os.write(jsonBody.toByteArray(Charsets.UTF_8))
            os.flush()
        }

        val code = connection.responseCode
        if (code !in 200..299) {
            throw Exception("HTTP POST failed with code $code: $urlString")
        }

        val reader = BufferedReader(InputStreamReader(connection.inputStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }

    /**
     * Fetch trending movies and TV shows from TMDB
     */
    suspend fun fetchTrendingTmdb(): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            val url = "$TMDB_BASE_URL/trending/all/week?api_key=$TMDB_API_KEY"
            val response = httpGet(url)
            val json = JSONObject(response)
            val results = json.optJSONArray("results") ?: JSONArray()

            val items = mutableListOf<MediaItem>()
            for (i in 0 until results.length()) {
                val obj = results.getJSONObject(i)
                val mediaType = obj.optString("media_type", "movie")
                if (mediaType != "movie" && mediaType != "tv") continue

                val id = "tmdb_${obj.optInt("id")}"
                val title = obj.optString("title").ifBlank { obj.optString("name", "Untitled") }
                val posterPath = obj.optString("poster_path")
                val backdropPath = obj.optString("backdrop_path")
                val overview = obj.optString("overview", "No synopsis available.")
                val voteAverage = obj.optDouble("vote_average", 8.0)
                val releaseDate = obj.optString("release_date").ifBlank { obj.optString("first_air_date", "2025") }
                val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else "2025"

                val posterUrl = if (posterPath.isNotBlank() && posterPath != "null") {
                    "$TMDB_IMAGE_BASE/w500$posterPath"
                } else ""

                val backdropUrl = if (backdropPath.isNotBlank() && backdropPath != "null") {
                    "$TMDB_IMAGE_BASE/original$backdropPath"
                } else posterUrl

                if (posterUrl.isBlank()) continue

                val isTv = mediaType == "tv"
                val matchPercent = (voteAverage * 10).toInt().coerceIn(75, 99)

                items.add(
                    MediaItem(
                        id = id,
                        title = title,
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl,
                        streamUrl = if (i % 2 == 0) SAMPLE_STREAM_URL else SAMPLE_STREAM_TEARS,
                        synopsis = overview,
                        category = if (isTv) "TV Show" else "Movie",
                        rating = "$matchPercent% Match",
                        releaseYear = year,
                        durationOrEpisodes = if (isTv) "Series" else "2h 10m",
                        qualityTag = if (i % 3 == 0) "Vision • Atmos" else "4K HDR",
                        genres = listOf(if (isTv) "TV Series" else "Cinema", "Trending"),
                        isFeatured = i < 3
                    )
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB trending: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Search TMDB for any movie or TV series
     */
    suspend fun searchTmdb(query: String): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) return@withContext Result.success(emptyList())

            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$TMDB_BASE_URL/search/multi?api_key=$TMDB_API_KEY&query=$encoded&include_adult=false"
            val response = httpGet(url)
            val json = JSONObject(response)
            val results = json.optJSONArray("results") ?: JSONArray()

            val items = mutableListOf<MediaItem>()
            for (i in 0 until results.length()) {
                val obj = results.getJSONObject(i)
                val mediaType = obj.optString("media_type", "movie")
                if (mediaType != "movie" && mediaType != "tv") continue

                val id = "tmdb_${obj.optInt("id")}"
                val title = obj.optString("title").ifBlank { obj.optString("name", "Untitled") }
                val posterPath = obj.optString("poster_path")
                val backdropPath = obj.optString("backdrop_path")
                val overview = obj.optString("overview", "No synopsis available.")
                val voteAverage = obj.optDouble("vote_average", 7.8)
                val releaseDate = obj.optString("release_date").ifBlank { obj.optString("first_air_date", "2025") }
                val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else "2025"

                val posterUrl = if (posterPath.isNotBlank() && posterPath != "null") {
                    "$TMDB_IMAGE_BASE/w500$posterPath"
                } else ""

                val backdropUrl = if (backdropPath.isNotBlank() && backdropPath != "null") {
                    "$TMDB_IMAGE_BASE/original$backdropPath"
                } else posterUrl

                if (posterUrl.isBlank()) continue

                val isTv = mediaType == "tv"
                val matchPercent = (voteAverage * 10).toInt().coerceIn(70, 99)

                items.add(
                    MediaItem(
                        id = id,
                        title = title,
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl,
                        streamUrl = SAMPLE_STREAM_URL,
                        synopsis = overview,
                        category = if (isTv) "TV Show" else "Movie",
                        rating = "$matchPercent% Match",
                        releaseYear = year,
                        durationOrEpisodes = if (isTv) "TV Series" else "Feature Film",
                        qualityTag = "4K HDR",
                        genres = listOf(if (isTv) "TV Series" else "Movie"),
                        isFeatured = false
                    )
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching TMDB: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch trending anime from AniList GraphQL
     */
    suspend fun fetchTrendingAnime(): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            val graphQLQuery = """
                {
                  "query": "{ Page(page: 1, perPage: 15) { media(type: ANIME, sort: TRENDING_DESC) { id title { romaji english } coverImage { extraLarge large } bannerImage description averageScore seasonYear episodes format genres } } }"
                }
            """.trimIndent()

            val response = httpPostJson(ANILIST_URL, graphQLQuery)
            val json = JSONObject(response)
            val mediaArray = json.optJSONObject("data")
                ?.optJSONObject("Page")
                ?.optJSONArray("media") ?: JSONArray()

            val items = mutableListOf<MediaItem>()
            for (i in 0 until mediaArray.length()) {
                val media = mediaArray.getJSONObject(i)
                val id = "anime_${media.optInt("id")}"
                val titleObj = media.optJSONObject("title")
                val english = titleObj?.optString("english", "") ?: ""
                val romaji = titleObj?.optString("romaji", "") ?: ""
                val title = if (english.isNotBlank()) english else romaji

                val coverObj = media.optJSONObject("coverImage")
                val posterUrl = coverObj?.optString("extraLarge", "").takeIf { !it.isNullOrBlank() }
                    ?: coverObj?.optString("large", "") ?: ""

                val bannerImage = media.optString("bannerImage", "")
                val backdropUrl = if (bannerImage.isNotBlank()) bannerImage else posterUrl

                if (posterUrl.isBlank()) continue

                val rawDesc = media.optString("description", "An acclaimed Japanese anime series.")
                val synopsis = rawDesc.replace(Regex("<.*?>"), "").trim()

                val score = media.optInt("averageScore", 88)
                val episodes = media.optInt("episodes", 24)
                val year = media.optInt("seasonYear", 2024).toString()
                val format = media.optString("format", "TV")
                val match = "$score% Match"

                val genresArray = media.optJSONArray("genres")
                val genres = mutableListOf<String>()
                genres.add("Anime")
                if (genresArray != null) {
                    for (g in 0 until minOf(2, genresArray.length())) {
                        genres.add(genresArray.getString(g))
                    }
                }

                items.add(
                    MediaItem(
                        id = id,
                        title = title,
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl,
                        streamUrl = SAMPLE_STREAM_SINTEL,
                        synopsis = synopsis,
                        category = "Anime",
                        rating = match,
                        releaseYear = year,
                        durationOrEpisodes = if (format == "MOVIE") "Anime Movie" else "$episodes Episodes",
                        qualityTag = "Atmos • Remaster",
                        genres = genres,
                        isFeatured = i < 2
                    )
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching AniList anime: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Search AniList GraphQL for any anime title
     */
    suspend fun searchAnime(query: String): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) return@withContext Result.success(emptyList())

            val escapedQuery = JSONObject.quote(query)
            val graphQLQuery = """
                {
                  "query": "{ Page(page: 1, perPage: 12) { media(type: ANIME, search: $escapedQuery, sort: SEARCH_MATCH) { id title { romaji english } coverImage { extraLarge large } bannerImage description averageScore seasonYear episodes format genres } } }"
                }
            """.trimIndent()

            val response = httpPostJson(ANILIST_URL, graphQLQuery)
            val json = JSONObject(response)
            val mediaArray = json.optJSONObject("data")
                ?.optJSONObject("Page")
                ?.optJSONArray("media") ?: JSONArray()

            val items = mutableListOf<MediaItem>()
            for (i in 0 until mediaArray.length()) {
                val media = mediaArray.getJSONObject(i)
                val id = "anime_${media.optInt("id")}"
                val titleObj = media.optJSONObject("title")
                val english = titleObj?.optString("english", "") ?: ""
                val romaji = titleObj?.optString("romaji", "") ?: ""
                val title = if (english.isNotBlank()) english else romaji

                val coverObj = media.optJSONObject("coverImage")
                val posterUrl = coverObj?.optString("extraLarge", "").takeIf { !it.isNullOrBlank() }
                    ?: coverObj?.optString("large", "") ?: ""

                val bannerImage = media.optString("bannerImage", "")
                val backdropUrl = if (bannerImage.isNotBlank()) bannerImage else posterUrl

                if (posterUrl.isBlank()) continue

                val rawDesc = media.optString("description", "An acclaimed Japanese anime series.")
                val synopsis = rawDesc.replace(Regex("<.*?>"), "").trim()

                val score = media.optInt("averageScore", 85)
                val episodes = media.optInt("episodes", 12)
                val year = media.optInt("seasonYear", 2024).toString()
                val format = media.optString("format", "TV")

                val genresArray = media.optJSONArray("genres")
                val genres = mutableListOf<String>()
                genres.add("Anime")
                if (genresArray != null) {
                    for (g in 0 until minOf(2, genresArray.length())) {
                        genres.add(genresArray.getString(g))
                    }
                }

                items.add(
                    MediaItem(
                        id = id,
                        title = title,
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl,
                        streamUrl = SAMPLE_STREAM_SINTEL,
                        synopsis = synopsis,
                        category = "Anime",
                        rating = "$score% Match",
                        releaseYear = year,
                        durationOrEpisodes = if (format == "MOVIE") "Anime Movie" else "$episodes Episodes",
                        qualityTag = "1080p HD",
                        genres = genres,
                        isFeatured = false
                    )
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching AniList anime: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Scrape FlixPatrol charts using Jsoup (existing support preserved)
     */
    suspend fun scrapeMedia(url: String): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching URL: $url")
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "text/html")
                .timeout(8000)
                .get()

            val mediaItems = mutableListOf<MediaItem>()
            val rows = document.select("tr.table-group")
            Log.d(TAG, "Found ${rows.size} rows on FlixPatrol")

            for ((index, row) in rows.withIndex()) {
                val titleElement = row.selectFirst("a.hover:underline")
                val title = titleElement?.text() ?: continue

                val imgElement = row.selectFirst("img")
                val rawPoster = imgElement?.attr("src")?.replace("w=64", "w=600") ?: ""
                val posterUrl = if (rawPoster.startsWith("/")) "https://flixpatrol.com$rawPoster" else rawPoster

                val id = "fp_${title.hashCode()}"

                val mediaItem = MediaItem(
                    id = id,
                    title = title,
                    posterUrl = posterUrl,
                    backdropUrl = posterUrl,
                    streamUrl = SAMPLE_STREAM_URL,
                    synopsis = "Top trending worldwide sensation '$title' streaming on FlixTV.",
                    category = if (index % 3 == 0) "TV Show" else "Movie",
                    rating = "${99 - index}% Match",
                    releaseYear = "2025",
                    durationOrEpisodes = if (index % 3 == 0) "Series" else "2h 05m",
                    qualityTag = "4K HDR",
                    genres = listOf("Top 10", "Trending"),
                    isFeatured = index == 0
                )
                mediaItems.add(mediaItem)
            }

            if (mediaItems.isEmpty()) {
                Log.d(TAG, "No items found on FlixPatrol, using fallback")
                return@withContext Result.success(getCuratedFallbacks())
            }

            Result.success(mediaItems)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scrape FlixPatrol: ${e.message}", e)
            Result.success(getCuratedFallbacks())
        }
    }

    /**
     * Universal search across Movies, TV Series, and Anime
     */
    suspend fun universalSearch(query: String): Result<List<MediaItem>> = coroutineScope {
        if (query.isBlank()) return@coroutineScope Result.success(emptyList())

        val tmdbDeferred = async(Dispatchers.IO) { searchTmdb(query).getOrDefault(emptyList()) }
        val animeDeferred = async(Dispatchers.IO) { searchAnime(query).getOrDefault(emptyList()) }

        val tmdbResults = tmdbDeferred.await()
        val animeResults = animeDeferred.await()

        val combined = mutableListOf<MediaItem>()
        val maxLen = maxOf(tmdbResults.size, animeResults.size)
        for (i in 0 until maxLen) {
            if (i < tmdbResults.size) combined.add(tmdbResults[i])
            if (i < animeResults.size) combined.add(animeResults[i])
        }

        Result.success(combined)
    }

    /**
     * Curated CineWave items with rich artwork and visionOS liquid styling
     */
    fun getCuratedFallbacks(): List<MediaItem> = listOf(
        MediaItem(
            id = "cw_nebula",
            title = "NEBULA: Beyond Horizons",
            posterUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=800&auto=format&fit=crop",
            backdropUrl = "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?q=80&w=1600&auto=format&fit=crop",
            streamUrl = SAMPLE_STREAM_URL,
            synopsis = "When humanity's deepest subspace probe breaches the perimeter of the Kepler Rift, astronaut Lyra Vance encounters an intelligence that alters the fabric of time.",
            category = "Movie",
            rating = "99% Match",
            releaseYear = "2025",
            durationOrEpisodes = "1h 48m",
            qualityTag = "4K Vision • Atmos",
            genres = listOf("Sci-Fi", "Mystery", "CineWave Premiere"),
            isFeatured = true
        ),
        MediaItem(
            id = "cw_chrono",
            title = "Chrono Eclipse",
            posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=800&auto=format&fit=crop",
            backdropUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?q=80&w=1600&auto=format&fit=crop",
            streamUrl = SAMPLE_STREAM_TEARS,
            synopsis = "When an unpredicted temporal event fractures orbital space station Horizon-9, lead chronologist Dr. Elena Rostova discovers duplicate timelines colliding across the decaying habitat.",
            category = "Movie",
            rating = "98% Match",
            releaseYear = "2025",
            durationOrEpisodes = "2h 18m",
            qualityTag = "4K Ultra HD • HDR10+",
            genres = listOf("Sci-Fi", "Thriller", "CineWave Studios"),
            isFeatured = true
        ),
        MediaItem(
            id = "cw_aot",
            title = "Attack on Titan: The Final Season",
            posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?q=80&w=800&auto=format&fit=crop",
            backdropUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?q=80&w=1600&auto=format&fit=crop",
            streamUrl = SAMPLE_STREAM_SINTEL,
            synopsis = "After spending four years fighting on foreign soil, Eren Yeager and the Scout Regiment prepare for their ultimate confrontation that will decide the fate of all humanity.",
            category = "Anime",
            rating = "99% Match",
            releaseYear = "2024",
            durationOrEpisodes = "28 Episodes",
            qualityTag = "Atmos 7.1",
            genres = listOf("Anime", "Dark Fantasy", "Action"),
            isFeatured = true
        ),
        MediaItem(
            id = "cw_jujutsu",
            title = "Jujutsu Kaisen: Shibuya Incident",
            posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?q=80&w=800&auto=format&fit=crop",
            backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=1600&auto=format&fit=crop",
            streamUrl = SAMPLE_STREAM_SINTEL,
            synopsis = "On October 31st, a curtain suddenly falls around Shibuya Station, trapping countless civilians. Satoru Gojo enters the lion's den alone to confront special grade cursed spirits.",
            category = "Anime",
            rating = "97% Match",
            releaseYear = "2024",
            durationOrEpisodes = "23 Episodes",
            qualityTag = "Spatial Audio",
            genres = listOf("Anime", "Supernatural", "Action"),
            isFeatured = false
        ),
        MediaItem(
            id = "cw_chroma",
            title = "Chroma Shift",
            posterUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?q=80&w=800&auto=format&fit=crop",
            backdropUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?q=80&w=1600&auto=format&fit=crop",
            streamUrl = SAMPLE_STREAM_TEARS,
            synopsis = "A neo-noir detective standing under an umbrella on a rainy Tokyo rooftop discovers floating neon holographic conspiracies that overwrite human memory.",
            category = "TV Show",
            rating = "96% Match",
            releaseYear = "2025",
            durationOrEpisodes = "10 Episodes",
            qualityTag = "VisionOS HDR",
            genres = listOf("Cyberpunk", "Mystery"),
            isFeatured = false
        ),
        MediaItem(
            id = "cw_citadel",
            title = "The High Citadel",
            posterUrl = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?q=80&w=800&auto=format&fit=crop",
            backdropUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=80&w=1600&auto=format&fit=crop",
            streamUrl = SAMPLE_STREAM_URL,
            synopsis = "Ancient architectural ruins carved directly into sheer alpine cliff faces hold secrets to an empire that mastered gravity itself.",
            category = "Movie",
            rating = "94% Match",
            releaseYear = "2024",
            durationOrEpisodes = "2h 32m",
            qualityTag = "4K IMAX",
            genres = listOf("Epic", "Adventure"),
            isFeatured = false
        )
    )
}