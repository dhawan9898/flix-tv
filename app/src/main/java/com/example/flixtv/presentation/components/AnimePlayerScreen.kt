package com.example.flixtv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flixtv.data.remote.AniSkipClient
import com.example.flixtv.domain.models.EpisodeItem
import com.example.flixtv.domain.models.MediaItem
import com.example.flixtv.domain.models.SkipRange
import com.example.flixtv.domain.models.StreamSource
import kotlinx.coroutines.launch

private val NetflixRed = Color(0xFFE50914)

/**
 * Plays anime episodes: resolves the real stream for the chosen episode and audio (sub/dub),
 * hands it to [VideoPlayerScreen] with referer, subtitles and skip markers, and wires up
 * episode navigation (next episode, episode picker, sub/dub switch).
 */
@Composable
fun AnimePlayerScreen(
    item: MediaItem,
    episodes: List<EpisodeItem>,
    startEpisode: Int,
    resolveSource: suspend (episode: Int, mode: String) -> Result<StreamSource>,
    lookupSkipIntervals: suspend (title: String, episode: Int, lengthSeconds: Long) -> AniSkipClient.Intervals,
    onBackClick: () -> Unit
) {
    var episodeNumber by remember { mutableIntStateOf(startEpisode) }
    var mode by remember { mutableStateOf("sub") }
    var reloadKey by remember { mutableIntStateOf(0) }
    var source by remember { mutableStateOf<StreamSource?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    // Skip markers found via AniSkip, for streams whose own sources carried none.
    var fallbackIntro by remember { mutableStateOf<SkipRange?>(null) }
    var fallbackOutro by remember { mutableStateOf<SkipRange?>(null) }

    val scope = rememberCoroutineScope()
    LandscapeImmersiveEffect()

    LaunchedEffect(episodeNumber, mode, reloadKey) {
        failure = null
        fallbackIntro = null
        fallbackOutro = null
        resolveSource(episodeNumber, mode).fold(
            onSuccess = { source = it },
            onFailure = { failure = it.message ?: "No server could play this episode" }
        )
    }

    val sorted = remember(episodes) { episodes.sortedBy { it.episodeNumber } }
    val nextEpisode = sorted.firstOrNull { it.episodeNumber > episodeNumber }
    val current = sorted.firstOrNull { it.episodeNumber == episodeNumber }
    val episodeLabel = current?.title?.takeIf { it.isNotBlank() && !it.startsWith("Episode") }
        ?.let { "E$episodeNumber • $it" } ?: "E$episodeNumber"

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        source?.let { src ->
            VideoPlayerScreen(
                streamUrl = src.streamUrl,
                title = "${item.title} - $episodeLabel",
                onBackClick = onBackClick,
                manageWindow = false,
                extras = PlayerExtras(
                    referer = src.referer,
                    subtitles = src.subtitles,
                    intro = src.intro ?: fallbackIntro,
                    outro = src.outro ?: fallbackOutro,
                    audioMode = src.mode,
                    onToggleAudioMode = {
                        source = null
                        mode = if (mode == "sub") "dub" else "sub"
                    },
                    onNextEpisode = nextEpisode?.let { next ->
                        {
                            source = null
                            episodeNumber = next.episodeNumber
                        }
                    },
                    episodes = sorted.map { PlayerEpisode(it.episodeNumber, it.title) },
                    currentEpisode = episodeNumber,
                    onSelectEpisode = { picked ->
                        if (picked != episodeNumber) {
                            source = null
                            episodeNumber = picked
                        }
                    },
                    onDurationKnown = { durationMs ->
                        if (src.intro == null || src.outro == null) {
                            val forEpisode = episodeNumber
                            scope.launch {
                                val found = lookupSkipIntervals(item.title, forEpisode, durationMs / 1000)
                                if (forEpisode == episodeNumber) {
                                    fallbackIntro = found.opening
                                    fallbackOutro = found.ending
                                }
                            }
                        }
                    }
                )
            )
        }

        val error = failure
        when {
            error != null -> ResolveFailure(
                message = error,
                onRetry = { reloadKey++ },
                onExit = onBackClick
            )
            source == null -> Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = NetflixRed, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
                    Text(
                        "Finding the best ${mode.uppercase()} server for $episodeLabel...",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResolveFailure(message: String, onRetry: () -> Unit, onExit: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("Can't play this episode", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(message, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("Retry", fontWeight = FontWeight.Bold) }
                TextButton(onClick = onExit) { Text("Exit", color = Color.White.copy(alpha = 0.7f)) }
            }
        }
    }
}
