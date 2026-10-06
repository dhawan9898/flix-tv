package com.example.flixtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.example.flixtv.domain.models.MediaItem
import com.example.flixtv.presentation.FlixViewModel
import com.example.flixtv.presentation.components.VideoPlayerScreen
import com.example.flixtv.presentation.screens.HomeScreen
import com.example.flixtv.presentation.screens.MediaDetailScreen
import com.example.flixtv.presentation.screens.SearchScreen
import com.example.flixtv.presentation.screens.SettingsScreen
import com.example.flixtv.theme.FlixTVTheme
import dagger.hilt.android.AndroidEntryPoint

// Define our routes as data objects / classes for Navigation 3
private data object HomeRoute
private data object SearchRoute
private data object SettingsRoute
private data class DetailRoute(val mediaItem: MediaItem)
private data class PlayerRoute(val streamUrl: String)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: FlixViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlixTVTheme {
                // Navigation 3 Backstack Management
                val backStack = remember { mutableStateListOf<Any>(HomeRoute) }
                
                // Collect Media state from Repository (via ViewModel)
                val mediaItems by viewModel.mediaItems.collectAsState()

                // Determine if we should show the bottom bar based on current route
                val currentRoute = backStack.lastOrNull()
                val showBottomBar = currentRoute is HomeRoute || currentRoute is SearchRoute || currentRoute is SettingsRoute

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0A0A0A),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                containerColor = Color(0xFF0A0A0A).copy(alpha = 0.95f),
                                contentColor = Color.White
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute is HomeRoute,
                                    onClick = { 
                                        if (currentRoute !is HomeRoute) {
                                            backStack.clear()
                                            backStack.add(HomeRoute)
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                    label = { Text("Home") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFFE50914),
                                        selectedTextColor = Color(0xFFE50914),
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentRoute is SearchRoute,
                                    onClick = { 
                                        if (currentRoute !is SearchRoute) {
                                            backStack.clear()
                                            backStack.add(SearchRoute)
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                    label = { Text("Search") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFFE50914),
                                        selectedTextColor = Color(0xFFE50914),
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentRoute is SettingsRoute,
                                    onClick = { 
                                        if (currentRoute !is SettingsRoute) {
                                            backStack.clear()
                                            backStack.add(SettingsRoute)
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFFE50914),
                                        selectedTextColor = Color(0xFFE50914),
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavDisplay(
                        backStack = backStack,
                        modifier = Modifier.padding(innerPadding),
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = { key ->
                            when (key) {
                                is HomeRoute -> NavEntry(key) {
                                    HomeScreen(
                                        mediaItems = mediaItems,
                                        onMediaClick = { mediaItem ->
                                            backStack.add(DetailRoute(mediaItem))
                                        }
                                    )
                                }
                                is SearchRoute -> NavEntry(key) {
                                    SearchScreen(
                                        mediaItems = mediaItems,
                                        onMediaClick = { mediaItem ->
                                            backStack.add(DetailRoute(mediaItem))
                                        }
                                    )
                                }
                                is SettingsRoute -> NavEntry(key) {
                                    SettingsScreen()
                                }
                                is DetailRoute -> NavEntry(key) {
                                    MediaDetailScreen(
                                        mediaItem = key.mediaItem,
                                        onPlayClick = { streamUrl ->
                                            backStack.add(PlayerRoute(streamUrl))
                                        }
                                    )
                                }
                                is PlayerRoute -> NavEntry(key) {
                                    VideoPlayerScreen(streamUrl = key.streamUrl)
                                }
                                else -> error("Unknown route: $key")
                            }
                        }
                    )
                }
            }
        }
    }
}
