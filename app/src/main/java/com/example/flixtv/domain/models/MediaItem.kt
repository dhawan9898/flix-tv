package com.example.flixtv.domain.models

data class MediaItem(
    val id: String,
    val title: String,
    val posterUrl: String,
    val streamUrl: String?,
    val synopsis: String? = null
)