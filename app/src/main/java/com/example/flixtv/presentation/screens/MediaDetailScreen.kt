package com.example.flixtv.presentation.screens

import androidx.compose.animation.*
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
import com.example.flixtv.domain.models.EpisodeItem
import com.example.flixtv.domain.models.MediaItem
import com.example.flixtv.presentation.components.FrostedOrbButton
import com.example.flixtv.theme.PrimaryAzure
import com.example.flixtv.theme.SecondaryIceCyan

@Composable
fun MediaDetailScreen(
    mediaItem: MediaItem,
    onBackClick: () -> Unit,
    onPlayClick: (streamUrl: String, title: String, embedUrl: String?) -> Unit,
    loadAnimeEpisodes: suspend (MediaItem) -> List<EpisodeItem> = { emptyList() },
    onPlayAnimeEpisode: (item: MediaItem, episodes: List<EpisodeItem>, episodeNumber: Int) -> Unit = { _, _, _ -> },
    onPlayResolved: (item: MediaItem, title: String, season: Int, episode: Int) -> Unit = { _, _, _, _ -> }
) {
    var isExpandedSynopsis by remember { mutableStateOf(false) }
    var isInWatchlist by remember { mutableStateOf(false) }
    val isSeries = mediaItem.category == "TV Show" || mediaItem.category == "Anime"

    val isAnime = mediaItem.category == "Anime"
    var animeEpisodes by remember(mediaItem.id) { mutableStateOf<List<EpisodeItem>?>(null) }
    if (isAnime) {
        LaunchedEffect(mediaItem.id) { animeEpisodes = loadAnimeEpisodes(mediaItem) }
    }
    val episodes = if (isAnime && !animeEpisodes.isNullOrEmpty()) animeEpisodes.orEmpty() else mediaItem.episodes
    val episodesLoading = isAnime && animeEpisodes == null
    val firstEp = episodes.firstOrNull()
    val defaultStream = firstEp?.streamUrl ?: mediaItem.streamUrl ?: firstEp?.embedUrl ?: mediaItem.embedUrl.orEmpty()
    val defaultEmbed = firstEp?.embedUrl ?: mediaItem.embedUrl

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
                    .height(380.dp)
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
                                    Color(0xFF08080C).copy(alpha = 0.35f),
                                    Color(0xFF08080C).copy(alpha = 0.70f),
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
                                text = if (mediaItem.category == "Anime") "ANIME SERIES" else if (isSeries) "TV SERIES" else "ORIGINAL FILM",
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

                        // Studio / Provider identity
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
                            val providerName = if (mediaItem.provider.isNotBlank()) mediaItem.provider else "Flexeo"
                            Text(
                                text = "$providerName Streaming Provider • HD Quality",
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
                                fontSize = 28.sp,
                                lineHeight = 34.sp,
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
                                    text = if (isSeries) "TV-MA" else "PG-13",
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
                                if (isAnime) {
                                    firstEp?.let { onPlayAnimeEpisode(mediaItem, episodes, it.episodeNumber) }
                                } else if (mediaItem.tmdbId != null) {
                                    onPlayResolved(
                                        mediaItem,
                                        if (isSeries) "${mediaItem.title} - S${firstEp?.seasonNumber ?: 1}:E${firstEp?.episodeNumber ?: 1}" else mediaItem.title,
                                        firstEp?.seasonNumber ?: 1,
                                        firstEp?.episodeNumber ?: 1
                                    )
                                } else {
                                    onPlayClick(
                                        defaultStream,
                                        if (isSeries) "${mediaItem.title} - S1:E1" else mediaItem.title,
                                        defaultEmbed
                                    )
                                }
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
                                text = when {
                                    episodesLoading -> "Loading episodes..."
                                    isAnime && firstEp == null -> "No episodes found"
                                    isAnime -> "Play E${firstEp!!.episodeNumber}"
                                    isSeries -> "Play S1:E1"
                                    else -> "Play Movie"
                                },
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

                    // Web Stream Direct Button (anime plays through the native resolver instead)
                    if (!isAnime) {
                        FrostedOrbButton(
                            icon = Icons.Default.Language,
                            contentDescription = "Web Stream",
                            onClick = {
                                onPlayClick(
                                    defaultStream,
                                    mediaItem.title,
                                    defaultEmbed
                                )
                            }
                        )
                    }
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

                // TV Shows & Anime: Episodes List Section
                if (isSeries && episodes.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Episodes",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(PrimaryAzure.copy(alpha = 0.25f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${episodes.size} Episodes",
                                        color = SecondaryIceCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9999.dp))
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(9999.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Season 1",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Episode Cards List
                        episodes.forEach { episode ->
                            EpisodeCard(
                                episode = episode,
                                backdropFallback = mediaItem.backdropUrl ?: mediaItem.posterUrl,
                                onEpisodeClick = {
                                    if (isAnime) {
                                        onPlayAnimeEpisode(mediaItem, episodes, episode.episodeNumber)
                                    } else if (mediaItem.tmdbId != null) {
                                        onPlayResolved(
                                            mediaItem,
                                            "${mediaItem.title} - ${episode.title}",
                                            episode.seasonNumber,
                                            episode.episodeNumber
                                        )
                                    } else {
                                        onPlayClick(
                                            episode.streamUrl ?: defaultStream,
                                            "${mediaItem.title} - ${episode.title}",
                                            episode.embedUrl ?: defaultEmbed
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: EpisodeItem,
    backdropFallback: String,
    onEpisodeClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1.0f, label = "epScale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(cardShape)
            .background(Color(0xFF14151C))
            .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onEpisodeClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode Thumbnail with Play Badge
            Box(
                modifier = Modifier
                    .width(115.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0E0E12))
            ) {
                AsyncImage(
                    model = episode.stillUrl ?: backdropFallback,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Vignette
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )

                // Play Button Orb in Center
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Duration badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = episode.duration,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Episode Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = episode.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!episode.overview.isNullOrBlank()) {
                    Text(
                        text = episode.overview,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}