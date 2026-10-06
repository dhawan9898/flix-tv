package com.example.flixtv.data.remote

import com.example.flixtv.domain.models.MediaItem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaScraperDataSource @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun scrapeMedia(url: String): Result<MediaItem> = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch raw HTML
            val response = httpClient.get(url)
            val html = response.bodyAsText()

            // 2. Parse with Jsoup
            val document = Jsoup.parse(html)
            
            // Extract attributes (Generic example)
            val title = document.select("h1.media-title").text().takeIf { it.isNotBlank() } ?: "Unknown Title"
            val posterUrl = document.select("img.media-poster").attr("src")
            val streamUrl = document.select("video source").attr("src").takeIf { it.isNotBlank() }

            val mediaItem = MediaItem(
                id = url.hashCode().toString(),
                title = title,
                posterUrl = posterUrl,
                streamUrl = streamUrl,
                synopsis = document.select("div.synopsis").text()
            )
            Result.success(mediaItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}