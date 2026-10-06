package com.example.flixtv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.flixtv.domain.models.MediaItem

@Entity(tableName = "media_items")
data class MediaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val posterUrl: String,
    val streamUrl: String?,
    val synopsis: String?,
    val lastUpdated: Long
)

// Extension functions for mapping between Domain and Data layer
fun MediaEntity.toDomain() = MediaItem(
    id = id,
    title = title,
    posterUrl = posterUrl,
    streamUrl = streamUrl,
    synopsis = synopsis
)

fun MediaItem.toEntity() = MediaEntity(
    id = id,
    title = title,
    posterUrl = posterUrl,
    streamUrl = streamUrl,
    synopsis = synopsis,
    lastUpdated = System.currentTimeMillis()
)