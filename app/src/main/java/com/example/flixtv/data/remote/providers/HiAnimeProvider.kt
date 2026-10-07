package com.example.flixtv.data.remote.providers

import com.example.flixtv.domain.models.MediaItem

/**
 * HiAnimeProvider has been removed. All media streaming is provided exclusively by [FlexeoProvider].
 */
object HiAnimeProvider {
    const val PROVIDER_NAME = "Flexeo"

    fun isAnime(item: MediaItem): Boolean = item.category == "Anime"
}
