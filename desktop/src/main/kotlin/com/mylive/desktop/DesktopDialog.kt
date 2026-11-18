package com.mylive.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

private val DesktopDialogShape = RoundedCornerShape(18.dp)

@Composable
private fun Modifier.desktopModalEntrance(): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 190, easing = DesktopStandardEasing)
        )
    }
    return this.graphicsLayer {
        val t = progress.value
        alpha = t
        val scale = 0.965f + 0.035f * t
        scaleX = scale
        scaleY = scale
    }
}

/** Tab content scrolls inside a frame sized from the owner layout, not from the selected page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopDialog(
    title: String,
    onDismissRequest: () -> Unit,
    preferredWidth: Dp,
    modifier: Modifier = Modifier,
    preferredHeight: Dp = 650.dp,
    content: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < .5f
    val borderColor = if (isDark) Color.White.copy(alpha = .10f) else UiHairline
    BoxWithConstraints {
        val dialogWidth = minOf(preferredWidth, maxWidth - 32.dp)
        val dialogHeight = minOf(preferredHeight, maxHeight - 32.dp)
        BasicAlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier.size(dialogWidth, dialogHeight),
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .desktopModalEntrance()
                    .shadow(24.dp, DesktopDialogShape, spotColor = Color.Black.copy(alpha = .32f)),
                shape = DesktopDialogShape,
                color = UiSurface,
                contentColor = UiInk,
                border = BorderStroke(DesktopHairlineWidth, borderColor)
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(14.dp))
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { content() }
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(thickness = DesktopHairlineWidth, color = UiHairline)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/** Compact wrap-content macOS Sequoia sheet modal for prompts, forms, and quick actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopSheetModal(
    title: String,
    onDismissRequest: () -> Unit,
    preferredWidth: Dp = 460.dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < .5f
    val borderColor = if (isDark) Color.White.copy(alpha = .10f) else UiHairline
    BoxWithConstraints {
        val dialogWidth = minOf(preferredWidth, maxWidth - 32.dp)
        val maxSheetHeight = maxHeight - 40.dp
        BasicAlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier.width(dialogWidth).heightIn(max = maxSheetHeight),
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .desktopModalEntrance()
                    .shadow(22.dp, DesktopDialogShape, spotColor = Color.Black.copy(alpha = .30f)),
                shape = DesktopDialogShape,
                color = UiSurface,
                contentColor = UiInk,
                border = BorderStroke(DesktopHairlineWidth, borderColor)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { content() }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(thickness = DesktopHairlineWidth, color = UiHairline)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesktopQrCard(
    text: String,
    description: String,
    size: Dp = 188.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(DesktopHairlineWidth, Color.Black.copy(alpha = .08f))
    ) {
        Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
            DesktopQrImage(text, description, Modifier.size(size))
        }
    }
}

@Composable
internal fun DesktopDialogScroll(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = 12.dp, bottom = 4.dp),
            content = content
        )
        if (scroll.maxValue > 0) VerticalScrollbar(
            rememberScrollbarAdapter(scroll),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = defaultScrollbarStyle().copy(
                thickness = 6.dp,
                unhoverColor = UiMuted.copy(alpha = .20f),
                hoverColor = UiMuted.copy(alpha = .55f)
            )
        )
    }
}
