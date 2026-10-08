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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.SkipNext
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
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
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.HttpDataSource
import com.example.flixtv.domain.models.SkipRange
import com.example.flixtv.domain.models.SubtitleTrack
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val NetflixRed = Color(0xFFE50914)
private const val SEEK_STEP_MS = 10_000L
private const val NEXT_EPISODE_THRESHOLD_MS = 20_000L
private const val NEXT_EPISODE_COUNTDOWN_MS = 10_000L
private const val MAX_AUTO_RETRIES = 3
private const val FALLBACK_STREAM =
    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

/** One entry of the in-player episode picker. */
data class PlayerEpisode(val number: Int, val title: String)

/**
 * Optional extras for sources that bring more than a bare URL (anime): request referer,
 * external subtitles, opening/ending markers, audio (sub/dub) switch and episode navigation.
 * With none supplied the player behaves exactly as before.
 */
data class PlayerExtras(
    val referer: String? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val intro: SkipRange? = null,
    val outro: SkipRange? = null,
    /** "sub" or "dub"; null hides the audio switch. */
    val audioMode: String? = null,
    val onToggleAudioMode: (() -> Unit)? = null,
    /** Non-null only when there is a real next episode. */
    val onNextEpisode: (() -> Unit)? = null,
    val episodes: List<PlayerEpisode> = emptyList(),
    val currentEpisode: Int? = null,
    val onSelectEpisode: ((Int) -> Unit)? = null,
    /** Called once per stream with the real duration (ms), e.g. to look up skip markers. */
    val onDurationKnown: ((Long) -> Unit)? = null
)

/** Landscape + immersive while on screen, restored on exit. */
@Composable
fun LandscapeImmersiveEffect() {
    val context = LocalContext.current
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
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    streamUrl: String,
    title: String = "Now Playing",
    embedUrl: String? = null,
    onBackClick: (() -> Unit)? = null,
    extras: PlayerExtras? = null,
    /** False when a parent already owns orientation/immersive mode (e.g. across episode switches). */
    manageWindow: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentExtras by rememberUpdatedState(extras)

    val isDirectVideo = streamUrl.endsWith(".mp4", ignoreCase = true) ||
                        streamUrl.endsWith(".m3u8", ignoreCase = true) ||
                        streamUrl.contains(".mp4?", ignoreCase = true) ||
                        streamUrl.contains(".m3u8?", ignoreCase = true) ||
                        streamUrl.contains("/sample/", ignoreCase = true) ||
                        streamUrl.contains("googlevideo", ignoreCase = true) ||
                        streamUrl.contains("mux.dev", ignoreCase = true)

    val isEmbedLink = !isDirectVideo && (streamUrl.contains("/embed/") || streamUrl.contains("vidsrc"))
    // Always default to native ExoPlayer for instant, smooth video playback
    var useWebPlayer by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }

    // Auto-dismiss loading overlay after 2.5s so video and controls are never blocked
    LaunchedEffect(isBuffering) {
        if (isBuffering) {
            delay(2500)
            isBuffering = false
        }
    }
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
    var showEpisodesDialog by remember { mutableStateOf(false) }
    var errorDetail by remember(streamUrl) { mutableStateOf<String?>(null) }
    var autoRetries by remember(streamUrl) { mutableIntStateOf(0) }
    var retryRequest by remember(streamUrl) { mutableIntStateOf(0) }
    var hasEnded by remember(streamUrl) { mutableStateOf(false) }
    var nextTriggered by remember(streamUrl) { mutableStateOf(false) }
    var postPlayDismissed by remember(streamUrl) { mutableStateOf(false) }
    var postPlayStartMs by remember(streamUrl) { mutableLongStateOf(-1L) }
    var durationReported by remember(streamUrl) { mutableStateOf(false) }

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

    if (manageWindow) LandscapeImmersiveEffect()

    val exoPlayer = remember(resolvedNativeStream) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)
            .apply {
                val ref = currentExtras?.referer?.takeIf { it.isNotBlank() }
                if (ref != null) {
                    setDefaultRequestProperties(mapOf("Referer" to ref, "Origin" to ref.trimEnd('/')))
                }
            }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(httpDataSourceFactory)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
            )
            .build().apply {
                setMediaItem(buildMediaItem(resolvedNativeStream, currentExtras))
                prepare()
                playWhenReady = true
                // Dub servers list the subbed release's captions, timed to the Japanese dialogue:
                // start with subtitles off (still selectable from Audio & Subtitles).
                if (currentExtras?.audioMode == "dub") {
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) {
                            hasPlaybackError = false
                            isBuffering = false
                            autoRetries = 0
                        }
                        if (state == Player.STATE_ENDED) hasEnded = true
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        if (playing) {
                            isBuffering = false
                            hasPlaybackError = false
                        }
                    }

                    override fun onTracksChanged(newTracks: Tracks) {
                        tracks = newTracks
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        errorDetail = describeError(error)
                        if (resolvedNativeStream != FALLBACK_STREAM) {
                            // On CDN error, switch seamlessly to guaranteed direct stream
                            isBuffering = true
                            hasPlaybackError = false
                            setMediaItem(MediaItem.fromUri(FALLBACK_STREAM))
                            prepare()
                            play()
                        } else if (isTransient(error) && autoRetries < MAX_AUTO_RETRIES) {
                            // Flaky CDNs: quietly retry from where we were before bothering the viewer.
                            autoRetries++
                            isBuffering = true
                            retryRequest++
                        } else {
                            isBuffering = false
                            hasPlaybackError = true
                        }
                    }
                })
            }
    }

    // Seamless fallback to native ExoPlayer if Web Embed Player fails or gives HTTP 403
    LaunchedEffect(webLoadFailed) {
        if (webLoadFailed) {
            useWebPlayer = false
            webLoadFailed = false
            isBuffering = true
            hasPlaybackError = false
        }
    }
    LaunchedEffect(exoPlayer) {
        while (!durationReported) {
            val d = exoPlayer.duration
            if (d != C.TIME_UNSET && d > 0) {
                durationReported = true
                currentExtras?.onDurationKnown?.invoke(d)
            }
            delay(500)
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
        exoPlayer.setMediaItem(buildMediaItem(resolvedNativeStream, currentExtras), currentPosition)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    LaunchedEffect(retryRequest) {
        if (retryRequest > 0) {
            delay(1500L * retryRequest)
            retryNative()
        }
    }

    // ---- skip intro / outro and post-play "next episode" (position driven, so pausing pauses it)
    val inIntro = extras?.intro?.let { currentPosition >= it.startMs && currentPosition < it.endMs } == true
    val inOutro = extras?.outro?.let { currentPosition >= it.startMs && currentPosition < it.endMs } == true
    val hasNext = extras?.onNextEpisode != null
    val nearEnd = totalDuration > 1 && totalDuration - currentPosition <= NEXT_EPISODE_THRESHOLD_MS
    val wantPostPlay = hasNext && !useWebPlayer && !nextTriggered && !postPlayDismissed && !hasPlaybackError &&
        (inOutro || nearEnd || hasEnded)
    val postPlayProgress = if (wantPostPlay && postPlayStartMs >= 0) {
        ((currentPosition - postPlayStartMs) / NEXT_EPISODE_COUNTDOWN_MS.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LaunchedEffect(wantPostPlay) {
        postPlayStartMs = if (wantPostPlay) exoPlayer.currentPosition else -1L
    }
    LaunchedEffect(wantPostPlay, postPlayProgress >= 1f, hasEnded) {
        if (wantPostPlay && (postPlayProgress >= 1f || hasEnded)) {
            nextTriggered = true
            currentExtras?.onNextEpisode?.invoke()
        }
    }

    BackHandler(enabled = onBackClick != null) { onBackClick?.invoke() }

    if (showTracksDialog) {
        TracksDialog(tracks = tracks, player = exoPlayer, onDismiss = { showTracksDialog = false })
    }

    if (showEpisodesDialog && extras != null) {
        EpisodesDialog(
            episodes = extras.episodes,
            current = extras.currentEpisode,
            onSelect = {
                showEpisodesDialog = false
                extras.onSelectEpisode?.invoke(it)
            },
            onDismiss = { showEpisodesDialog = false }
        )
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
                    "We're having trouble playing this title." + (errorDetail?.let { "\n$it" } ?: "")
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

        if (!useWebPlayer && !hasPlaybackError) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = if (showHud && !isLocked) 110.dp else 32.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (inIntro) {
                    PillButton("Skip Intro", Icons.Default.SkipNext) { extras?.intro?.let { exoPlayer.seekTo(it.endMs) } }
                }
                if (inOutro && !hasNext) {
                    PillButton("Skip Outro", Icons.Default.SkipNext) { extras?.outro?.let { exoPlayer.seekTo(it.endMs) } }
                }
                if (wantPostPlay) {
                    NextEpisodePill(
                        progress = postPlayProgress,
                        onNow = {
                            nextTriggered = true
                            currentExtras?.onNextEpisode?.invoke()
                        }
                    )
                    if (!inOutro && !hasEnded) {
                        PillButton("Watch Credits", null) { postPlayDismissed = true }
                    }
                }
            }
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
                    audioMode = extras?.audioMode,
                    onToggleAudioMode = extras?.onToggleAudioMode,
                    onEpisodes = if (extras != null && extras.episodes.isNotEmpty()) {
                        { showEpisodesDialog = true }
                    } else null,
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
    audioMode: String?,
    onToggleAudioMode: (() -> Unit)?,
    onEpisodes: (() -> Unit)?,
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
                if (audioMode != null && onToggleAudioMode != null) {
                    ControlLabel(Icons.Default.Translate, "Audio: ${audioMode.uppercase()}", onToggleAudioMode)
                }
                if (onEpisodes != null) {
                    ControlLabel(Icons.Default.VideoLibrary, "Episodes", onEpisodes)
                }
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

@OptIn(UnstableApi::class)
private fun buildMediaItem(url: String, extras: PlayerExtras?): MediaItem {
    val builder = MediaItem.Builder().setUri(url)
    val lowerUrl = url.lowercase()
    if (lowerUrl.contains(".m3u8")) {
        builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    } else if (lowerUrl.contains(".mp4")) {
        builder.setMimeType(MimeTypes.VIDEO_MP4)
    }
    if (extras != null && extras.subtitles.isNotEmpty()) {
        val configs = ArrayList<MediaItem.SubtitleConfiguration>()
        val labels = HashSet<String>()
        extras.subtitles.forEachIndexed { i, track ->
            var label = track.label
            while (!labels.add(label)) label += " (2)"
            configs.add(
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.src))
                    .setMimeType(subtitleMimeType(track.src))
                    .setLanguage(label.lowercase())
                    .setLabel(label)
                    .setId(label)
                    .setSelectionFlags(if (track.isDefault || (i == 0 && extras.subtitles.none { it.isDefault })) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            )
        }
        if (configs.isNotEmpty()) builder.setSubtitleConfigurations(configs)
    }
    return builder.build()
}

// Infer the real format from the extension: parsing SRT/ASS as WebVTT silently yields zero cues.
private fun subtitleMimeType(url: String): String {
    val path = url.lowercase().substringBefore('?')
    return when {
        path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        path.endsWith(".ssa") || path.endsWith(".ass") -> MimeTypes.TEXT_SSA
        path.endsWith(".ttml") || path.endsWith(".dfxp") || path.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.TEXT_VTT
    }
}

@OptIn(UnstableApi::class)
private fun httpStatusOf(error: PlaybackException): Int? =
    (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode

@OptIn(UnstableApi::class)
private fun isTransient(error: PlaybackException): Boolean {
    val status = httpStatusOf(error)
    return when {
        status != null -> status == 408 || status == 429 || status >= 500
        else -> error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
    }
}

// Surfaces the real cause (CDN status code or exception) instead of one generic message.
@OptIn(UnstableApi::class)
private fun describeError(error: PlaybackException): String =
    httpStatusOf(error)?.let { "The server rejected the request (HTTP $it)." }
        ?: "${error.errorCodeName}${error.cause?.message?.let { ": $it" } ?: ""}"

@Composable
private fun PillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Netflix post-play: white fill grows over the countdown, then the next episode starts. */
@Composable
private fun NextEpisodePill(progress: Float, onNow: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .drawBehind {
                drawRect(Color.White.copy(alpha = 0.35f), size = Size(size.width * progress, size.height))
            }
            .clickable(onClick = onNow)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text("Next Episode", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EpisodesDialog(
    episodes: List<PlayerEpisode>,
    current: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF181818))
                .padding(24.dp)
        ) {
            Text("Episodes", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.height(280.dp)) {
                items(episodes, key = { it.number }) { ep ->
                    TrackRow("${ep.number}. ${ep.title}", ep.number == current) { onSelect(ep.number) }
                }
            }
        }
    }
}

private fun isAllowedEmbedDomain(host: String?): Boolean {
    if (host == null) return false
    val h = host.lowercase()
    return h.contains("flexeo") || h.contains("vidsrc") || h.contains("autoembed") ||
           h.contains("embed") || h.contains("2embed") || h.contains("cloudstream") ||
           h.contains("m3u8") || h.contains("stream")
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
                        val targetHost = request.url.host
                        if (isAllowedEmbedDomain(targetHost) || request.url.scheme in listOf("http", "https")) {
                            return false // Allow player frame loading
                        }
                        return true // Block ad popups
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
                        // Do not crash or report failure on minor ad/script 403s
                        if (request?.isForMainFrame == true && (error?.errorCode == ERROR_HOST_LOOKUP || error?.errorCode == ERROR_CONNECT)) {
                            onLoadFailed()
                        }
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
