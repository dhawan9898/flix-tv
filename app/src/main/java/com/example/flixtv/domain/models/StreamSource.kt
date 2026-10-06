package com.example.flixtv.domain.models

data class SubtitleTrack(
    val src: String,
    val label: String,
    val isDefault: Boolean = false
)

/** Opening/ending interval in milliseconds. */
data class SkipRange(val startMs: Long, val endMs: Long)

/** A resolved, directly playable stream plus everything the player needs to open it. */
data class StreamSource(
    val streamUrl: String,
    val referer: String,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val intro: SkipRange? = null,
    val outro: SkipRange? = null,
    /** "sub" or "dub": the audio type the serving server was tagged with. */
    val mode: String = "sub"
)
