package com.mylive.app.ui.screen.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A portrait viewport overlays chat instead of squeezing the video beside a landscape panel. */
@Composable
internal fun ImmersiveLiveRoomLayout(
    panelFraction: Float,
    onDismissPanel: () -> Unit,
    modifier: Modifier = Modifier,
    player: @Composable (Modifier) -> Unit,
    panel: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val panelWidth = minOf(320.dp, (maxWidth - 48.dp).coerceAtLeast(0.dp))
        val visibleWidth = panelWidth * panelFraction.coerceIn(0f, 1f)
        val panelContent: @Composable () -> Unit = {
            if (visibleWidth > 0.dp) {
                Box(Modifier.width(visibleWidth).fillMaxHeight().clipToBounds().testTag("immersive_chat_panel")) {
                    panel(Modifier.requiredWidth(panelWidth).fillMaxHeight())
                }
            }
        }
        if (maxWidth < maxHeight) {
            Box(Modifier.fillMaxSize()) {
                player(Modifier.fillMaxSize())
                if (visibleWidth > 0.dp) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
                            .clickable(onClick = onDismissPanel)
                            .semantics { contentDescription = "关闭实时聊天" }
                            .testTag("immersive_chat_scrim")
                    )
                    Box(Modifier.align(Alignment.CenterEnd)) { panelContent() }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                player(Modifier.weight(1f).fillMaxHeight())
                panelContent()
            }
        }
    }
}
