package com.mylive.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Tab content scrolls inside a frame sized from the owner layout, not from the selected page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopDialog(
    title: String,
    onDismissRequest: () -> Unit,
    preferredWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    BoxWithConstraints {
        val dialogWidth = minOf(preferredWidth, maxWidth - 32.dp)
        val dialogHeight = minOf(660.dp, maxHeight - 32.dp)
        BasicAlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier.size(dialogWidth, dialogHeight),
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(20.dp),
                color = UiSurface,
                contentColor = UiInk,
                border = BorderStroke(1.dp, UiHairline)
            ) {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { content() }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = UiHairline)
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
                unhoverColor = UiMuted.copy(alpha = .22f),
                hoverColor = UiMuted.copy(alpha = .6f)
            )
        )
    }
}
