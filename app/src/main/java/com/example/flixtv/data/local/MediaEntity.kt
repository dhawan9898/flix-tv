package com.example.flixtv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.flixtv.domain.models.MediaItem

@Entity(tableName = "media_items")
data class MediaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String? = null,
    val streamUrl: String?,
    val synopsis: String?,
    val category: String = "Movie",
    val rating: String = "98% Match",
    val releaseYear: String = "2025",
    val durationOrEpisodes: String = "2h 15m",
    val qualityTag: String = "4K HDR",
    val genresJson: String = "",
    val isFeatured: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

// Extension functions for mapping between Domain and Data layer
fun MediaEntity.toDomain(): MediaItem {
    val genreList = if (genresJson.isNotBlank()) {
        genresJson.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    } else {
        emptyList()
    }
    return MediaItem(
        id = id,
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        streamUrl = streamUrl,
        synopsis = synopsis,
        category = category,
        rating = rating,
        releaseYear = releaseYear,
        durationOrEpisodes = durationOrEpisodes,
        qualityTag = qualityTag,
        genres = genreList,
        isFeatured = isFeatured
    )
}

fun MediaItem.toEntity(): MediaEntity = MediaEntity(
    id = id,
    title = title,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    streamUrl = streamUrl,
    synopsis = synopsis,
    category = category,
    rating = rating,
    releaseYear = releaseYear,
    durationOrEpisodes = durationOrEpisodes,
    qualityTag = qualityTag,
    genresJson = genres.joinToString(","),
    isFeatured = isFeatured,
    lastUpdated = System.currentTimeMillis()
)