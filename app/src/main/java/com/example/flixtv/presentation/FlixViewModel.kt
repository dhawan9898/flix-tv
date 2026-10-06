package com.example.flixtv.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flixtv.data.remote.AniSkipClient
import com.example.flixtv.data.repository.MediaRepository
import com.example.flixtv.domain.models.EpisodeItem
import com.example.flixtv.domain.models.StreamSource
import com.example.flixtv.domain.models.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlixViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val mediaItems: StateFlow<List<MediaItem>> = repository.getAllMediaItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val featuredItems: StateFlow<List<MediaItem>> = repository.getFeaturedMediaItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredMediaItems: StateFlow<List<MediaItem>> = combine(
        mediaItems,
        _selectedCategory
    ) { items, category ->
        when (category) {
            "All" -> items
            "Movies" -> items.filter { it.category == "Movie" }
            "TV Shows" -> items.filter { it.category == "TV Show" }
            "Anime" -> items.filter { it.category == "Anime" }
            "Trending" -> items.filter { it.genres.any { g -> g.contains("Trending", ignoreCase = true) || g.contains("Top", ignoreCase = true) } }
            else -> items.filter { it.category.equals(category, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.refreshAllContent()
            _isLoading.value = false
        }
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    suspend fun resolveStreamUrl(item: MediaItem, season: Int = 1, episode: Int = 1): String {
        val resolved = try {
            repository.resolveStreamUrl(item, season, episode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
        return resolved.ifBlank { item.streamUrl ?: item.embedUrl ?: "" }
    }

    /** Real episode list for an anime title (empty when the site doesn't know it). */
    suspend fun loadAnimeEpisodes(item: MediaItem): List<EpisodeItem> = repository.getAnimeEpisodes(item)

    /** Stream + referer + subtitles + skip markers for one anime episode, or the failure. */
    suspend fun resolveAnimeSource(item: MediaItem, episode: Int, mode: String): Result<StreamSource> =
        try {
            Result.success(repository.resolveAnimeSource(item, episode, mode))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun skipIntervals(title: String, episode: Int, lengthSeconds: Long): AniSkipClient.Intervals =
        repository.getSkipIntervals(title, episode, lengthSeconds)

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            // Instant local cache match first
            val localMatches = mediaItems.value.filter {
                it.title.contains(query, ignoreCase = true) ||
                        (it.synopsis?.contains(query, ignoreCase = true) == true)
            }
            if (localMatches.isNotEmpty()) {
                _searchResults.value = localMatches
            }

            // Debounce before hitting remote TMDB + AniList API
            delay(350)
            val remoteResults = repository.searchRemote(query)
            if (remoteResults.isNotEmpty()) {
                val merged = (remoteResults + localMatches).distinctBy { it.id }
                _searchResults.value = merged
            }
            _isSearching.value = false
        }
    }
}