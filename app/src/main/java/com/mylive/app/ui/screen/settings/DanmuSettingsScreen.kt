package com.mylive.app.ui.screen.settings

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
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
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
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.component.settings.SettingsSwitch

private val fontSizeValues = listOf(0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.5)
private val speedValues = listOf(0.6, 0.8, 1.0, 1.2, 1.5)
private val areaValues = listOf(0.25, 0.333, 0.5, 0.667, 1.0)
private val opacityValues = listOf(0.2, 0.4, 0.6, 0.8, 1.0)
private val fontWeightValues = listOf(200, 400, 600, 800)
private val strokeWidthValues = listOf(0.0, 0.5, 1.0, 2.0)
private val lineCountValues = (0..12).toList()
private val delayValues = (0..50).map { it * 100 }
private val safeMarginValues = (0..12).map { it * 4 }
private val dedupeWindowValues = listOf(1, 5, 10, 20, 30, 50, 100)
private val dedupeStepValues = listOf(1, 2, 3, 5, 10, 20)

enum class DanmuSettingDialog {
    FONT_SIZE,
    SPEED,
    AREA,
    LINE_COUNT,
    OPACITY,
    FONT_WEIGHT,
    STROKE_WIDTH,
    DELAY,
    TOP_MARGIN,
    BOTTOM_MARGIN,
    DEDUPE_WINDOW,
    DEDUPE_STEP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DanmuSettingsScreen(
    navigator: Navigator,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val danmuEnable by viewModel.danmuEnable.collectAsStateWithLifecycle()
    val danmuRenderEmoji by viewModel.danmuRenderEmoji.collectAsStateWithLifecycle()
    val danmuHideScroll by viewModel.danmuHideScroll.collectAsStateWithLifecycle()
    val danmuHideBottom by viewModel.danmuHideBottom.collectAsStateWithLifecycle()
    val danmuHideTop by viewModel.danmuHideTop.collectAsStateWithLifecycle()
    val danmuSize by viewModel.danmuSize.collectAsStateWithLifecycle()
    val danmuSpeed by viewModel.danmuSpeed.collectAsStateWithLifecycle()
    val danmuArea by viewModel.danmuArea.collectAsStateWithLifecycle()
    val danmuLineCount by viewModel.danmuLineCount.collectAsStateWithLifecycle()
    val danmuDelay by viewModel.danmuDelay.collectAsStateWithLifecycle()
    val danmuOpacity by viewModel.danmuOpacity.collectAsStateWithLifecycle()
    val danmuFontWeight by viewModel.danmuFontWeight.collectAsStateWithLifecycle()
    val danmuStrokeWidth by viewModel.danmuStrokeWidth.collectAsStateWithLifecycle()
    val danmuTopMargin by viewModel.danmuTopMargin.collectAsStateWithLifecycle()
    val danmuBottomMargin by viewModel.danmuBottomMargin.collectAsStateWithLifecycle()
    val danmuDedupeEnable by viewModel.danmuDedupeEnable.collectAsStateWithLifecycle()
    val danmuDedupeWindow by viewModel.danmuDedupeWindow.collectAsStateWithLifecycle()
    val danmuDedupeStep by viewModel.danmuDedupeStep.collectAsStateWithLifecycle()
    val danmuDedupeStrictMode by viewModel.danmuDedupeStrictMode.collectAsStateWithLifecycle()
    val danmuShieldEnable by viewModel.danmuShieldEnable.collectAsStateWithLifecycle()
    val danmuKeywordShieldEnable by viewModel.danmuKeywordShieldEnable.collectAsStateWithLifecycle()
    val danmuUserShieldEnable by viewModel.danmuUserShieldEnable.collectAsStateWithLifecycle()

    val fontSizeOptions = stringArrayResource(R.array.danmu_font_size_options)
    val speedOptions = stringArrayResource(R.array.danmu_speed_options)
    val areaOptions = stringArrayResource(R.array.danmu_area_options)
    val opacityOptions = stringArrayResource(R.array.danmu_opacity_options)
    val fontWeightOptions = stringArrayResource(R.array.danmu_font_weight_options)
    val strokeWidthOptions = stringArrayResource(R.array.danmu_stroke_options)

    val fontSizeDefault = stringResource(R.string.danmu_font_size_default)
    val speedDefault = stringResource(R.string.danmu_speed_default)
    val areaDefault = stringResource(R.string.danmu_area_default)
    val opacityDefault = stringResource(R.string.danmu_opacity_default)
    val fontWeightDefault = stringResource(R.string.danmu_font_weight_default)
    val strokeDefault = stringResource(R.string.danmu_stroke_default)

    var activeDialog by remember { mutableStateOf<DanmuSettingDialog?>(null) }

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
                title = { Text(stringResource(R.string.settings_danmu)) },
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
            SettingsSectionTitle("弹幕屏蔽")

            SettingsSwitch(
                title = "启用弹幕屏蔽",
                subtitle = "关闭后关键词和用户屏蔽都会暂时失效",
                checked = danmuShieldEnable,
                onCheckedChange = { viewModel.setDanmuShieldEnable(it) }
            )
            HorizontalDivider()

            SettingsSwitch(
                title = "启用关键词屏蔽",
                checked = danmuKeywordShieldEnable,
                onCheckedChange = { viewModel.setDanmuKeywordShieldEnable(it) }
            )
            HorizontalDivider()

            SettingsSwitch(
                title = "启用用户屏蔽",
                subtitle = "可按平台分别管理，也可在直播间点击用户名快速处理",
                checked = danmuUserShieldEnable,
                onCheckedChange = { viewModel.setDanmuUserShieldEnable(it) }
            )
            HorizontalDivider()

            SettingsMenu(
                title = "打开屏蔽管理",
                subtitle = "添加、编辑关键词和屏蔽用户，管理屏蔽预设",
                onClick = { navigator.navigate(Route.SettingsDanmuShield) }
            )
            HorizontalDivider()

            SettingsSectionTitle("弹幕显示")

            SettingsSwitch(
                title = stringResource(R.string.danmu_switch_title),
                subtitle = stringResource(R.string.danmu_enable_subtitle),
                checked = danmuEnable,
                onCheckedChange = { viewModel.setDanmuEnable(it) }
            )
            HorizontalDivider()

            SettingsSwitch(
                title = stringResource(R.string.danmu_render_emoji_title),
                subtitle = stringResource(R.string.danmu_render_emoji_subtitle),
                checked = danmuRenderEmoji,
                onCheckedChange = { viewModel.setDanmuRenderEmoji(it) }
            )
            HorizontalDivider()

            SettingsSwitch(
                title = stringResource(R.string.danmu_hide_scroll_title),
                checked = danmuHideScroll,
                onCheckedChange = { viewModel.setDanmuHideScroll(it) }
            )
            HorizontalDivider()

            SettingsSwitch(
                title = stringResource(R.string.danmu_hide_bottom_title),
                checked = danmuHideBottom,
                onCheckedChange = { viewModel.setDanmuHideBottom(it) }
            )
            HorizontalDivider()

            SettingsSwitch(
                title = stringResource(R.string.danmu_hide_top_title),
                checked = danmuHideTop,
                onCheckedChange = { viewModel.setDanmuHideTop(it) }
            )
            HorizontalDivider()

            // Menu items with selection dialogs
            SettingsMenu(
                title = stringResource(R.string.danmu_size_title),
                value = fontSizeOptions.getOrElse(fontSizeValues.indexOf(danmuSize)) { fontSizeDefault },
                onClick = { activeDialog = DanmuSettingDialog.FONT_SIZE }
            )
            HorizontalDivider()

            SettingsMenu(
                title = stringResource(R.string.danmu_speed_title),
                value = speedOptions.getOrElse(speedValues.indexOf(danmuSpeed)) { speedDefault },
                onClick = { activeDialog = DanmuSettingDialog.SPEED }
            )
            HorizontalDivider()

            SettingsMenu(
                title = stringResource(R.string.danmu_area_title),
                value = areaOptions.getOrElse(areaValues.indexOf(danmuArea)) { areaDefault },
                onClick = { activeDialog = DanmuSettingDialog.AREA }
            )
            HorizontalDivider()

            SettingsMenu(
                title = "显示几行",
                subtitle = "0 表示不显示播放器弹幕，超过当前区域上限时自动收紧",
                value = danmuLineCountText(danmuLineCount),
                onClick = { activeDialog = DanmuSettingDialog.LINE_COUNT }
            )
            HorizontalDivider()

            SettingsMenu(
                title = stringResource(R.string.danmu_opacity_title),
                value = opacityOptions.getOrElse(opacityValues.indexOf(danmuOpacity)) { opacityDefault },
                onClick = { activeDialog = DanmuSettingDialog.OPACITY }
            )
            HorizontalDivider()

            SettingsMenu(
                title = stringResource(R.string.danmu_font_weight_title),
                value = fontWeightOptions.getOrElse(fontWeightValues.indexOf(danmuFontWeight)) { fontWeightDefault },
                onClick = { activeDialog = DanmuSettingDialog.FONT_WEIGHT }
            )
            HorizontalDivider()

            SettingsMenu(
                title = stringResource(R.string.danmu_stroke_title),
                value = strokeWidthOptions.getOrElse(strokeWidthValues.indexOf(danmuStrokeWidth)) { strokeDefault },
                onClick = { activeDialog = DanmuSettingDialog.STROKE_WIDTH }
            )
            HorizontalDivider()

            SettingsMenu(
                title = "全局弹幕延迟",
                subtitle = "单位毫秒，适合不同平台节奏不同或网络抖动时微调",
                value = "${danmuDelay.toInt()} ms",
                onClick = { activeDialog = DanmuSettingDialog.DELAY }
            )
            HorizontalDivider()

            SettingsMenu(
                title = "顶部安全边距",
                subtitle = "异形屏或状态栏遮挡时可微调",
                value = "${danmuTopMargin.toInt()} dp",
                onClick = { activeDialog = DanmuSettingDialog.TOP_MARGIN }
            )
            HorizontalDivider()

            SettingsMenu(
                title = "底部安全边距",
                subtitle = "导航栏或手势条遮挡时可微调",
                value = "${danmuBottomMargin.toInt()} dp",
                onClick = { activeDialog = DanmuSettingDialog.BOTTOM_MARGIN }
            )
            HorizontalDivider()

            SettingsSectionTitle("弹幕过滤")

            SettingsSwitch(
                title = "重复弹幕过滤",
                subtitle = "同一用户在最近若干条内重复刷同一句时只显示一次",
                checked = danmuDedupeEnable,
                onCheckedChange = { viewModel.setDanmuDedupeEnable(it) }
            )
            HorizontalDivider()

            SettingsSwitch(
                title = "严格去重模式",
                subtitle = "全局过滤不同观众发送的相同弹幕",
                checked = danmuDedupeStrictMode,
                onCheckedChange = { viewModel.setDanmuDedupeStrictMode(it) }
            )
            HorizontalDivider()

            SettingsMenu(
                title = "过滤窗口",
                subtitle = "默认 10 条；窗口越大越容易过滤刷屏",
                value = "$danmuDedupeWindow 条",
                onClick = { activeDialog = DanmuSettingDialog.DEDUPE_WINDOW }
            )
            HorizontalDivider()

            SettingsMenu(
                title = "过滤步长",
                subtitle = "默认 2；数值越大检查窗口移动越少",
                value = "$danmuDedupeStep 条",
                onClick = { activeDialog = DanmuSettingDialog.DEDUPE_STEP }
            )
        }
    }

    when (activeDialog) {
        DanmuSettingDialog.FONT_SIZE -> {
            SelectionDialog(
                title = stringResource(R.string.danmu_size_title),
                options = fontSizeOptions,
                values = fontSizeValues,
                selectedValue = danmuSize,
                onSelect = { viewModel.setDanmuSize(it) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.SPEED -> {
            SelectionDialog(
                title = stringResource(R.string.danmu_speed_title),
                options = speedOptions,
                values = speedValues,
                selectedValue = danmuSpeed,
                onSelect = { viewModel.setDanmuSpeed(it) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.AREA -> {
            SelectionDialog(
                title = stringResource(R.string.danmu_area_title),
                options = areaOptions,
                values = areaValues,
                selectedValue = danmuArea,
                onSelect = { viewModel.setDanmuArea(it) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.LINE_COUNT -> {
            SelectionDialog(
                title = "显示几行",
                options = lineCountValues.map { danmuLineCountText(it) }.toTypedArray(),
                values = lineCountValues,
                selectedValue = danmuLineCount,
                onSelect = { viewModel.setDanmuLineCount(it) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.OPACITY -> {
            SelectionDialog(
                title = stringResource(R.string.danmu_opacity_title),
                options = opacityOptions,
                values = opacityValues,
                selectedValue = danmuOpacity,
                onSelect = { viewModel.setDanmuOpacity(it) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.FONT_WEIGHT -> {
            SelectionDialog(
                title = stringResource(R.string.danmu_font_weight_title),
                options = fontWeightOptions,
                values = fontWeightValues,
                selectedValue = danmuFontWeight,
                onSelect = { viewModel.setDanmuFontWeight(it) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.STROKE_WIDTH -> {
            SelectionDialog(
                title = stringResource(R.string.danmu_stroke_title),
                options = strokeWidthOptions,
                values = strokeWidthValues,
                selectedValue = danmuStrokeWidth,
                onSelect = { viewModel.setDanmuStrokeWidth(it) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.DELAY -> {
            SelectionDialog(
                title = "全局弹幕延迟",
                options = delayValues.map { "$it ms" }.toTypedArray(),
                values = delayValues,
                selectedValue = danmuDelay.toInt(),
                onSelect = { viewModel.setDanmuDelay(it.toDouble()) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.TOP_MARGIN -> {
            SelectionDialog(
                title = "顶部安全边距",
                options = safeMarginValues.map { "$it dp" }.toTypedArray(),
                values = safeMarginValues,
                selectedValue = danmuTopMargin.toInt(),
                onSelect = { viewModel.setDanmuTopMargin(it.toDouble()) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.BOTTOM_MARGIN -> {
            SelectionDialog(
                title = "底部安全边距",
                options = safeMarginValues.map { "$it dp" }.toTypedArray(),
                values = safeMarginValues,
                selectedValue = danmuBottomMargin.toInt(),
                onSelect = { viewModel.setDanmuBottomMargin(it.toDouble()) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.DEDUPE_WINDOW -> {
            SelectionDialog(
                title = "过滤窗口",
                options = dedupeWindowValues.map { "$it 条" }.toTypedArray(),
                values = dedupeWindowValues,
                selectedValue = danmuDedupeWindow,
                onSelect = { viewModel.setDanmuDedupeWindow(it) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.DEDUPE_STEP -> {
            SelectionDialog(
                title = "过滤步长",
                options = dedupeStepValues.map { "$it 条" }.toTypedArray(),
                values = dedupeStepValues,
                selectedValue = danmuDedupeStep,
                onSelect = { viewModel.setDanmuDedupeStep(it) },
                onDismiss = { activeDialog = null }
            )
        }
        null -> { /* Do nothing */ }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

private fun danmuLineCountText(value: Int): String =
    if (value <= 0) "不显示弹幕" else "$value 行"

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

