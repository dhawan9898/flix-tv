package com.example.flixtv.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flixtv.data.repository.MediaRepository
import com.example.flixtv.domain.models.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlixViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    // Expose cached database state to UI
    val mediaItems: StateFlow<List<MediaItem>> = repository.getAllMediaItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // For demonstration, prepopulate with some dummy scrape triggers.
        // In a real app, this would be triggered by a background worker or user intent.
        scrapeInitialData()
    }

    private fun scrapeInitialData() {
        viewModelScope.launch {
            // Provide a test URL that our scraper will process.
            // Using a dummy url since we're using a generic scraping template right now.
            repository.scrapeAndCache("https://example.com/movie-1")
            repository.scrapeAndCache("https://example.com/movie-2")
        }
    }
}