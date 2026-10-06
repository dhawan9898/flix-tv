package com.example.flixtv.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.example.flixtv.theme.PrimaryAzure
import com.example.flixtv.theme.SecondaryIceCyan
import kotlinx.coroutines.delay

@Composable
fun HeroSpotlightSection(
    featuredItems: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (featuredItems.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }

    // Auto-advance carousel every 6 seconds
    LaunchedEffect(featuredItems.size) {
        if (featuredItems.size > 1) {
            while (true) {
                delay(6000)
                currentIndex = (currentIndex + 1) % featuredItems.size
            }
        }
    }

    val currentItem = featuredItems.getOrNull(currentIndex) ?: featuredItems.first()

    // Pulse animation for Premiere indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // visionOS Ambient Underglow (Light Spill Effect)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.9f)
                .height(180.dp)
                .offset(y = (-12).dp)
                .blur(48.dp)
                .background(
                    PrimaryAzure.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(9999.dp)
                )
        )

        // Main Hero Card
        val cardShape = RoundedCornerShape(24.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(cardShape)
                .background(Color.Black)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = cardShape
                )
                .shadow(
                    elevation = 28.dp,
                    shape = cardShape,
                    ambientColor = Color.Black.copy(alpha = 0.8f),
                    spotColor = Color.Black.copy(alpha = 0.8f)
                )
        ) {
            // Animated Cross-fade between featured slides
            AnimatedContent(
                targetState = currentItem,
                transitionSpec = {
                    fadeIn(animationSpec = tween(700)) + scaleIn(initialScale = 0.98f) togetherWith
                            fadeOut(animationSpec = tween(500))
                },
                label = "heroCarouselTransition"
            ) { item ->
                Box(modifier = Modifier.fillMaxSize()) {
                    // Media Backdrop Layer
                    AsyncImage(
                        model = item.backdropUrl ?: item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Scrim Vignette Gradients
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF08080C).copy(alpha = 0.35f),
                                        Color(0xFF08080C).copy(alpha = 0.60f),
                                        Color(0xFF08080C)
                                    ),
                                    startY = 50f
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF08080C).copy(alpha = 0.85f),
                                        Color.Transparent
                                    ),
                                    startX = 0f,
                                    endX = 500f
                                )
                            )
                    )
                }
            }

            // Top Badges Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live Ambient Indicator
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(9999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .shadow(8.dp, CircleShape, ambientColor = PrimaryAzure, spotColor = PrimaryAzure)
                                .background(PrimaryAzure.copy(alpha = pulseAlpha), CircleShape)
                        )
                        Text(
                            text = if (currentItem.category == "Anime") "ANIME PREMIERE" else "CINEWAVE PREMIERE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.08.sp
                        )
                    }
                }

                // Audio / Visual Spatial Tags
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("4K HDR", "Vision", "Atmos").forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9999.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(9999.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tag,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Bottom Content and Glass Floating CTA Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                // Metadata Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PrimaryAzure.copy(alpha = 0.25f))
                            .border(1.dp, PrimaryAzure.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = currentItem.category.uppercase(),
                            color = SecondaryIceCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.06.sp
                        )
                    }

                    Text(
                        text = "• ${currentItem.durationOrEpisodes}",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = SecondaryIceCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = currentItem.rating,
                            color = SecondaryIceCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Title
                Text(
                    text = currentItem.title,
                    color = Color.White,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Synopsis
                if (!currentItem.synopsis.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentItem.synopsis,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Glass Floating CTA Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Play Now Button
                    val playInteractionSource = remember { MutableInteractionSource() }
                    val playPressed by playInteractionSource.collectIsPressedAsState()
                    val playScale by animateFloatAsState(if (playPressed) 0.94f else 1.0f, label = "playBtnScale")

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .scale(playScale)
                            .clip(RoundedCornerShape(9999.dp))
                            .background(Color.White)
                            .shadow(12.dp, RoundedCornerShape(9999.dp), spotColor = Color.White.copy(alpha = 0.35f))
                            .clickable(
                                interactionSource = playInteractionSource,
                                indication = null
                            ) {
                                currentItem.streamUrl?.let { onPlayClick(it) } ?: onItemClick(currentItem)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Play Now",
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Watchlist Orb
                    FrostedOrbButton(
                        icon = Icons.Default.BookmarkBorder,
                        contentDescription = "Watchlist",
                        onClick = { /* Watchlist toggled */ }
                    )

                    // Details Orb
                    FrostedOrbButton(
                        icon = Icons.Default.Info,
                        contentDescription = "Info",
                        onClick = { onItemClick(currentItem) }
                    )
                }

                // Carousel Dot Indicators
                if (featuredItems.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        featuredItems.forEachIndexed { idx, _ ->
                            val isSelected = idx == currentIndex
                            val dotWidth by animateDpAsState(
                                targetValue = if (isSelected) 20.dp else 6.dp,
                                animationSpec = spring(stiffness = 500f),
                                label = "dotWidth"
                            )
                            val dotColor by animateColorAsState(
                                targetValue = if (isSelected) PrimaryAzure else Color.White.copy(alpha = 0.25f),
                                label = "dotColor"
                            )

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .height(6.dp)
                                    .width(dotWidth)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .clickable { currentIndex = idx }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FrostedOrbButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.90f else 1.0f, label = "orbScale")

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}
