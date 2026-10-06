package com.example.flixtv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.flixtv.domain.models.EpisodeItem
import com.example.flixtv.domain.models.MediaItem
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "media_items")
data class MediaEntity(
    @PrimaryKey val id: String,
    val tmdbId: Int? = null,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String? = null,
    val streamUrl: String?,
    val embedUrl: String? = null,
    val synopsis: String?,
    val category: String = "Movie",
    val rating: String = "98% Match",
    val releaseYear: String = "2025",
    val durationOrEpisodes: String = "2h 15m",
    val qualityTag: String = "4K HDR",
    val genresJson: String = "",
    val isFeatured: Boolean = false,
    val totalSeasons: Int = 1,
    val episodesJson: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)

// Extension functions for mapping between Domain and Data layer
fun MediaEntity.toDomain(): MediaItem {
    val genreList = if (genresJson.isNotBlank()) {
        genresJson.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    } else {
        emptyList()
    }

    val episodeList = mutableListOf<EpisodeItem>()
    if (episodesJson.isNotBlank()) {
        try {
            val jsonArray = JSONArray(episodesJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                episodeList.add(
                    EpisodeItem(
                        episodeNumber = obj.optInt("episodeNumber", i + 1),
                        seasonNumber = obj.optInt("seasonNumber", 1),
                        title = obj.optString("title", "Episode ${i + 1}"),
                        overview = if (obj.has("overview") && !obj.isNull("overview")) obj.getString("overview") else null,
                        stillUrl = if (obj.has("stillUrl") && !obj.isNull("stillUrl")) obj.getString("stillUrl") else null,
                        duration = obj.optString("duration", "45m"),
                        streamUrl = if (obj.has("streamUrl") && !obj.isNull("streamUrl")) obj.getString("streamUrl") else null,
                        embedUrl = if (obj.has("embedUrl") && !obj.isNull("embedUrl")) obj.getString("embedUrl") else null
                    )
                )
            }
        } catch (_: Exception) {}
    }

    // If TV show or Anime has no episodes saved, provide automatic Season 1 episode cards
    val finalEpisodes = if (episodeList.isEmpty() && (category == "TV Show" || category == "Anime")) {
        val count = when {
            durationOrEpisodes.contains("Episode", ignoreCase = true) -> {
                durationOrEpisodes.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 24) ?: 10
            }
            category == "Anime" -> 12
            else -> 8
        }
        (1..count).map { epNum ->
            EpisodeItem(
                episodeNumber = epNum,
                seasonNumber = 1,
                title = when (epNum) {
                    1 -> "Episode 1 • Premiere"
                    2 -> "Episode 2 • The Gathering"
                    3 -> "Episode 3 • Event Horizon"
                    4 -> "Episode 4 • Fractured Realities"
                    else -> "Episode $epNum"
                },
                overview = "Exciting chapter in the journey of $title. Follow the characters through unexpected twists and revelations.",
                stillUrl = backdropUrl ?: posterUrl,
                duration = if (category == "Anime") "24m" else "52m",
                streamUrl = streamUrl,
                embedUrl = if (tmdbId != null) "https://vidsrc.xyz/embed/tv?tmdb=$tmdbId&season=1&episode=$epNum" else embedUrl
            )
        }
    } else {
        episodeList
    }

    return MediaItem(
        id = id,
        tmdbId = tmdbId,
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        streamUrl = streamUrl,
        embedUrl = embedUrl,
        synopsis = synopsis,
        category = category,
        rating = rating,
        releaseYear = releaseYear,
        durationOrEpisodes = durationOrEpisodes,
        qualityTag = qualityTag,
        genres = genreList,
        isFeatured = isFeatured,
        totalSeasons = totalSeasons,
        episodes = finalEpisodes
    )
}

fun MediaItem.toEntity(): MediaEntity {
    val epJson = if (episodes.isNotEmpty()) {
        val arr = JSONArray()
        for (ep in episodes) {
            val obj = JSONObject()
            obj.put("episodeNumber", ep.episodeNumber)
            obj.put("seasonNumber", ep.seasonNumber)
            obj.put("title", ep.title)
            obj.put("overview", ep.overview)
            obj.put("stillUrl", ep.stillUrl)
            obj.put("duration", ep.duration)
            obj.put("streamUrl", ep.streamUrl)
            obj.put("embedUrl", ep.embedUrl)
            arr.put(obj)
        }
        arr.toString()
    } else ""

    return MediaEntity(
        id = id,
        tmdbId = tmdbId,
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        streamUrl = streamUrl,
        embedUrl = embedUrl,
        synopsis = synopsis,
        category = category,
        rating = rating,
        releaseYear = releaseYear,
        durationOrEpisodes = durationOrEpisodes,
        qualityTag = qualityTag,
        genresJson = genres.joinToString(","),
        isFeatured = isFeatured,
        totalSeasons = totalSeasons,
        episodesJson = epJson,
        lastUpdated = System.currentTimeMillis()
    )
}