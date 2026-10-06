package com.example.flixtv.data.repository

import android.util.Log
import com.example.flixtv.data.local.MediaDao
import com.example.flixtv.data.local.toDomain
import com.example.flixtv.data.local.toEntity
import com.example.flixtv.data.remote.AniSkipClient
import com.example.flixtv.data.remote.MediaScraperDataSource
import com.example.flixtv.domain.models.EpisodeItem
import com.example.flixtv.domain.models.StreamSource
import com.example.flixtv.domain.models.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val mediaDao: MediaDao,
    private val scraperDataSource: MediaScraperDataSource,
    private val aniSkipClient: AniSkipClient
) {
    companion object {
        private const val TAG = "MediaRepository"
    }

    /**
     * Returns a reactive flow of locally cached media items.
     */
    fun getAllMediaItems(): Flow<List<MediaItem>> {
        return mediaDao.getAllMedia().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Returns items filtered by category ("Movie", "TV Show", "Anime")
     */
    fun getMediaByCategory(category: String): Flow<List<MediaItem>> {
        return if (category == "All" || category.isBlank()) {
            getAllMediaItems()
        } else {
            mediaDao.getMediaByCategory(category).map { entities ->
                entities.map { it.toDomain() }
            }
        }
    }

    /**
     * Returns featured billboard items for the hero spotlight
     */
    fun getFeaturedMediaItems(): Flow<List<MediaItem>> {
        return mediaDao.getFeaturedMedia().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Local Room search flow
     */
    fun searchLocal(query: String): Flow<List<MediaItem>> {
        return mediaDao.searchLocalMedia(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Universal search that queries remote sources (TMDB + AniList) and caches results
     */
    suspend fun searchRemote(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val result = scraperDataSource.universalSearch(query)
            val items = result.getOrDefault(emptyList())
            if (items.isNotEmpty()) {
                mediaDao.insertAll(items.map { it.toEntity() })
            }
            items
        } catch (e: Exception) {
            Log.e(TAG, "Search remote failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Resolves playable video stream URL for a media item from Flexeo or HiAnime
     */
    suspend fun resolveStreamUrl(
        item: MediaItem,
        seasonNumber: Int = 1,
        episodeNumber: Int = 1
    ): String = withContext(Dispatchers.IO) {
        return@withContext scraperDataSource.resolveStreamForMedia(item, seasonNumber, episodeNumber)
    }

    /** Resolves stream + referer for a movie or TV episode from Flexeo. */
    suspend fun resolveMovieSource(item: MediaItem, season: Int, episode: Int): StreamSource =
        scraperDataSource.resolveMovieSource(item, season, episode)

    /** Real episode list for an anime title from the provider site. */
    suspend fun getAnimeEpisodes(item: MediaItem): List<EpisodeItem> = withContext(Dispatchers.IO) {
        scraperDataSource.fetchAnimeEpisodes(item)
    }

    /** Resolves stream + referer + subtitles + skip markers for an anime episode. */
    suspend fun resolveAnimeSource(item: MediaItem, episodeNumber: Int, mode: String): StreamSource =
        scraperDataSource.resolveAnimeSource(item, episodeNumber, mode)

    /** Real opening/ending intervals for an episode (fallback when the stream carries none). */
    suspend fun getSkipIntervals(title: String, episodeNumber: Int, lengthSeconds: Long): AniSkipClient.Intervals =
        aniSkipClient.getIntervals(title, episodeNumber, lengthSeconds)

    /**
     * Fetches movies, TV shows (Flexeo), anime (HiAnime), and charts concurrently, caching into Room
     */
    suspend fun refreshAllContent() = coroutineScope {
        try {
            // First ensure curated fallback items exist so UI is never blank
            val fallbacks = scraperDataSource.getCuratedFallbacks()
            mediaDao.insertAll(fallbacks.map { it.toEntity() })

            val providersResult = scraperDataSource.fetchAllProviders()
            providersResult.onSuccess { allItems ->
                if (allItems.isNotEmpty()) {
                    mediaDao.insertAll(allItems.map { it.toEntity() })
                    Log.d(TAG, "Successfully refreshed and cached ${allItems.size} media items from Flexeo & HiAnime")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed refreshing content: ${e.message}", e)
        }
    }

    /**
     * Scrapes a specific URL for backwards compatibility
     */
    suspend fun scrapeAndCache(url: String) {
        val result = scraperDataSource.scrapeMedia(url)
        result.onSuccess { items ->
            items.forEach { item ->
                mediaDao.insertMedia(item.toEntity())
            }
        }
    }
}