package com.mylive.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.R
import com.mylive.app.ui.component.settings.SettingsHairlineDivider
import com.mylive.app.ui.component.settings.SettingsInsetCard
import com.mylive.app.ui.component.settings.SettingsMenu
import com.mylive.app.ui.component.settings.SettingsSectionHeader
import com.mylive.app.ui.component.settings.SettingsSelectionDialog
import com.mylive.app.ui.component.settings.SettingsSwitch
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.theme.Icons

private val exitDurationValues = listOf(15, 30, 45, 60, 90, 120, 180)
private val roomExitDurationValues = listOf(0, 5, 10, 15, 30, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoExitSettingsScreen(
    navigator: Navigator,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val autoExitEnable by viewModel.autoExitEnable.collectAsStateWithLifecycle()
    val autoExitDuration by viewModel.autoExitDuration.collectAsStateWithLifecycle()
    val roomAutoExitDuration by viewModel.roomAutoExitDuration.collectAsStateWithLifecycle()

    val exitDurationOptions = stringArrayResource(R.array.auto_exit_duration_options)
    val roomExitDurationOptions = stringArrayResource(R.array.auto_exit_room_duration_options)
    val exitDurationDefault = stringResource(R.string.auto_exit_duration_default)
    val roomExitDurationDefault = stringResource(R.string.auto_exit_room_duration_default)

    var showExitDurationDialog by remember { mutableStateOf(false) }
    var showRoomExitDurationDialog by remember { mutableStateOf(false) }
    val handleBack = navigator.rememberRouteActions().onBack

    Scaffold(
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_auto_exit)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        AdaptivePageFrame(Modifier.padding(padding), LocalAppLayout.current.settingsMaxWidthDp) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (embedded) Color.Transparent else MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SettingsSectionHeader("自动关闭策略")
                SettingsInsetCard(embedded = embedded) {
                    SettingsSwitch(
                        title = stringResource(R.string.auto_exit_switch_title),
                        subtitle = stringResource(R.string.auto_exit_switch_subtitle),
                        checked = autoExitEnable,
                        onCheckedChange = { viewModel.setAutoExitEnable(it) }
                    )
                    SettingsHairlineDivider()
                    SettingsMenu(
                        title = stringResource(R.string.auto_exit_duration_title),
                        subtitle = stringResource(R.string.auto_exit_duration_subtitle),
                        value = exitDurationOptions.getOrElse(
                            exitDurationValues.indexOf(autoExitDuration)
                        ) { exitDurationDefault },
                        onClick = { showExitDurationDialog = true }
                    )
                    SettingsHairlineDivider()
                    SettingsMenu(
                        title = stringResource(R.string.auto_exit_room_duration_title),
                        subtitle = stringResource(R.string.auto_exit_room_duration_subtitle),
                        value = roomExitDurationOptions.getOrElse(
                            roomExitDurationValues.indexOf(roomAutoExitDuration)
                        ) { roomExitDurationDefault },
                        onClick = { showRoomExitDurationDialog = true }
                    )
                }
            }
        }
    }

    if (showExitDurationDialog) {
        SettingsSelectionDialog(
            title = stringResource(R.string.auto_exit_duration_title),
            options = exitDurationOptions,
            values = exitDurationValues,
            selectedValue = autoExitDuration,
            onSelect = { viewModel.setAutoExitDuration(it) },
            onDismiss = { showExitDurationDialog = false }
        )
    }

    if (showRoomExitDurationDialog) {
        SettingsSelectionDialog(
            title = stringResource(R.string.auto_exit_room_duration_title),
            options = roomExitDurationOptions,
            values = roomExitDurationValues,
            selectedValue = roomAutoExitDuration,
            onSelect = { viewModel.setRoomAutoExitDuration(it) },
            onDismiss = { showRoomExitDurationDialog = false }
        )
    }
}
