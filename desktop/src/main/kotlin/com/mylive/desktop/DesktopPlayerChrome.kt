package com.mylive.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

internal const val PLAYER_CHROME_MOVE_THROTTLE_MS = 250L
internal const val PLAYER_CHROME_MIN_MOVE_DISTANCE_SQ = 4f

private val TopTitleScrimBrush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = .65f), Color.Transparent))

internal fun shouldWakePlayerChromeOnPointerEvent(
    type: PointerEventType,
    position: Offset?,
    previous: Offset?,
    visible: Boolean,
    elapsedSinceLastWakeMillis: Long
): Boolean = when (type) {
    PointerEventType.Press, PointerEventType.Enter -> true
    PointerEventType.Move -> {
        if (position == null || position == previous) false
        else if (!visible) true
        else {
            val movedEnough = previous == null || (position - previous).getDistanceSquared() >= PLAYER_CHROME_MIN_MOVE_DISTANCE_SQ
            movedEnough && elapsedSinceLastWakeMillis >= PLAYER_CHROME_MOVE_THROTTLE_MS
        }
    }
    else -> false
}

/** Pointer observations do not consume input used by buttons, menus or sliders. */
@Composable
internal fun DesktopPlayerChrome(playing: Boolean, busy: Boolean, immersive: Boolean, title: String,
    onFullscreen: () -> Unit, modifier: Modifier = Modifier,
    controls: @Composable (Modifier, (Boolean) -> Unit) -> Unit) {
    val activity = remember { MutableStateFlow(0L) }
    var visible by remember { mutableStateOf(true) }
    var menuHeld by remember { mutableStateOf(false) }
    var hoveringControls by remember { mutableStateOf(false) }
    val wakeTracker = remember { object { var lastWakeMillis = 0L } }
    val latestFullscreen by rememberUpdatedState(onFullscreen)
    val focus = remember { FocusRequester() }
    fun nowMillis(): Long = System.nanoTime() / 1_000_000L
    fun signal(timestampMillis: Long = nowMillis()) {
        wakeTracker.lastWakeMillis = timestampMillis
        if (!visible) visible = true
        activity.value++
    }
    LaunchedEffect(playing, busy, immersive, menuHeld, hoveringControls) {
        if (!playing || busy || menuHeld || hoveringControls) visible = true
        else activity.collectLatest { visible = true; delay(3_000); visible = false }
    }
    Box(modifier.testTag("player-interaction").onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) signal(); false }
        .focusRequester(focus).focusable()
        .pointerInput(Unit) {
            awaitPointerEventScope {
                var previous: Offset? = null
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val position = event.changes.firstOrNull()?.position
                    val now = nowMillis()
                    val elapsed = (now - wakeTracker.lastWakeMillis).coerceAtLeast(0L)
                    if (shouldWakePlayerChromeOnPointerEvent(event.type, position, previous, visible, elapsed)) {
                        signal(now)
                        previous = position
                    } else if (event.type != PointerEventType.Move || previous == null) {
                        previous = position
                    }
                }
            }
        }.pointerInput(Unit) {
            detectTapGestures(onTap = { signal(); focus.requestFocus() }, onDoubleTap = { signal(); latestFullscreen() })
        }) {
        AnimatedVisibility(visible, Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(140)), exit = fadeOut(tween(140))) {
            Box(Modifier.fillMaxWidth().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        when (awaitPointerEvent(PointerEventPass.Initial).type) {
                            PointerEventType.Enter -> if (!hoveringControls) hoveringControls = true
                            PointerEventType.Exit -> if (hoveringControls) hoveringControls = false
                        }
                    }
                }
            }) { controls(Modifier.fillMaxWidth()) { menuHeld = it } }
        }
        if (immersive) AnimatedVisibility(visible, Modifier.align(Alignment.TopStart), enter = fadeIn(), exit = fadeOut()) {
            Text(title, color = Color.White, modifier = Modifier.fillMaxWidth()
                .background(TopTitleScrimBrush)
                .padding(18.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
