package com.example.flixtv.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.flixtv.domain.models.MediaItem
import com.example.flixtv.presentation.components.FrostedOrbButton
import com.example.flixtv.theme.PrimaryAzure
import com.example.flixtv.theme.SecondaryIceCyan

@Composable
fun MediaDetailScreen(
    mediaItem: MediaItem,
    onBackClick: () -> Unit,
    onPlayClick: (String) -> Unit
) {
    var isExpandedSynopsis by remember { mutableStateOf(false) }
    var isInWatchlist by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08080C))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp)
        ) {
            // Full-Bleed Hero Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                // Backdrop Image
                AsyncImage(
                    model = mediaItem.backdropUrl ?: mediaItem.posterUrl,
                    contentDescription = mediaItem.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Atmospheric Gradients
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF08080C).copy(alpha = 0.4f),
                                    Color(0xFF08080C).copy(alpha = 0.7f),
                                    Color(0xFF08080C)
                                )
                            )
                        )
                )

                // Top Bar with Back Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable { onBackClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Floating Top Badges
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9999.dp))
                                .background(PrimaryAzure.copy(alpha = 0.25f))
                                .border(1.dp, PrimaryAzure.copy(alpha = 0.45f), RoundedCornerShape(9999.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (mediaItem.category == "Anime") "ANIME PREMIERE" else "ORIGINAL FILM",
                                color = SecondaryIceCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.05.sp
                            )
                        }
                    }
                }
            }

            // Floated Content Layer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-40).dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title & Specs Glass Plaque
                val plaqueShape = RoundedCornerShape(24.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(plaqueShape)
                        .background(Color(0xFF16161C).copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), plaqueShape)
                        .shadow(20.dp, plaqueShape, spotColor = Color.Black)
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Specular Highlight line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.25f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Studio identity
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AllInclusive,
                                contentDescription = null,
                                tint = PrimaryAzure,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "CINEWAVE STUDIOS • PREMIERE",
                                color = PrimaryAzure,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.06.sp
                            )
                        }

                        // Title
                        Text(
                            text = mediaItem.title,
                            color = Color.White,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 30.sp,
                                lineHeight = 36.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        // Metadata Badges Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = mediaItem.releaseYear,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(text = "•", color = Color.White.copy(alpha = 0.4f))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "TV-MA",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(text = "•", color = Color.White.copy(alpha = 0.4f))
                            Text(
                                text = mediaItem.durationOrEpisodes,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                            Text(text = "•", color = Color.White.copy(alpha = 0.4f))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = mediaItem.category,
                                    color = SecondaryIceCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Sensory Audio/Visual Quality Pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            listOf("4K Ultra HD", "HDR10+", "Spatial Audio", "CC").forEach { pill ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9999.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = pill,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Primary Interaction Bar: Wide Play + Liquid Frosted Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val playInteractionSource = remember { MutableInteractionSource() }
                    val playPressed by playInteractionSource.collectIsPressedAsState()
                    val playScale by animateFloatAsState(if (playPressed) 0.95f else 1.0f, label = "detailPlayScale")

                    // Main Frosted Play Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .scale(playScale)
                            .clip(RoundedCornerShape(9999.dp))
                            .background(PrimaryAzure)
                            .shadow(16.dp, RoundedCornerShape(9999.dp), spotColor = PrimaryAzure)
                            .clickable(
                                interactionSource = playInteractionSource,
                                indication = null
                            ) {
                                mediaItem.streamUrl?.let { onPlayClick(it) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = if (mediaItem.category == "Anime") "Watch Anime" else "Play Movie",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Liquid Icon Orbs
                    FrostedOrbButton(
                        icon = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Watchlist",
                        onClick = { isInWatchlist = !isInWatchlist }
                    )

                    FrostedOrbButton(
                        icon = Icons.Default.FileDownload,
                        contentDescription = "Download",
                        onClick = { /* Download */ }
                    )

                    FrostedOrbButton(
                        icon = Icons.Default.Share,
                        contentDescription = "Share",
                        onClick = { /* Share */ }
                    )
                }

                // Expandable Acrylic Synopsis Panel
                val synopsisShape = RoundedCornerShape(20.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(synopsisShape)
                        .background(Color(0xFF16161C).copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), synopsisShape)
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Storyline",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = SecondaryIceCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = mediaItem.rating,
                                    color = SecondaryIceCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = mediaItem.synopsis ?: "No storyline available.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = if (isExpandedSynopsis) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (isExpandedSynopsis) "Show Less" else "Read More",
                            color = PrimaryAzure,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { isExpandedSynopsis = !isExpandedSynopsis }
                                .padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}