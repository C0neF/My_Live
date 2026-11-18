package com.mylive.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.awt.Cursor

private val PeekSlideEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
internal const val PeekEnterDurationMillis = 240
internal const val PeekExitDurationMillis = 200

@Composable
internal fun DesktopWatchLayout(immersive: Boolean, chatPreferred: Boolean, onChatPreference: () -> Unit,
    modifier: Modifier = Modifier, chatAvailable: Boolean = true, fullArea: Boolean = false,
    player: @Composable (chatVisible: Boolean, toggleChat: () -> Unit) -> Unit,
    chat: @Composable (Modifier, close: () -> Unit) -> Unit) {
    BoxWithConstraints(modifier) {
        val inline = maxWidth >= 1000.dp && !immersive && !fullArea && chatAvailable
        var chatWidth by rememberSaveable { mutableFloatStateOf(304f) }
        val maxChatWidth = (maxWidth.value * .4f).coerceAtMost(480f)
        val density = LocalDensity.current.density
        var sheetOpen by remember(inline, immersive, chatAvailable, fullArea) { mutableStateOf(false) }
        val peekMode = fullArea && !immersive && chatAvailable
        var peekOpen by remember(peekMode) { mutableStateOf(false) }
        var dismissedAtEdge by remember(peekMode) { mutableStateOf(false) }
        val peekWidth = minOf(320.dp, maxWidth)
        val sheetFocus = remember { FocusRequester() }
        LaunchedEffect(sheetOpen) { if (sheetOpen) sheetFocus.requestFocus() }
        val visible = chatAvailable && !immersive && when {
            peekMode -> peekOpen
            inline -> chatPreferred
            else -> sheetOpen
        }
        val toggle = if (!chatAvailable || immersive) ({}) else if (peekMode) ({ peekOpen = !peekOpen }) else if (inline) onChatPreference else ({ sheetOpen = !sheetOpen })
        // Observe hover without consuming clicks, text selection or scroll input in the chat.
        val peekPointer = if (peekMode) Modifier.pointerInput(peekWidth) {
            val triggerWidth = 12.dp.toPx()
            val panelWidth = peekWidth.toPx()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    when (event.type) {
                        PointerEventType.Exit -> {
                            peekOpen = false
                            dismissedAtEdge = false
                        }
                        PointerEventType.Enter, PointerEventType.Move -> {
                            val position = event.changes.first().position
                            val inBounds = position.x >= 0 && position.x < size.width &&
                                position.y >= 0 && position.y < size.height
                            val inTriggerZone = inBounds && position.x >= size.width - triggerWidth
                            if (!inTriggerZone) {
                                dismissedAtEdge = false
                            }
                            if (!dismissedAtEdge) {
                                val activeWidth = if (peekOpen) panelWidth else triggerWidth
                                peekOpen = inBounds && position.x >= size.width - activeWidth
                            }
                        }
                    }
                }
            }
        } else Modifier
        Box(Modifier.fillMaxSize().clipToBounds().onPreviewKeyEvent { event ->
            if (sheetOpen && event.type == KeyEventType.KeyDown && event.key == Key.Escape) { sheetOpen = false; true } else false
        }.then(peekPointer)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().testTag("watch-video")) { player(visible, toggle) }
            if (inline) {
                AnimatedVisibility(
                    visible = visible,
                    enter = expandHorizontally(
                        animationSpec = tween(durationMillis = 220, easing = PeekSlideEasing),
                        expandFrom = Alignment.Start
                    ) + fadeIn(animationSpec = tween(durationMillis = 170, easing = LinearOutSlowInEasing)),
                    exit = shrinkHorizontally(
                        animationSpec = tween(durationMillis = 180, easing = PeekSlideEasing),
                        shrinkTowards = Alignment.Start
                    ) + fadeOut(animationSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing))
                ) {
                    Row(Modifier.fillMaxHeight()) {
                        val interaction = remember { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()
                        val dragging by interaction.collectIsDraggedAsState()
                        val grabberColor by animateColorAsState(
                            targetValue = if (hovered || dragging) UiAccent else UiInk.copy(alpha = .16f),
                            animationSpec = tween(durationMillis = 150, easing = PeekSlideEasing)
                        )
                        Box(Modifier.width(16.dp).fillMaxHeight().testTag("chat-divider")
                            .semantics { contentDescription = "调整聊天栏宽度，双击重置" }
                            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                            .hoverable(interaction)
                            .draggable(rememberDraggableState { delta -> chatWidth = (chatWidth - delta / density).coerceIn(248f, maxChatWidth) },
                                Orientation.Horizontal, interactionSource = interaction)
                            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { chatWidth = 304f }) }
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.DirectionLeft, Key.DirectionRight)) {
                                    chatWidth = (chatWidth + if (event.key == Key.DirectionLeft) 16f else -16f).coerceIn(248f, maxChatWidth)
                                    true
                                } else false
                            }.focusable()) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .width(3.dp)
                                    .height(36.dp)
                                    .background(grabberColor, CircleShape)
                            )
                        }
                        chat(Modifier.width(chatWidth.coerceIn(248f, maxChatWidth).dp).fillMaxHeight().testTag("watch-chat"), onChatPreference)
                    }
                }
            }
        }
        if (peekMode) {
            AnimatedVisibility(
                visible = !peekOpen,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = PeekExitDurationMillis, easing = PeekSlideEasing),
                    initialOffsetX = { fullWidth -> fullWidth }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)
                ),
                exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = 140, easing = PeekSlideEasing),
                    targetOffsetX = { fullWidth -> fullWidth }
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 120, easing = FastOutLinearInEasing)
                )
            ) {
                Box(
                    Modifier
                        .width(12.dp)
                        .height(48.dp)
                        .background(Color.Black.copy(alpha = .46f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                ) {
                    Icon(Icons.Default.ChevronLeft, "移到右侧查看实时聊天", Modifier.align(Alignment.Center).size(12.dp), tint = Color.White.copy(alpha = .75f))
                }
            }
            AnimatedVisibility(
                visible = peekOpen,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = PeekEnterDurationMillis, easing = PeekSlideEasing),
                    initialOffsetX = { fullWidth -> fullWidth }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing)
                ),
                exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = PeekExitDurationMillis, easing = PeekSlideEasing),
                    targetOffsetX = { fullWidth -> fullWidth }
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing)
                )
            ) {
                Box(
                    Modifier
                        .width(peekWidth)
                        .fillMaxHeight()
                        .testTag("watch-chat-peek")
                        .padding(top = 8.dp, bottom = 8.dp, end = 8.dp)
                ) {
                    chat(Modifier.fillMaxSize()) {
                        peekOpen = false
                        dismissedAtEdge = true
                    }
                }
            }
        }
        if (!peekMode && !inline) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing))
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)).clickable(onClickLabel = "关闭聊天", onClick = { sheetOpen = false }))
            }
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = PeekEnterDurationMillis, easing = PeekSlideEasing),
                    initialOffsetX = { fullWidth -> fullWidth }
                ) + fadeIn(animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing)),
                exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = PeekExitDurationMillis, easing = PeekSlideEasing),
                    targetOffsetX = { fullWidth -> fullWidth }
                ) + fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing))
            ) {
                Box(Modifier.width(320.dp).fillMaxHeight().testTag("watch-chat-sheet")
                    .focusRequester(sheetFocus).focusable()) {
                    chat(Modifier.fillMaxSize()) { sheetOpen = false }
                }
            }
        }
        }
    }
}
