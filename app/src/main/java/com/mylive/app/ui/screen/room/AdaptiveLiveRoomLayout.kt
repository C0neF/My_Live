package com.mylive.app.ui.screen.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mylive.app.R
import com.mylive.app.ui.layout.AppLayoutInfo
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import com.mylive.app.ui.screen.room.player.isPortraitVideo

internal const val DefaultLandscapePlayerAspectRatio = 16f / 9f

internal enum class LiveRoomLayout { Immersive, Split, Stacked }

internal fun resolveLiveRoomLayout(
    layout: AppLayoutInfo,
    isLandscape: Boolean,
    fullscreen: Boolean
): LiveRoomLayout = when {
    fullscreen -> LiveRoomLayout.Immersive
    layout.tablet -> if (layout.splitLiveRoom) LiveRoomLayout.Split else LiveRoomLayout.Stacked
    isLandscape -> LiveRoomLayout.Immersive
    else -> LiveRoomLayout.Stacked
}

/** Player session ownership stays above this layout, so resizing never restarts the stream. */
@Composable
internal fun AdaptiveLiveRoomLayout(
    splitPane: Boolean,
    chatWidthDp: Int,
    limitStackedPlayerHeight: Boolean,
    modifier: Modifier = Modifier,
    videoAspectRatio: Float? = null,
    playerResizeState: PortraitPlayerResizeState? = null,
    sidePanelCollapsed: Boolean = false,
    sidePanelHeader: (@Composable () -> Unit)? = null,
    player: @Composable ColumnScope.(Dp) -> Unit,
    chat: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier) {
        val expandedPlayerMaxHeight = when {
            splitPane -> maxHeight
            limitStackedPlayerHeight -> maxHeight * 0.52f
            // Leave room for the anchor header and readable chat even on short phone windows.
            isPortraitVideo(videoAspectRatio) ->
                (maxHeight - 240.dp).coerceIn(0.dp, maxHeight * 0.65f)
            else -> Dp.Infinity
        }
        val canResizePlayer = !splitPane && isPortraitVideo(videoAspectRatio) && playerResizeState != null
        var playerMaxHeight = expandedPlayerMaxHeight
        var chatScrollModifier: Modifier = Modifier
        if (canResizePlayer) {
            val resizeState = requireNotNull(playerResizeState)
            val expandedHeight = (maxWidth / requireNotNull(videoAspectRatio)).coerceAtMost(expandedPlayerMaxHeight)
            val minimumHeight = (maxWidth / DefaultLandscapePlayerAspectRatio).coerceAtMost(expandedHeight)
            playerMaxHeight = minimumHeight + (expandedHeight - minimumHeight) * resizeState.expansionFraction
            val density = LocalDensity.current
            val minimumHeightPx = with(density) { minimumHeight.toPx() }
            val expandedHeightPx = with(density) { expandedHeight.toPx() }
            val connection = remember(resizeState, minimumHeightPx, expandedHeightPx) {
                PortraitPlayerResizeConnection(resizeState, minimumHeightPx, expandedHeightPx)
            }
            DisposableEffect(connection) {
                onDispose { resizeState.stopResizing() }
            }
            chatScrollModifier = Modifier.nestedScroll(connection)
        }
        if (splitPane) {
            val motionPolicy = LocalAppMotionPolicy.current
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("tablet_live_player"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(Modifier.fillMaxSize()) {
                        player(playerMaxHeight)
                    }
                }
                AnimatedVisibility(
                    visible = !sidePanelCollapsed,
                    enter = if (motionPolicy.reducedMotion) {
                        fadeIn(motionPolicy.effectsSpec())
                    } else {
                        expandHorizontally(
                            animationSpec = motionPolicy.contentSpec(),
                            expandFrom = Alignment.Start
                        ) + fadeIn(motionPolicy.effectsSpec())
                    },
                    exit = if (motionPolicy.reducedMotion) {
                        fadeOut(motionPolicy.effectsSpec())
                    } else {
                        shrinkHorizontally(
                            animationSpec = motionPolicy.contentSpec(),
                            shrinkTowards = Alignment.Start
                        ) + fadeOut(motionPolicy.effectsSpec())
                    }
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .width(chatWidthDp.dp)
                            .fillMaxHeight()
                            .testTag("tablet_live_chat"),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            if (sidePanelHeader != null) {
                                sidePanelHeader()
                            } else {
                                Text(
                                    stringResource(R.string.tablet_live_chat),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            chat(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                player(playerMaxHeight)
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                chat(Modifier.weight(1f).then(chatScrollModifier))
            }
        }
    }
}

/** A short window must constrain the surface itself, not only its reported layout bounds. */
@Composable
internal fun LiveRoomPlayerArea(
    maximumHeight: Dp,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
    videoAspectRatio: Float? = null,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth().testTag("live_room_player")) {
        val playerHeight = if (fillAvailableHeight && maxHeight != Dp.Infinity) {
            maxHeight
        } else {
            val aspectRatio = videoAspectRatio?.takeIf { it.isFinite() && it > 0f } ?: DefaultLandscapePlayerAspectRatio
            (maxWidth / aspectRatio).coerceAtMost(maximumHeight)
        }
        Box(Modifier.fillMaxWidth().height(playerHeight).background(Color.Black)) {
            content()
        }
    }
}
