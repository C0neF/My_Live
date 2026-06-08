package com.mylive.app.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.R
import com.mylive.app.ui.component.settings.SettingsMenu
import com.mylive.app.ui.component.settings.SettingsSwitch
import com.mylive.app.ui.navigation.Navigator

private val qualityValues = listOf(0, 1, 2, 3, 4, 5)
private val scaleModeValues = listOf(0, 1, 2, 3, 4)
private val hwDecodeValues = listOf(true, false)

enum class PlaySettingDialog {
    QUALITY, SCALE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaySettingsScreen(
    navigator: Navigator,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val qualityLevel by viewModel.qualityLevel.collectAsStateWithLifecycle()
    val scaleMode by viewModel.scaleMode.collectAsStateWithLifecycle()
    val hardwareDecode by viewModel.hardwareDecode.collectAsStateWithLifecycle()
    val playerCompatMode by viewModel.playerCompatMode.collectAsStateWithLifecycle()
    val playerAutoPause by viewModel.playerAutoPause.collectAsStateWithLifecycle()
    val allowBackgroundPlayback by viewModel.allowBackgroundPlayback.collectAsStateWithLifecycle()
    val playerForceHttps by viewModel.playerForceHttps.collectAsStateWithLifecycle()

    val qualityOptions = stringArrayResource(R.array.play_quality_options)
    val scaleModeOptions = stringArrayResource(R.array.play_scale_options)
    val hwDecodeOptions = stringArrayResource(R.array.play_hw_decode_options)

    val qualityDefault = stringResource(R.string.play_quality_default)
    val scaleDefault = stringResource(R.string.play_scale_default)

    var activeDialog by remember { mutableStateOf<PlaySettingDialog?>(null) }
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
                title = { Text(stringResource(R.string.settings_play)) },
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
            SettingsMenu(
                title = stringResource(R.string.play_quality_title),
                subtitle = stringResource(R.string.play_quality_subtitle),
                value = qualityOptions.getOrElse(qualityValues.indexOf(qualityLevel)) { qualityDefault },
                onClick = { activeDialog = PlaySettingDialog.QUALITY }
            )
            HorizontalDivider()

            SettingsMenu(
                title = stringResource(R.string.play_scale_title),
                subtitle = stringResource(R.string.play_scale_subtitle),
                value = scaleModeOptions.getOrElse(scaleModeValues.indexOf(scaleMode)) { scaleDefault },
                onClick = { activeDialog = PlaySettingDialog.SCALE }
            )
            HorizontalDivider()

            SettingsMenu(
                title = stringResource(R.string.play_hw_decode_title),
                subtitle = stringResource(R.string.play_hw_decode_subtitle),
                value = hwDecodeOptions.getOrElse(hwDecodeValues.indexOf(hardwareDecode)) { hwDecodeOptions[1] },
                onClick = { viewModel.setHardwareDecode(!hardwareDecode) }
            )
            HorizontalDivider()

            SettingsSwitch(
                title = stringResource(R.string.play_compat_title),
                subtitle = stringResource(R.string.play_compat_subtitle),
                checked = playerCompatMode,
                onCheckedChange = { viewModel.setPlayerCompatMode(it) }
            )
            HorizontalDivider()

            SettingsSwitch(
                title = stringResource(R.string.play_auto_pause_title),
                subtitle = stringResource(R.string.play_auto_pause_subtitle),
                checked = playerAutoPause,
                onCheckedChange = { viewModel.setPlayerAutoPause(it) }
            )
            HorizontalDivider()

            SettingsSwitch(
                title = stringResource(R.string.play_bg_title),
                subtitle = stringResource(R.string.play_bg_subtitle),
                checked = allowBackgroundPlayback,
                onCheckedChange = { viewModel.setAllowBackgroundPlayback(it) }
            )
            HorizontalDivider()

            SettingsSwitch(
                title = stringResource(R.string.play_force_https_title),
                subtitle = stringResource(R.string.play_force_https_subtitle),
                checked = playerForceHttps,
                onCheckedChange = { viewModel.setPlayerForceHttps(it) }
            )
        }
    }

    when (activeDialog) {
        PlaySettingDialog.QUALITY -> {
            SelectionDialog(
                title = stringResource(R.string.play_quality_title),
                options = qualityOptions,
                values = qualityValues,
                selectedValue = qualityLevel,
                onSelect = { viewModel.setQualityLevel(it) },
                onDismiss = { activeDialog = null }
            )
        }
        PlaySettingDialog.SCALE -> {
            SelectionDialog(
                title = stringResource(R.string.play_scale_title),
                options = scaleModeOptions,
                values = scaleModeValues,
                selectedValue = scaleMode,
                onSelect = { viewModel.setScaleMode(it) },
                onDismiss = { activeDialog = null }
            )
        }
        null -> {}
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

