package com.mylive.app.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mylive.app.R
import com.mylive.app.core.model.AppLayoutMode
import com.mylive.app.core.model.TabletOrientationMode
import com.mylive.app.ui.component.settings.SettingsMenu

@Composable
internal fun LayoutModeSettings(
    mode: AppLayoutMode,
    orientation: TabletOrientationMode,
    tabletActive: Boolean,
    onModeChange: (AppLayoutMode) -> Unit,
    onOrientationChange: (TabletOrientationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLayoutDialog by remember { mutableStateOf(false) }
    var showOrientationDialog by remember(tabletActive) { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        SettingsMenu(
            title = stringResource(R.string.layout_mode_title),
            subtitle = stringResource(if (tabletActive) R.string.layout_active_tablet else R.string.layout_active_phone),
            value = layoutModeLabel(mode),
            onClick = { showLayoutDialog = true },
            modifier = Modifier.testTag("layout_mode_picker")
        )
        HorizontalDivider()
        if (tabletActive) {
            SettingsMenu(
                title = stringResource(R.string.tablet_rotation_title),
                value = orientationLabel(orientation),
                onClick = { showOrientationDialog = true },
                modifier = Modifier.testTag("tablet_rotation_picker")
            )
            HorizontalDivider()
        }
    }

    if (showLayoutDialog) {
        LayoutSelectionDialog(
            title = stringResource(R.string.layout_mode_title),
            onDismiss = { showLayoutDialog = false }
        ) {
            AppLayoutMode.entries.forEach { choice ->
                LayoutChoice(
                    label = layoutModeLabel(choice),
                    description = stringResource(when (choice) {
                        AppLayoutMode.Auto -> R.string.layout_mode_auto_description
                        AppLayoutMode.Phone -> R.string.layout_mode_phone_description
                        AppLayoutMode.Tablet -> R.string.layout_mode_tablet_description
                    }),
                    selected = mode == choice,
                    tag = "layout_mode_${choice.name.lowercase()}",
                    onClick = {
                        showLayoutDialog = false
                        onModeChange(choice)
                    }
                )
            }
        }
    }

    if (showOrientationDialog && tabletActive) {
        LayoutSelectionDialog(
            title = stringResource(R.string.tablet_rotation_title),
            onDismiss = { showOrientationDialog = false }
        ) {
            listOf(TabletOrientationMode.FollowSystem, TabletOrientationMode.Portrait, TabletOrientationMode.Landscape)
                .forEach { choice ->
                    LayoutChoice(
                        label = orientationLabel(choice),
                        selected = orientation == choice ||
                            (orientation == TabletOrientationMode.Uninitialized && choice == TabletOrientationMode.FollowSystem),
                        tag = "tablet_rotation_${choice.name.lowercase()}",
                        onClick = {
                            showOrientationDialog = false
                            onOrientationChange(choice)
                        }
                    )
                }
            Text(
                stringResource(R.string.tablet_rotation_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun layoutModeLabel(mode: AppLayoutMode): String = stringResource(when (mode) {
    AppLayoutMode.Auto -> R.string.layout_mode_auto
    AppLayoutMode.Phone -> R.string.layout_mode_phone
    AppLayoutMode.Tablet -> R.string.layout_mode_tablet
})

@Composable
private fun orientationLabel(orientation: TabletOrientationMode): String = stringResource(when (orientation) {
    TabletOrientationMode.Portrait -> R.string.tablet_rotation_portrait
    TabletOrientationMode.Landscape -> R.string.tablet_rotation_landscape
    else -> R.string.tablet_rotation_system
})

@Composable
private fun LayoutSelectionDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup()) {
                content()
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
private fun LayoutChoice(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    description: String? = null
) {
    Row(
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(start = 12.dp))
    }
}
