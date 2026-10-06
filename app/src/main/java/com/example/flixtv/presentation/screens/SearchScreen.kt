package com.example.flixtv.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flixtv.domain.models.MediaItem
import com.example.flixtv.presentation.components.MediaCard
import com.example.flixtv.presentation.components.shimmerEffect
import com.example.flixtv.theme.PrimaryAzure
import com.example.flixtv.theme.SecondaryIceCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchQuery: String,
    searchResults: List<MediaItem>,
    isSearching: Boolean,
    allMedia: List<MediaItem>,
    onQueryChanged: (String) -> Unit,
    onMediaClick: (MediaItem) -> Unit
) {
    var searchFilter by remember { mutableStateOf("All") }

    val displayItems = remember(searchQuery, searchResults, allMedia, searchFilter) {
        val baseList = if (searchQuery.isNotBlank()) searchResults else allMedia
        when (searchFilter) {
            "Movies" -> baseList.filter { it.category == "Movie" }
            "TV Shows" -> baseList.filter { it.category == "TV Show" }
            "Anime" -> baseList.filter { it.category == "Anime" }
            else -> baseList
        }
    }

    val filterOptions = listOf("All", "Movies", "TV Shows", "Anime")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08080C))
    ) {
        // Search Header with Liquid Glass Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF08080C).copy(alpha = 0.85f))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Search movies, TV shows, anime...",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = PrimaryAzure
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChanged("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryAzure,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                        focusedContainerColor = Color(0xFF16161C),
                        unfocusedContainerColor = Color(0xFF16161C).copy(alpha = 0.7f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = PrimaryAzure
                    ),
                    shape = RoundedCornerShape(9999.dp),
                    singleLine = true
                )

                // Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterOptions) { filter ->
                        val isSelected = filter == searchFilter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9999.dp))
                                .background(
                                    if (isSelected) PrimaryAzure.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.06f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) PrimaryAzure.copy(alpha = 0.6f)
                                    else Color.White.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(9999.dp)
                                )
                                .clickable { searchFilter = filter }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = filter,
                                color = if (isSelected) SecondaryIceCyan else Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Live Search Results or Empty State
        if (isSearching) {
            // Loading Skeletons
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        repeat(3) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.68f)
                                        .clip(RoundedCornerShape(20.dp))
                                        .shimmerEffect()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .shimmerEffect()
                                )
                            }
                        }
                    }
                }
            }
        } else if (searchQuery.isNotBlank() && displayItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "No results found for \"$searchQuery\"",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Try searching for movie titles, TV shows, or anime like \"One Piece\", \"Spider-Man\", or \"Interstellar\".",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (searchQuery.isBlank()) {
                    item {
                        Text(
                            text = "Popular Suggestions",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "Found ${displayItems.size} matches",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }

                val rowChunkSize = 3
                val chunks = displayItems.chunked(rowChunkSize)

                items(chunks) { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        for (i in 0 until rowChunkSize) {
                            if (i < rowItems.size) {
                                val item = rowItems[i]
                                Box(modifier = Modifier.weight(1f)) {
                                    MediaCard(
                                        item = item,
                                        onClick = { onMediaClick(item) }
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}