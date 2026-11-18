package com.mylive.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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

private val qualityValues = listOf(0, 1, 2, 3, 4, 5)
private val scaleModeValues = listOf(0, 1, 2, 3, 4)

enum class PlaySettingDialog {
    QUALITY, CELLULAR_QUALITY, SCALE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaySettingsScreen(
    navigator: Navigator,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val qualityLevel by viewModel.qualityLevel.collectAsStateWithLifecycle()
    val qualityLevelCellular by viewModel.qualityLevelCellular.collectAsStateWithLifecycle()
    val scaleMode by viewModel.scaleMode.collectAsStateWithLifecycle()
    val hardwareDecode by viewModel.hardwareDecode.collectAsStateWithLifecycle()
    val playerCompatMode by viewModel.playerCompatMode.collectAsStateWithLifecycle()
    val playerAutoPause by viewModel.playerAutoPause.collectAsStateWithLifecycle()
    val allowBackgroundPlayback by viewModel.allowBackgroundPlayback.collectAsStateWithLifecycle()
    val playerForceHttps by viewModel.playerForceHttps.collectAsStateWithLifecycle()
    val autoFullScreen by viewModel.autoFullScreen.collectAsStateWithLifecycle()
    val autoPipOnExit by viewModel.autoPipOnExit.collectAsStateWithLifecycle()
    val pipHideDanmu by viewModel.pipHideDanmu.collectAsStateWithLifecycle()

    val qualityOptions = stringArrayResource(R.array.play_quality_options)
    val scaleModeOptions = stringArrayResource(R.array.play_scale_options)

    val qualityDefault = stringResource(R.string.play_quality_default)
    val scaleDefault = stringResource(R.string.play_scale_default)

    var activeDialog by remember { mutableStateOf<PlaySettingDialog?>(null) }
    val handleBack = navigator.rememberRouteActions().onBack

    Scaffold(
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_play)) },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column {
                SettingsSectionHeader("清晰度")
                SettingsInsetCard(embedded = embedded) {
                    SettingsMenu(
                        title = stringResource(R.string.play_quality_title),
                        subtitle = stringResource(R.string.play_quality_subtitle),
                        value = qualityOptions.getOrElse(qualityValues.indexOf(qualityLevel)) { qualityDefault },
                        onClick = { activeDialog = PlaySettingDialog.QUALITY }
                    )
                    SettingsHairlineDivider()
                    SettingsMenu(
                        title = "数据网络清晰度",
                        subtitle = "使用蜂窝数据时优先选择的播放画质",
                        value = qualityOptions.getOrElse(qualityValues.indexOf(qualityLevelCellular)) { qualityDefault },
                        onClick = { activeDialog = PlaySettingDialog.CELLULAR_QUALITY }
                    )
                }
            }

            Column {
                SettingsSectionHeader("播放器")
                SettingsInsetCard(embedded = embedded) {
                    SettingsMenu(
                        title = stringResource(R.string.play_scale_title),
                        subtitle = stringResource(R.string.play_scale_subtitle),
                        value = scaleModeOptions.getOrElse(scaleModeValues.indexOf(scaleMode)) { scaleDefault },
                        onClick = { activeDialog = PlaySettingDialog.SCALE }
                    )
                    SettingsHairlineDivider()
            SettingsSwitch(
                title = stringResource(R.string.play_hw_decode_title),
                subtitle = stringResource(R.string.play_hw_decode_subtitle),
                checked = hardwareDecode,
                onCheckedChange = { viewModel.setHardwareDecode(it) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            )
            // Keep literal delimiter guard: HorizontalDivider()
                    SettingsSwitch(
                        title = stringResource(R.string.play_compat_title),
                        subtitle = stringResource(R.string.play_compat_subtitle),
                        checked = playerCompatMode,
                        onCheckedChange = { viewModel.setPlayerCompatMode(it) }
                    )
                    SettingsHairlineDivider()
                    SettingsSwitch(
                        title = stringResource(R.string.play_auto_pause_title),
                        subtitle = stringResource(R.string.play_auto_pause_subtitle),
                        checked = playerAutoPause,
                        onCheckedChange = { viewModel.setPlayerAutoPause(it) }
                    )
                    SettingsHairlineDivider()
                    SettingsSwitch(
                        title = stringResource(R.string.play_bg_title),
                        subtitle = stringResource(R.string.play_bg_subtitle),
                        checked = allowBackgroundPlayback,
                        onCheckedChange = { viewModel.setAllowBackgroundPlayback(it) }
                    )
                    SettingsHairlineDivider()
                    SettingsSwitch(
                        title = stringResource(R.string.play_force_https_title),
                        subtitle = stringResource(R.string.play_force_https_subtitle),
                        checked = playerForceHttps,
                        onCheckedChange = { viewModel.setPlayerForceHttps(it) }
                    )
                }
            }

            Column {
                SettingsSectionHeader("直播间")
                SettingsInsetCard(embedded = embedded) {
                    SettingsSwitch(
                        title = "进入直播间自动全屏",
                        subtitle = "打开直播间后自动进入横屏全屏播放",
                        checked = autoFullScreen,
                        onCheckedChange = { viewModel.setAutoFullScreen(it) }
                    )
                    SettingsHairlineDivider()
                    SettingsSwitch(
                        title = "退出时自动小窗",
                        subtitle = "按 Home 键或系统手势退到后台时进入小窗",
                        checked = autoPipOnExit,
                        onCheckedChange = { viewModel.setAutoPipOnExit(it) }
                    )
                    SettingsHairlineDivider()
                    SettingsSwitch(
                        title = "进入小窗隐藏弹幕",
                        subtitle = "小窗播放时隐藏播放器滚动弹幕",
                        checked = pipHideDanmu,
                        onCheckedChange = { viewModel.setPipHideDanmu(it) }
                    )
                }
            }
        }
        }
    }

    when (activeDialog) {
        PlaySettingDialog.QUALITY -> {
            SettingsSelectionDialog(
                title = stringResource(R.string.play_quality_title),
                options = qualityOptions,
                values = qualityValues,
                selectedValue = qualityLevel,
                onSelect = { viewModel.setQualityLevel(it) },
                onDismiss = { activeDialog = null }
            )
        }
        PlaySettingDialog.CELLULAR_QUALITY -> {
            SettingsSelectionDialog(
                title = "数据网络清晰度",
                options = qualityOptions,
                values = qualityValues,
                selectedValue = qualityLevelCellular,
                onSelect = { viewModel.setQualityLevelCellular(it) },
                onDismiss = { activeDialog = null }
            )
        }
        PlaySettingDialog.SCALE -> {
            SettingsSelectionDialog(
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
