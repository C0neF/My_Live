package com.mylive.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.R
import com.mylive.app.ui.component.settings.SettingsMenu
import com.mylive.app.ui.component.settings.SettingsSwitch
import com.mylive.app.ui.navigation.Navigator

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable

private val exitDurationValues = listOf(15, 30, 45, 60, 90, 120, 180)
private val roomExitDurationValues = listOf(0, 5, 10, 15, 30, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoExitSettingsScreen(
    navigator: Navigator,
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
    var isExiting by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (!isExiting) {
            isExiting = true
            navigator.goBack()
        }
    }

    BackHandler(enabled = !isExiting) {
        handleBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_auto_exit)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSwitch(
                title = stringResource(R.string.auto_exit_switch_title),
                subtitle = stringResource(R.string.auto_exit_switch_subtitle),
                checked = autoExitEnable,
                onCheckedChange = { viewModel.setAutoExitEnable(it) }
            )
            HorizontalDivider()

            SettingsMenu(
                title = stringResource(R.string.auto_exit_duration_title),
                subtitle = stringResource(R.string.auto_exit_duration_subtitle),
                value = exitDurationOptions.getOrElse(
                    exitDurationValues.indexOf(autoExitDuration)
                ) { exitDurationDefault },
                onClick = { showExitDurationDialog = true }
            )
            HorizontalDivider()

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

    if (showExitDurationDialog) {
        SelectionDialog(
            title = stringResource(R.string.auto_exit_duration_title),
            options = exitDurationOptions,
            values = exitDurationValues,
            selectedValue = autoExitDuration,
            onSelect = { viewModel.setAutoExitDuration(it) },
            onDismiss = { showExitDurationDialog = false }
        )
    }

    if (showRoomExitDurationDialog) {
        SelectionDialog(
            title = stringResource(R.string.auto_exit_room_duration_title),
            options = roomExitDurationOptions,
            values = roomExitDurationValues,
            selectedValue = roomAutoExitDuration,
            onSelect = { viewModel.setRoomAutoExitDuration(it) },
            onDismiss = { showRoomExitDurationDialog = false }
        )
    }
}

@Composable
private fun <T> SelectionDialog(
    title: String,
    options: Array<String>,
    values: List<T>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                values.forEachIndexed { index, value ->
                    val isSelected = value == selectedValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = options.getOrElse(index) { value.toString() },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
