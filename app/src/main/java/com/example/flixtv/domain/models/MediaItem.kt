package com.example.flixtv.domain.models

data class MediaItem(
    val id: String,
    val tmdbId: Int? = null,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String? = null,
    val streamUrl: String? = null,
    val embedUrl: String? = null,
    val synopsis: String? = null,
    val category: String = "Movie", // "Movie", "TV Show", "Anime"
    val rating: String = "98% Match",
    val releaseYear: String = "2025",
    val durationOrEpisodes: String = "2h 15m",
    val qualityTag: String = "4K HDR",
    val genres: List<String> = emptyList(),
    val isFeatured: Boolean = false,
    val totalSeasons: Int = 1,
    val episodes: List<EpisodeItem> = emptyList()
)