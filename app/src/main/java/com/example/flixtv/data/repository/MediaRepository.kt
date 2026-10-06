package com.example.flixtv.data.repository

import com.example.flixtv.data.local.MediaDao
import com.example.flixtv.data.local.toDomain
import com.example.flixtv.data.local.toEntity
import com.example.flixtv.data.remote.MediaScraperDataSource
import com.example.flixtv.domain.models.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val mediaDao: MediaDao,
    private val scraperDataSource: MediaScraperDataSource
) {
    /**
     * Returns a reactive flow of locally cached media items.
     * The UI should observe this flow for SSOT (Single Source of Truth).
     */
    fun getAllMediaItems(): Flow<List<MediaItem>> {
        return mediaDao.getAllMedia().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Triggers a scrape action from the remote data source.
     * On success, updates the Room Database gracefully.
     */
    suspend fun scrapeAndCache(url: String) {
        val result = scraperDataSource.scrapeMedia(url)
        result.onSuccess { item ->
            // The UPSERT strategy handles keeping history safe and updated
            mediaDao.insertMedia(item.toEntity())
        }
    }
}