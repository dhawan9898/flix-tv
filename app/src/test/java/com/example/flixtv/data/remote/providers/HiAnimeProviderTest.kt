package com.example.flixtv.data.remote.providers

import com.example.flixtv.domain.models.MediaItem
import org.junit.Assert.assertTrue
import org.junit.Test

class HiAnimeProviderTest {

    @Test
    fun isAnimeCheck() {
        val anime = MediaItem(id = "anime_1", title = "Solo Leveling", posterUrl = "", category = "Anime", provider = "Flexeo")
        assertTrue(HiAnimeProvider.isAnime(anime))
    }
}
