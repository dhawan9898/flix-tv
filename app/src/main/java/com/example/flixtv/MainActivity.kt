package com.example.flixtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.example.flixtv.domain.models.EpisodeItem
import com.example.flixtv.domain.models.MediaItem
import com.example.flixtv.presentation.FlixViewModel
import com.example.flixtv.presentation.components.AnimePlayerScreen
import com.example.flixtv.presentation.components.VideoPlayerScreen
import com.example.flixtv.presentation.screens.HomeScreen
import com.example.flixtv.presentation.screens.MediaDetailScreen
import com.example.flixtv.presentation.screens.SearchScreen
import com.example.flixtv.presentation.screens.SettingsScreen
import com.example.flixtv.theme.FlixTVTheme
import dagger.hilt.android.AndroidEntryPoint

// Navigation 3 routes
private data object HomeRoute
private data object SearchRoute
private data object AnimeRoute
private data object SettingsRoute
private data class DetailRoute(val mediaItem: MediaItem)
private data class AnimePlayerRoute(
    val item: MediaItem,
    val episodes: List<EpisodeItem>,
    val episodeNumber: Int
)
private data class PlayerRoute(
    val streamUrl: String,
    val title: String,
    val embedUrl: String? = null
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: FlixViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlixTVTheme {
                val backStack = remember { mutableStateListOf<Any>(HomeRoute) }

                // State collection from ViewModel
                val mediaItems by viewModel.filteredMediaItems.collectAsState()
                val allMedia by viewModel.mediaItems.collectAsState()
                val featuredItems by viewModel.featuredItems.collectAsState()
                val selectedCategory by viewModel.selectedCategory.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()

                val searchQuery by viewModel.searchQuery.collectAsState()
                val searchResults by viewModel.searchResults.collectAsState()
                val isSearching by viewModel.isSearching.collectAsState()

                val currentRoute = backStack.lastOrNull()
                val showBottomBar = currentRoute is HomeRoute || currentRoute is SearchRoute ||
                        currentRoute is AnimeRoute || currentRoute is SettingsRoute

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF08080C))
                ) {
                    // Main Nav Display
                    NavDisplay(
                        backStack = backStack,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = { key ->
                            when (key) {
                                is HomeRoute -> NavEntry(key) {
                                    HomeScreen(
                                        mediaItems = mediaItems,
                                        featuredItems = featuredItems,
                                        selectedCategory = selectedCategory,
                                        isLoading = isLoading,
                                        onCategorySelected = { viewModel.setCategory(it) },
                                        onMediaClick = { backStack.add(DetailRoute(it)) },
                                        onPlayClick = { stream, title, embed ->
                                            backStack.add(PlayerRoute(stream, title, embed))
                                        }
                                    )
                                }
                                is SearchRoute -> NavEntry(key) {
                                    SearchScreen(
                                        searchQuery = searchQuery,
                                        searchResults = searchResults,
                                        isSearching = isSearching,
                                        allMedia = allMedia,
                                        onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                                        onMediaClick = { backStack.add(DetailRoute(it)) }
                                    )
                                }
                                is AnimeRoute -> NavEntry(key) {
                                    // Direct Anime Discovery Section
                                    val animeItems = allMedia.filter { it.category == "Anime" }
                                    HomeScreen(
                                        mediaItems = animeItems,
                                        featuredItems = featuredItems.filter { it.category == "Anime" },
                                        selectedCategory = "Anime",
                                        isLoading = isLoading,
                                        onCategorySelected = { cat ->
                                            if (cat != "Anime") {
                                                viewModel.setCategory(cat)
                                                backStack.clear()
                                                backStack.add(HomeRoute)
                                            }
                                        },
                                        onMediaClick = { backStack.add(DetailRoute(it)) },
                                        onPlayClick = { stream, title, embed ->
                                            backStack.add(PlayerRoute(stream, title, embed))
                                        }
                                    )
                                }
                                is SettingsRoute -> NavEntry(key) {
                                    SettingsScreen(
                                        onRefreshCatalog = { viewModel.loadData() }
                                    )
                                }
                                is DetailRoute -> NavEntry(key) {
                                    MediaDetailScreen(
                                        mediaItem = key.mediaItem,
                                        onBackClick = { backStack.removeLastOrNull() },
                                        onPlayClick = { stream, title, embed ->
                                            backStack.add(PlayerRoute(stream, title, embed))
                                        },
                                        loadAnimeEpisodes = { viewModel.loadAnimeEpisodes(it) },
                                        onPlayAnimeEpisode = { item, episodes, number ->
                                            backStack.add(AnimePlayerRoute(item, episodes, number))
                                        }
                                    )
                                }
                                is AnimePlayerRoute -> NavEntry(key) {
                                    AnimePlayerScreen(
                                        item = key.item,
                                        episodes = key.episodes,
                                        startEpisode = key.episodeNumber,
                                        resolveSource = { ep, mode -> viewModel.resolveAnimeSource(key.item, ep, mode) },
                                        lookupSkipIntervals = { title, ep, len -> viewModel.skipIntervals(title, ep, len) },
                                        onBackClick = { backStack.removeLastOrNull() }
                                    )
                                }
                                is PlayerRoute -> NavEntry(key) {
                                    VideoPlayerScreen(
                                        streamUrl = key.streamUrl,
                                        title = key.title,
                                        embedUrl = key.embedUrl,
                                        onBackClick = { backStack.removeLastOrNull() }
                                    )
                                }
                                else -> error("Unknown route: $key")
                            }
                        }
                    )

                    // visionOS Floating Capsule Dock Navigation
                    if (showBottomBar) {
                        VisionOsFloatingDock(
                            currentRoute = currentRoute,
                            onTabSelected = { route ->
                                backStack.clear()
                                backStack.add(route)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisionOsFloatingDock(
    currentRoute: Any?,
    onTabSelected: (Any) -> Unit,
    modifier: Modifier = Modifier
) {
    val dockShape = RoundedCornerShape(9999.dp)

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .clip(dockShape)
            .background(Color(0xFF0F111A).copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.22f),
                shape = dockShape
            )
            .shadow(
                elevation = 28.dp,
                shape = dockShape,
                ambientColor = Color.Black.copy(alpha = 0.85f),
                spotColor = Color.Black.copy(alpha = 0.85f)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VisionOsDockTab(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentRoute is HomeRoute,
                onClick = { onTabSelected(HomeRoute) }
            )

            VisionOsDockTab(
                icon = Icons.Default.Search,
                label = "Search",
                isSelected = currentRoute is SearchRoute,
                onClick = { onTabSelected(SearchRoute) }
            )

            VisionOsDockTab(
                icon = Icons.Default.Tv,
                label = "Anime",
                isSelected = currentRoute is AnimeRoute,
                onClick = { onTabSelected(AnimeRoute) }
            )

            VisionOsDockTab(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = currentRoute is SettingsRoute,
                onClick = { onTabSelected(SettingsRoute) }
            )
        }
    }
}

@Composable
private fun VisionOsDockTab(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = if (isPressed) 0.92f else 1.0f

    val tabShape = RoundedCornerShape(9999.dp)

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(tabShape)
            .background(
                if (isSelected) Color.White.copy(alpha = 0.20f)
                else Color.Transparent
            )
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) Color.White.copy(alpha = 0.28f) else Color.Transparent,
                shape = tabShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(
                horizontal = if (isSelected) 16.dp else 10.dp,
                vertical = 8.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp)
            )

            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + expandHorizontally(animationSpec = spring(stiffness = 500f)),
                exit = fadeOut() + shrinkHorizontally(animationSpec = spring(stiffness = 500f))
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.01.sp
                )
            }
        }
    }
}
