package com.example.flixtv.domain.models

data class EpisodeItem(
    val episodeNumber: Int,
    val seasonNumber: Int = 1,
    val title: String,
    val overview: String? = null,
    val stillUrl: String? = null,
    val duration: String = "45m",
    val streamUrl: String? = null,
    val embedUrl: String? = null
)
