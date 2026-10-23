package com.mylive.app.ui.screen.room

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mylive.app.R
import com.mylive.app.ui.layout.AppLayoutInfo

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
    player: @Composable ColumnScope.(Dp) -> Unit,
    chat: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier) {
        val playerMaxHeight = when {
            splitPane -> maxHeight
            limitStackedPlayerHeight -> maxHeight * 0.52f
            else -> Dp.Infinity
        }
        if (splitPane) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("tablet_live_player"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        player(playerMaxHeight)
                    }
                }
                Surface(
                    modifier = Modifier.width(chatWidthDp.dp).fillMaxHeight().testTag("tablet_live_chat"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Text(
                            stringResource(R.string.tablet_live_chat),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                        HorizontalDivider()
                        chat(Modifier.weight(1f))
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                player(playerMaxHeight)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                chat(Modifier.weight(1f))
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
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth().testTag("live_room_player")) {
        val playerHeight = if (fillAvailableHeight && maxHeight != Dp.Infinity) {
            maxHeight
        } else {
            (maxWidth / (16f / 9f)).coerceAtMost(maximumHeight)
        }
        Box(Modifier.fillMaxWidth().height(playerHeight).background(Color.Black)) {
            content()
        }
    }
}
