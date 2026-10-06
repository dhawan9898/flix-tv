package com.example.flixtv.presentation.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val NetflixRed = Color(0xFFE50914)
private const val SEEK_STEP_MS = 10_000L
private const val FALLBACK_STREAM =
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

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

    val isDirectVideo = streamUrl.endsWith(".mp4", ignoreCase = true) ||
                        streamUrl.endsWith(".m3u8", ignoreCase = true) ||
                        streamUrl.contains(".mp4?", ignoreCase = true) ||
                        streamUrl.contains(".m3u8?", ignoreCase = true) ||
                        streamUrl.contains("/sample/", ignoreCase = true)

    val isEmbedLink = !isDirectVideo && (streamUrl.contains("/embed/") || streamUrl.contains("vidsrc"))
    var useWebPlayer by remember { mutableStateOf(isEmbedLink && streamUrl.isNotBlank()) }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasPlaybackError by remember { mutableStateOf(false) }
    var webLoadFailed by remember { mutableStateOf(false) }
    var webReloadKey by remember { mutableIntStateOf(0) }

    var showHud by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(1L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var seekToast by remember { mutableStateOf<String?>(null) }
    var gestureText by remember { mutableStateOf<String?>(null) }
    var isLocked by remember { mutableStateOf(false) }
    var showTracksDialog by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }

    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volumeLevel by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var brightnessLevel by remember {
        mutableFloatStateOf(
            ((context as? Activity)?.window?.attributes?.screenBrightness ?: -1f).let { if (it < 0f) 0.5f else it }
        )
    }

    val resolvedNativeStream = remember(streamUrl) {
        if (streamUrl.isNotBlank()) streamUrl else FALLBACK_STREAM
    }

    val resolvedEmbedStream = remember(embedUrl, streamUrl) {
        when {
            !embedUrl.isNullOrBlank() -> embedUrl
            isEmbedLink -> streamUrl
            else -> null
        }
    }

    // Landscape + immersive while the player is on screen, restored on exit.
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation =
                previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val exoPlayer = remember(resolvedNativeStream) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(httpDataSourceFactory)
            )
            .build().apply {
                setMediaItem(MediaItem.fromUri(resolvedNativeStream))
                prepare()
                // Never start audio behind the web player.
                playWhenReady = !useWebPlayer

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) hasPlaybackError = false
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onTracksChanged(newTracks: Tracks) {
                        tracks = newTracks
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        isBuffering = false
                        hasPlaybackError = true
                    }
                })
            }
    }

    LaunchedEffect(showHud, isPlaying, isScrubbing, hasPlaybackError, useWebPlayer) {
        if (showHud && isPlaying && !isScrubbing && !hasPlaybackError && !useWebPlayer) {
            delay(4000)
            showHud = false
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) {
                currentPosition = exoPlayer.currentPosition
            }
            totalDuration = exoPlayer.duration.let { if (it == C.TIME_UNSET) 1L else it.coerceAtLeast(1L) }
            delay(250)
        }
    }

    LaunchedEffect(gestureText) {
        if (gestureText != null) {
            delay(800)
            gestureText = null
        }
    }

    LaunchedEffect(seekToast) {
        if (seekToast != null) {
            delay(700)
            seekToast = null
        }
    }

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

    fun seekBy(deltaMs: Long) {
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        exoPlayer.seekTo((exoPlayer.currentPosition + deltaMs).coerceIn(0L, duration))
        seekToast = if (deltaMs > 0) "+10s" else "-10s"
    }

    fun retryNative() {
        hasPlaybackError = false
        isBuffering = true
        exoPlayer.setMediaItem(MediaItem.fromUri(resolvedNativeStream), currentPosition)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    BackHandler(enabled = onBackClick != null) { onBackClick?.invoke() }

    if (showTracksDialog) {
        TracksDialog(tracks = tracks, player = exoPlayer, onDismiss = { showTracksDialog = false })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (useWebPlayer) {
            WebEmbedPlayer(
                url = resolvedEmbedStream,
                reloadKey = webReloadKey,
                onLoadStarted = { isBuffering = true; webLoadFailed = false },
                onLoadFinished = { isBuffering = false },
                onLoadFailed = { isBuffering = false; webLoadFailed = true }
            )
        } else {
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

            // Tap toggles controls, double-tap seeks 10s either side (like Netflix).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isLocked) {
                        detectTapGestures(
                            onTap = { showHud = !showHud },
                            onDoubleTap = { offset ->
                                if (isLocked) return@detectTapGestures
                                if (offset.x < size.width / 2) seekBy(-SEEK_STEP_MS) else seekBy(SEEK_STEP_MS)
                            }
                        )
                    }
                    // Right half drags volume, left half drags brightness.
                    .pointerInput(isLocked) {
                        if (isLocked) return@pointerInput
                        var startX = 0f
                        detectVerticalDragGestures(
                            onDragStart = { startX = it.x },
                            onVerticalDrag = { change, dragY ->
                                change.consume()
                                val delta = -dragY / size.height
                                if (startX > size.width / 2) {
                                    volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC, (volumeLevel * maxVolume).roundToInt(), 0
                                    )
                                    gestureText = "Volume ${(volumeLevel * 100).roundToInt()}%"
                                } else {
                                    brightnessLevel = (brightnessLevel + delta).coerceIn(0.02f, 1f)
                                    (context as? Activity)?.window?.let { w ->
                                        w.attributes = w.attributes.apply { screenBrightness = brightnessLevel }
                                    }
                                    gestureText = "Brightness ${(brightnessLevel * 100).roundToInt()}%"
                                }
                            }
                        )
                    }
            )
        }

        if (isBuffering && !hasPlaybackError && !webLoadFailed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = NetflixRed,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Loading $title...",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Connecting to high-speed stream server...",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        gestureText?.let {
            Text(
                text = it,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        seekToast?.let {
            Text(
                text = it,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 96.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        val showError = if (useWebPlayer) webLoadFailed || resolvedEmbedStream == null else hasPlaybackError
        if (showError) {
            PlaybackErrorCard(
                message = if (useWebPlayer) {
                    "This source couldn't be loaded. Check your connection and try again."
                } else {
                    "We're having trouble playing this title."
                },
                onRetry = {
                    if (useWebPlayer) {
                        webLoadFailed = false
                        webReloadKey++
                    } else {
                        retryNative()
                    }
                },
                onAlternateSource = if (!useWebPlayer && resolvedEmbedStream != null) {
                    {
                        hasPlaybackError = false
                        exoPlayer.pause()
                        useWebPlayer = true
                    }
                } else null,
                onExit = { onBackClick?.invoke() }
            )
        }

        if (useWebPlayer) {
            // The embedded page owns its own controls; keep only a back button on top.
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", 36.dp) { onBackClick?.invoke() }
            }
        } else {
            AnimatedVisibility(
                visible = showHud && !hasPlaybackError && isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { isLocked = false }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = "Unlock controls", tint = Color.White)
                    Text("Unlock", color = Color.White, fontWeight = FontWeight.Medium)
                }
            }

            AnimatedVisibility(
                visible = showHud && !hasPlaybackError && !isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                NetflixControls(
                    title = title,
                    isPlaying = isPlaying,
                    position = if (isScrubbing) (scrubFraction * totalDuration).toLong() else currentPosition,
                    duration = totalDuration,
                    playbackSpeed = playbackSpeed,
                    onBack = { onBackClick?.invoke() },
                    onPlayPause = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    onRewind = { seekBy(-SEEK_STEP_MS) },
                    onForward = { seekBy(SEEK_STEP_MS) },
                    onScrub = { fraction ->
                        isScrubbing = true
                        scrubFraction = fraction
                    },
                    onScrubFinished = {
                        exoPlayer.seekTo((scrubFraction * totalDuration).toLong())
                        currentPosition = (scrubFraction * totalDuration).toLong()
                        isScrubbing = false
                    },
                    onLock = {
                        isLocked = true
                        showHud = false
                    },
                    onAudioSubtitles = { showTracksDialog = true },
                    onCycleSpeed = {
                        playbackSpeed = when (playbackSpeed) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2.0f
                            else -> 1.0f
                        }
                        exoPlayer.setPlaybackSpeed(playbackSpeed)
                    }
                )
            }
        }
    }
}

@Composable
private fun NetflixControls(
    title: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    playbackSpeed: Float,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onLock: () -> Unit,
    onAudioSubtitles: () -> Unit,
    onCycleSpeed: () -> Unit
) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(modifier = Modifier.fillMaxSize()) {
        // Top and bottom scrims keep the artwork visible while text stays legible.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", 40.dp, onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudIconButton(Icons.Default.Replay10, "Rewind 10 seconds", 52.dp, onClick = onRewind)
            HudIconButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                size = 72.dp,
                onClick = onPlayPause
            )
            HudIconButton(Icons.Default.Forward10, "Forward 10 seconds", 52.dp, onClick = onForward)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = fraction,
                    onValueChange = onScrub,
                    onValueChangeFinished = onScrubFinished,
                    colors = SliderDefaults.colors(
                        thumbColor = NetflixRed,
                        activeTrackColor = NetflixRed,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatMillis((duration - position).coerceAtLeast(0L)),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                ControlLabel(
                    Icons.Default.Speed,
                    "Speed (${if (playbackSpeed % 1f == 0f) playbackSpeed.toInt().toString() else playbackSpeed.toString()}x)",
                    onCycleSpeed
                )
                ControlLabel(Icons.Default.Subtitles, "Audio & Subtitles", onAudioSubtitles)
                ControlLabel(Icons.Default.Lock, "Lock", onLock)
            }
        }
    }
}

@Composable
private fun ControlLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TracksDialog(
    tracks: Tracks,
    player: Player,
    onDismiss: () -> Unit
) {
    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
    val textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

    fun label(group: Tracks.Group, index: Int): String {
        val format = group.getTrackFormat(0)
        return format.label
            ?: format.language?.let { java.util.Locale(it).displayLanguage }
            ?: "Track ${index + 1}"
    }

    fun select(group: Tracks.Group) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF181818))
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Audio", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (audioGroups.isEmpty()) {
                    Text("Default", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                }
                audioGroups.forEachIndexed { i, g ->
                    TrackRow(label(g, i), g.isSelected) { select(g) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Subtitles", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TrackRow("Off", textDisabled || textGroups.none { it.isSelected }) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    onDismiss()
                }
                textGroups.forEachIndexed { i, g ->
                    TrackRow(label(g, i), g.isSelected && !textDisabled) { select(g) }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = if (selected) "\u2713  $label" else "     $label",
        color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
private fun PlaybackErrorCard(
    message: String,
    onRetry: () -> Unit,
    onAlternateSource: (() -> Unit)?,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("Can't play this title", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(message, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("Retry", fontWeight = FontWeight.Bold) }
                if (onAlternateSource != null) {
                    TextButton(onClick = onAlternateSource) {
                        Text("Try another source", color = Color.White)
                    }
                }
                TextButton(onClick = onExit) { Text("Exit", color = Color.White.copy(alpha = 0.7f)) }
            }
        }
    }
}

@Composable
private fun HudIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(size * 0.8f))
    }
}

/**
 * Embedded web player. Navigation is pinned to the embed's own site and popup windows
 * are refused, so ad redirects can't hijack the player.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebEmbedPlayer(
    url: String?,
    reloadKey: Int,
    onLoadStarted: () -> Unit,
    onLoadFinished: () -> Unit,
    onLoadFailed: () -> Unit
) {
    if (url == null) return
    val allowedSite = remember(url) { registrableDomain(Uri.parse(url).host) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        val target = request.url
                        val sameSite = target.scheme in listOf("http", "https") &&
                            registrableDomain(target.host) == allowedSite
                        return !sameSite // true = block the navigation
                    }

                    override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                        onLoadStarted()
                    }

                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        onLoadFinished()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) onLoadFailed()
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean = false // no popups
                }

                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                tag = reloadKey
                loadUrl(url)
            }
        },
        update = { web ->
            if (web.tag != reloadKey) {
                web.tag = reloadKey
                web.loadUrl(url)
            }
        },
        onRelease = { web ->
            web.stopLoading()
            web.destroy()
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun registrableDomain(host: String?): String {
    val parts = host.orEmpty().lowercase().split('.')
    return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host.orEmpty()
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}
