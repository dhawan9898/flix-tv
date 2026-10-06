package com.example.flixtv.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.flixtv.domain.models.MediaItem
import com.example.flixtv.presentation.components.CategoryPillsBar
import com.example.flixtv.presentation.components.HeroSpotlightSection
import com.example.flixtv.presentation.components.MediaCard
import com.example.flixtv.presentation.components.shimmerEffect
import com.example.flixtv.theme.PrimaryAzure
import com.example.flixtv.theme.SecondaryIceCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mediaItems: List<MediaItem>,
    featuredItems: List<MediaItem>,
    selectedCategory: String,
    isLoading: Boolean,
    onCategorySelected: (String) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (String) -> Unit
) {
    Scaffold(
        topBar = {
            // visionOS Acrylic Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF08080C).copy(alpha = 0.75f))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo & App Name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryAzure)
                                .shadow(8.dp, RoundedCornerShape(8.dp), spotColor = PrimaryAzure),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CW",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }

                        Text(
                            text = "CineWave",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = Color.White
                        )
                    }

                    // Profile Avatar Glass Orb
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .padding(2.dp)
                    ) {
                        AsyncImage(
                            model = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=200&auto=format&fit=crop",
                            contentDescription = "Profile",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF08080C)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Category Pills Filter Bar
            item {
                CategoryPillsBar(
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected
                )
            }

            // Cinematic Hero Spotlight Billboard (Only show when on All or Movies/Anime if featured exists)
            val spotlightList = if (featuredItems.isNotEmpty()) {
                if (selectedCategory == "All") featuredItems else featuredItems.filter { it.category.equals(selectedCategory, ignoreCase = true) }.ifEmpty { featuredItems }
            } else mediaItems.take(4)

            if (spotlightList.isNotEmpty() && !isLoading) {
                item {
                    HeroSpotlightSection(
                        featuredItems = spotlightList,
                        onItemClick = onMediaClick,
                        onPlayClick = onPlayClick
                    )
                }
            }

            // "Continue Watching / Trending" Horizontal Rail
            val trendingRailItems = mediaItems.filter {
                it.genres.any { g -> g.contains("Trending", ignoreCase = true) || g.contains("Top", ignoreCase = true) }
            }.ifEmpty { mediaItems.take(8) }

            if (trendingRailItems.isNotEmpty() && !isLoading) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (selectedCategory == "Anime") "Trending Anime" else "Trending Worldwide",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(PrimaryAzure, CircleShape)
                                        .shadow(6.dp, CircleShape, spotColor = PrimaryAzure)
                                )
                            }

                            Text(
                                text = "See All",
                                color = SecondaryIceCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            itemsIndexed(trendingRailItems.take(8)) { index, item ->
                                Box(modifier = Modifier.width(135.dp)) {
                                    MediaCard(
                                        item = item,
                                        rankNumber = index + 1,
                                        onClick = { onMediaClick(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section Header for Main Grid
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when (selectedCategory) {
                            "Movies" -> "Popular & Blockbuster Movies"
                            "TV Shows" -> "Binge-Worthy TV Series"
                            "Anime" -> "Top Japanese Anime Series & Films"
                            "Trending" -> "Global Top Trending"
                            else -> "Explore Movies, TV & Anime"
                        },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Loading Shimmer Skeletons or Grid
            if (isLoading && mediaItems.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                                .fillMaxWidth(0.8f)
                                                .height(14.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .shimmerEffect()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Adaptive Grid rows
                val rowChunkSize = 3
                val chunks = mediaItems.chunked(rowChunkSize)

                items(chunks) { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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