package com.mylive.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

/** Reading mpv properties is only needed while the user observes metrics or explicitly enables logging. */
@Composable
internal fun rememberDesktopPlaybackDiagnostics(enabled: Boolean, read: () -> PlaybackDiagnostics): State<PlaybackDiagnostics> {
    val latestRead by rememberUpdatedState(read)
    return produceState(PlaybackDiagnostics(), enabled) {
        if (!enabled) return@produceState
        while (true) {
            value = latestRead()
            delay(1_000)
        }
    }
}
