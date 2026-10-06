package com.example.flixtv.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.flixtv.domain.models.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    mediaItems: List<MediaItem>,
    onMediaClick: (MediaItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    // Simple local filter for demonstration. 
    // In a production app, this should hit the ViewModel/Room Database or a Remote API.
    val filteredItems = remember(searchQuery, mediaItems) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            mediaItems.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)) // Deep Obsidian Dark
    ) {
        // Search Bar Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f)) // Liquid Glass
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .statusBarsPadding()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search movies, shows...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE50914), // Red streaming accent
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFE50914)
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
        }

        // Results Grid
        if (searchQuery.isNotBlank() && filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    text = "No results found for \"$searchQuery\"",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredItems) { item ->
                    MediaCard(item = item, onClick = { onMediaClick(item) })
                }
            }
        }
    }
}