package com.example.flixtv.presentation.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.OptIn
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.flixtv.theme.PrimaryAzure
import com.example.flixtv.theme.SecondaryIceCyan
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    streamUrl: String,
    title: String = "Now Playing",
    embedUrl: String? = null,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Decide initial player mode: if streamUrl is an embed or user chose web, use web player
    var useWebPlayer by remember {
        mutableStateOf(streamUrl.contains("vidsrc") || streamUrl.contains("embed") || streamUrl.isBlank())
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasPlaybackError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showHud by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(1L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // Active stream resolution
    val resolvedNativeStream = remember(streamUrl) {
        if (streamUrl.isNotBlank() && !streamUrl.contains("vidsrc") && !streamUrl.contains("embed")) {
            streamUrl
        } else {
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        }
    }

    val resolvedEmbedStream = remember(embedUrl, streamUrl) {
        when {
            !embedUrl.isNullOrBlank() -> embedUrl
            streamUrl.contains("vidsrc") || streamUrl.contains("embed") -> streamUrl
            else -> "https://vidsrc.xyz/embed/movie?tmdb=693134"
        }
    }

    // Configure ExoPlayer with custom User-Agent and timeouts to prevent 403 Forbidden
    val exoPlayer = remember(resolvedNativeStream) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                val mediaItem = MediaItem.fromUri(resolvedNativeStream)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) {
                            hasPlaybackError = false
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        isBuffering = false
                        hasPlaybackError = true
                        errorMessage = "Native stream error (${error.errorCodeName}). Try Web Stream."
                    }
                })
            }
    }

    // Auto-hide HUD after 5 seconds of inactivity
    LaunchedEffect(showHud, isPlaying) {
        if (showHud && isPlaying && !hasPlaybackError) {
            delay(5000)
            showHud = false
        }
    }

    // Progress polling for timeline
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            totalDuration = exoPlayer.duration.coerceAtLeast(1L)
            delay(500)
        }
    }

    // Manage lifecycle
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (isPlaying && !useWebPlayer) exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulseGlow")
    val pulseGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showHud = !showHud }
    ) {
        if (useWebPlayer) {
            // Web Embed Stream Player Engine (VidSrc, SuperEmbed, Anime Web Player)
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.userAgentString =
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isBuffering = false
                            }
                        }

                        webChromeClient = object : WebChromeClient() {}

                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        loadUrl(resolvedEmbedStream)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Native ExoPlayer Stream Engine
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Animated Buffering Spinner
        if (isBuffering && !hasPlaybackError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = PrimaryAzure,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Playback Error Recovery Card
        if (hasPlaybackError && !useWebPlayer) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF16161C).copy(alpha = 0.95f))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Color(0xFFFFB4AB),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Stream Unavailable",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = errorMessage ?: "The direct CDN video stream encountered an error.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                useWebPlayer = true
                                isBuffering = true
                                hasPlaybackError = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAzure),
                            shape = RoundedCornerShape(9999.dp)
                        ) {
                            Text("Switch to Web Stream (VidSrc)", fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = {
                                hasPlaybackError = false
                                exoPlayer.seekTo(0)
                                exoPlayer.prepare()
                                exoPlayer.play()
                            }
                        ) {
                            Text("Retry Direct Stream", color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        // Custom CineWave visionOS Liquid Glass HUD Overlay
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f))
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                // Top Overlay Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button orb
                    FrostedHudOrb(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = { onBackClick?.invoke() }
                    )

                    // Title Capsule
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(9999.dp))
                            .background(Color(0xFF16161C).copy(alpha = 0.85f))
                            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(9999.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(PrimaryAzure, CircleShape)
                                    .shadow(8.dp, CircleShape, spotColor = PrimaryAzure)
                            )
                            Text(
                                text = title.uppercase(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.05.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Engine Mode Switcher Pill (Native Player vs Web Stream)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(if (useWebPlayer) SecondaryIceCyan.copy(alpha = 0.25f) else PrimaryAzure.copy(alpha = 0.25f))
                            .border(
                                1.dp,
                                if (useWebPlayer) SecondaryIceCyan.copy(alpha = 0.6f) else PrimaryAzure.copy(alpha = 0.6f),
                                RoundedCornerShape(9999.dp)
                            )
                            .clickable {
                                useWebPlayer = !useWebPlayer
                                if (useWebPlayer) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (useWebPlayer) "WEB STREAM" else "NATIVE",
                            color = if (useWebPlayer) SecondaryIceCyan else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Center Optical Controls (Shown when Native Player is active)
                if (!useWebPlayer) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 10s Rewind Orb
                        FrostedHudOrb(
                            icon = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            size = 54.dp,
                            onClick = {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
                            }
                        )

                        // Focal Play / Pause Core Island with Cyan Pulse Glow
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(84.dp)
                        ) {
                            // Ambient Pulse Glow
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(20.dp)
                                    .background(PrimaryAzure.copy(alpha = pulseGlowAlpha), CircleShape)
                            )

                            // Main Play Island
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryAzure)
                                    .shadow(24.dp, CircleShape, spotColor = PrimaryAzure)
                                    .clickable {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        isPlaying = !isPlaying
                                    },
                            contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // 10s Forward Orb
                        FrostedHudOrb(
                            icon = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            size = 54.dp,
                            onClick = {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                            }
                        )
                    }
                }

                // Bottom Timeline & Scrubber Capsule (Only for Native Player)
                if (!useWebPlayer) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val progressFraction = if (totalDuration > 0) {
                            (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        // Track Rail with Glass Pearl Scrubber
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Background track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(9999.dp))
                                    .background(Color.White.copy(alpha = 0.20f))
                            )

                            // Elapsed fill
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(9999.dp))
                                    .background(PrimaryAzure)
                                    .shadow(8.dp, RoundedCornerShape(9999.dp), spotColor = PrimaryAzure)
                            )

                            // Glass Pearl Scrubber Head
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .wrapContentWidth(Alignment.End)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(2.dp, PrimaryAzure, CircleShape)
                                    .shadow(8.dp, CircleShape, spotColor = Color.White)
                            )
                        }

                        // Timestamp Readout & Utility Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatMillis(currentPosition),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Speed selector pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9999.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(9999.dp))
                                    .clickable {
                                        playbackSpeed = when (playbackSpeed) {
                                            1.0f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2.0f
                                            else -> 1.0f
                                        }
                                        exoPlayer.setPlaybackSpeed(playbackSpeed)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    color = PrimaryAzure,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "-${formatMillis((totalDuration - currentPosition).coerceAtLeast(0L))}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrostedHudOrb(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.90f else 1.0f, label = "hudOrbScale")

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(Color(0xFF16161C).copy(alpha = 0.75f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
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
            modifier = Modifier.size(size * 0.45f)
        )
    }
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}