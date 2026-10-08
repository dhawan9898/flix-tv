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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import com.example.flixtv.domain.models.StreamSource

private val NetflixRed = Color(0xFFE50914)

/**
 * Plays a movie/TV episode whose stream is resolved on demand (Flexeo): shows progress while
 * resolving, then hands the source to [VideoPlayerScreen] with the referer its CDN expects.
 */
@Composable
fun ResolvedPlayerScreen(
    title: String,
    resolveSource: suspend () -> Result<StreamSource>,
    onBackClick: () -> Unit
) {
    var source by remember { mutableStateOf<StreamSource?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        failure = null
        source = null
        val result = try {
            resolveSource()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result.fold(
            onSuccess = { source = it },
            onFailure = { failure = it.message?.takeIf { m -> m.isNotBlank() } ?: "No playable stream was found. Please try again." }
        )
    }

    val resolved = source
    if (resolved != null) {
        VideoPlayerScreen(
            streamUrl = resolved.streamUrl,
            title = title,
            onBackClick = onBackClick,
            extras = PlayerExtras(referer = resolved.referer)
        )
        return
    }

    LandscapeImmersiveEffect()
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            val error = failure
            if (error == null) {
                CircularProgressIndicator(color = NetflixRed, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
                Text("Finding the best server for $title...", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            } else {
                Text("Can't play this title", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(error, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { reloadKey++ },
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("Retry", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = onBackClick) { Text("Exit", color = Color.White.copy(alpha = 0.7f)) }
                }
            }
        }
    }
}
