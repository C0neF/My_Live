package com.mylive.app.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.R
import com.mylive.app.core.model.AppLayoutMode
import com.mylive.app.core.model.TabletOrientationMode
import com.mylive.app.ui.layout.LocalAppLayout

@Composable
internal fun AppLayoutSettingsSection(viewModel: SettingsViewModel = hiltViewModel()) {
    val mode by viewModel.appLayoutMode.collectAsStateWithLifecycle()
    val orientation by viewModel.tabletOrientationMode.collectAsStateWithLifecycle()
    LayoutModeSettings(
        mode = mode,
        orientation = orientation,
        tabletActive = LocalAppLayout.current.tablet,
        onModeChange = viewModel::setAppLayoutMode,
        onOrientationChange = viewModel::setTabletOrientationMode
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LayoutModeSettings(
    mode: AppLayoutMode,
    orientation: TabletOrientationMode,
    tabletActive: Boolean,
    onModeChange: (AppLayoutMode) -> Unit,
    onOrientationChange: (TabletOrientationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.layout_mode_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (tabletActive) R.string.layout_active_tablet else R.string.layout_active_phone),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("layout_current_mode")
            )
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLayoutMode.entries.forEach { choice ->
                    LayoutChoice(
                        label = stringResource(when (choice) {
                            AppLayoutMode.Auto -> R.string.layout_mode_auto
                            AppLayoutMode.Phone -> R.string.layout_mode_phone
                            AppLayoutMode.Tablet -> R.string.layout_mode_tablet
                        }),
                        selected = mode == choice,
                        tag = "layout_mode_${choice.name.lowercase()}",
                        onClick = { onModeChange(choice) }
                    )
                }
            }
            Text(
                stringResource(R.string.layout_mode_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (tabletActive) {
                HorizontalDivider()
                Text(stringResource(R.string.tablet_rotation_title), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(TabletOrientationMode.FollowSystem, TabletOrientationMode.Portrait, TabletOrientationMode.Landscape)
                        .forEach { choice ->
                            LayoutChoice(
                                label = stringResource(when (choice) {
                                    TabletOrientationMode.Portrait -> R.string.tablet_rotation_portrait
                                    TabletOrientationMode.Landscape -> R.string.tablet_rotation_landscape
                                    else -> R.string.tablet_rotation_system
                                }),
                                selected = orientation == choice ||
                                    (orientation == TabletOrientationMode.Uninitialized && choice == TabletOrientationMode.FollowSystem),
                                tag = "tablet_rotation_${choice.name.lowercase()}",
                                onClick = { onOrientationChange(choice) }
                            )
                        }
                }
                Text(
                    stringResource(R.string.tablet_rotation_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LayoutChoice(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.testTag(tag).selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}