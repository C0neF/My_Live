package com.mylive.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.mylive.app.core.model.DanmakuSettingsPolicy
import com.mylive.app.ui.component.settings.SettingsHairlineDivider
import com.mylive.app.ui.component.settings.SettingsInsetCard
import com.mylive.app.ui.component.settings.SettingsMenu
import com.mylive.app.ui.component.settings.SettingsSectionHeader
import com.mylive.app.ui.component.settings.SettingsSelectionDialog
import com.mylive.app.ui.component.settings.SettingsSwitch
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.theme.Icons

private val fontWeightValues = DanmakuSettingsPolicy.fontWeightValues
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
    embedded: Boolean = false,
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

    val size = DanmakuSettingsPolicy.sizeMultiplier(danmuSize)
    val speed = DanmakuSettingsPolicy.speedMultiplier(danmuSpeed)
    val area = DanmakuSettingsPolicy.area(danmuArea)
    val opacity = DanmakuSettingsPolicy.opacity(danmuOpacity)
    val weight = DanmakuSettingsPolicy.fontWeight(danmuFontWeight)
    val stroke = DanmakuSettingsPolicy.strokeWidth(danmuStrokeWidth)
    val fontSizeValues = DanmakuSettingsPolicy.choicesWithCurrent(DanmakuSettingsPolicy.sizeValues, size)
    val speedValues = DanmakuSettingsPolicy.choicesWithCurrent(DanmakuSettingsPolicy.speedValues, speed)
    val areaValues = DanmakuSettingsPolicy.choicesWithCurrent(DanmakuSettingsPolicy.areaValues, area)
    val opacityValues = DanmakuSettingsPolicy.choicesWithCurrent(DanmakuSettingsPolicy.opacityValues, opacity)
    val strokeWidthValues = DanmakuSettingsPolicy.choicesWithCurrent(DanmakuSettingsPolicy.strokeValues, stroke)
    val fontSizeOptions = fontSizeValues.map(DanmakuSettingsPolicy::multiplierText).toTypedArray()
    val speedOptions = speedValues.map(DanmakuSettingsPolicy::multiplierText).toTypedArray()
    val areaOptions = areaValues.map(DanmakuSettingsPolicy::percentText).toTypedArray()
    val opacityOptions = opacityValues.map(DanmakuSettingsPolicy::percentText).toTypedArray()
    val fontWeightOptions = stringArrayResource(R.array.danmu_font_weight_options)
    val strokeWidthOptions = strokeWidthValues.map(DanmakuSettingsPolicy::strokeText).toTypedArray()

    val fontWeightDefault = stringResource(R.string.danmu_font_weight_default)

    var activeDialog by remember { mutableStateOf<DanmuSettingDialog?>(null) }
    val handleBack = navigator.rememberRouteActions().onBack

    Scaffold(
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_danmu)) },
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
                    SettingsSectionHeader("弹幕屏蔽")
                    SettingsInsetCard(embedded = embedded) {
                        SettingsSwitch(
                            title = "启用弹幕屏蔽",
                            subtitle = "关闭后关键词和用户屏蔽都会暂时失效",
                            checked = danmuShieldEnable,
                            onCheckedChange = { viewModel.setDanmuShieldEnable(it) }
                        )
                        SettingsHairlineDivider()
                        SettingsSwitch(
                            title = "启用关键词屏蔽",
                            checked = danmuKeywordShieldEnable,
                            onCheckedChange = { viewModel.setDanmuKeywordShieldEnable(it) }
                        )
                        SettingsHairlineDivider()
                        SettingsSwitch(
                            title = "启用用户屏蔽",
                            subtitle = "可按平台分别管理，也可在直播间点击用户名快速处理",
                            checked = danmuUserShieldEnable,
                            onCheckedChange = { viewModel.setDanmuUserShieldEnable(it) }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = "打开屏蔽管理",
                            subtitle = "添加、编辑关键词和屏蔽用户，管理屏蔽预设",
                            onClick = { navigator.navigate(Route.SettingsDanmuShield) }
                        )
                    }
                }

                Column {
                    SettingsSectionHeader("弹幕显示")
                    SettingsInsetCard(embedded = embedded) {
                        SettingsSwitch(
                            title = stringResource(R.string.danmu_switch_title),
                            subtitle = stringResource(R.string.danmu_enable_subtitle),
                            checked = danmuEnable,
                            onCheckedChange = { viewModel.setDanmuEnable(it) }
                        )
                        SettingsHairlineDivider()
                        SettingsSwitch(
                            title = stringResource(R.string.danmu_render_emoji_title),
                            subtitle = stringResource(R.string.danmu_render_emoji_subtitle),
                            checked = danmuRenderEmoji,
                            onCheckedChange = { viewModel.setDanmuRenderEmoji(it) }
                        )
                        SettingsHairlineDivider()
                        SettingsSwitch(
                            title = stringResource(R.string.danmu_hide_scroll_title),
                            checked = danmuHideScroll,
                            onCheckedChange = { viewModel.setDanmuHideScroll(it) }
                        )
                        SettingsHairlineDivider()
                        SettingsSwitch(
                            title = stringResource(R.string.danmu_hide_bottom_title),
                            checked = danmuHideBottom,
                            onCheckedChange = { viewModel.setDanmuHideBottom(it) }
                        )
                        SettingsHairlineDivider()
                        SettingsSwitch(
                            title = stringResource(R.string.danmu_hide_top_title),
                            checked = danmuHideTop,
                            onCheckedChange = { viewModel.setDanmuHideTop(it) }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = stringResource(R.string.danmu_size_title),
                            value = DanmakuSettingsPolicy.multiplierText(size),
                            onClick = { activeDialog = DanmuSettingDialog.FONT_SIZE }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = stringResource(R.string.danmu_speed_title),
                            value = DanmakuSettingsPolicy.multiplierText(speed),
                            onClick = { activeDialog = DanmuSettingDialog.SPEED }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = stringResource(R.string.danmu_area_title),
                            value = DanmakuSettingsPolicy.percentText(area),
                            onClick = { activeDialog = DanmuSettingDialog.AREA }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = "显示几行",
                            subtitle = "0 表示不显示播放器弹幕，超过当前区域上限时自动收紧",
                            value = danmuLineCountText(danmuLineCount),
                            onClick = { activeDialog = DanmuSettingDialog.LINE_COUNT }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = stringResource(R.string.danmu_opacity_title),
                            value = DanmakuSettingsPolicy.percentText(opacity),
                            onClick = { activeDialog = DanmuSettingDialog.OPACITY }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = stringResource(R.string.danmu_font_weight_title),
                            value = fontWeightOptions.getOrElse(fontWeightValues.indexOf(weight)) { fontWeightDefault },
                            onClick = { activeDialog = DanmuSettingDialog.FONT_WEIGHT }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = stringResource(R.string.danmu_stroke_title),
                            value = DanmakuSettingsPolicy.strokeText(stroke),
                            onClick = { activeDialog = DanmuSettingDialog.STROKE_WIDTH }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = "全局弹幕延迟",
                            subtitle = "单位毫秒，适合不同平台节奏不同或网络抖动时微调",
                            value = "${danmuDelay.toInt()} ms",
                            onClick = { activeDialog = DanmuSettingDialog.DELAY }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = "顶部安全边距",
                            subtitle = "异形屏或状态栏遮挡时可微调",
                            value = "${danmuTopMargin.toInt()} dp",
                            onClick = { activeDialog = DanmuSettingDialog.TOP_MARGIN }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = "底部安全边距",
                            subtitle = "导航栏或手势条遮挡时可微调",
                            value = "${danmuBottomMargin.toInt()} dp",
                            onClick = { activeDialog = DanmuSettingDialog.BOTTOM_MARGIN }
                        )
                    }
                }

                Column {
                    SettingsSectionHeader("弹幕过滤")
                    SettingsInsetCard(embedded = embedded) {
                        SettingsSwitch(
                            title = "重复弹幕过滤",
                            subtitle = "同一用户在最近若干条内重复刷同一句时只显示一次",
                            checked = danmuDedupeEnable,
                            onCheckedChange = { viewModel.setDanmuDedupeEnable(it) }
                        )
                        SettingsHairlineDivider()
                        SettingsSwitch(
                            title = "严格去重模式",
                            subtitle = "全局过滤不同观众发送的相同弹幕",
                            checked = danmuDedupeStrictMode,
                            onCheckedChange = { viewModel.setDanmuDedupeStrictMode(it) }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = "过滤窗口",
                            subtitle = "默认 10 条；窗口越大越容易过滤刷屏",
                            value = "$danmuDedupeWindow 条",
                            onClick = { activeDialog = DanmuSettingDialog.DEDUPE_WINDOW }
                        )
                        SettingsHairlineDivider()
                        SettingsMenu(
                            title = "过滤步长",
                            subtitle = "默认 2；数值越大检查窗口移动越少",
                            value = "$danmuDedupeStep 条",
                            onClick = { activeDialog = DanmuSettingDialog.DEDUPE_STEP }
                        )
                    }
                }
            }
        }
    }

    when (activeDialog) {
        DanmuSettingDialog.FONT_SIZE -> {
            SettingsSelectionDialog(
                title = stringResource(R.string.danmu_size_title),
                options = fontSizeOptions,
                values = fontSizeValues,
                selectedValue = size,
                onSelect = { viewModel.setDanmuSize(it) },
                onDismiss = { activeDialog = null },
                equalsPredicate = DanmakuSettingsPolicy::sameValue
            )
        }
        DanmuSettingDialog.SPEED -> {
            SettingsSelectionDialog(
                title = stringResource(R.string.danmu_speed_title),
                options = speedOptions,
                values = speedValues,
                selectedValue = speed,
                onSelect = { viewModel.setDanmuSpeed(it) },
                onDismiss = { activeDialog = null },
                equalsPredicate = DanmakuSettingsPolicy::sameValue
            )
        }
        DanmuSettingDialog.AREA -> {
            SettingsSelectionDialog(
                title = stringResource(R.string.danmu_area_title),
                options = areaOptions,
                values = areaValues,
                selectedValue = area,
                onSelect = { viewModel.setDanmuArea(it) },
                onDismiss = { activeDialog = null },
                equalsPredicate = DanmakuSettingsPolicy::sameValue
            )
        }
        DanmuSettingDialog.LINE_COUNT -> {
            SettingsSelectionDialog(
                title = "显示几行",
                options = lineCountValues.map { danmuLineCountText(it) }.toTypedArray(),
                values = lineCountValues,
                selectedValue = danmuLineCount,
                onSelect = { viewModel.setDanmuLineCount(it) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.OPACITY -> {
            SettingsSelectionDialog(
                title = stringResource(R.string.danmu_opacity_title),
                options = opacityOptions,
                values = opacityValues,
                selectedValue = opacity,
                onSelect = { viewModel.setDanmuOpacity(it) },
                onDismiss = { activeDialog = null },
                equalsPredicate = DanmakuSettingsPolicy::sameValue
            )
        }
        DanmuSettingDialog.FONT_WEIGHT -> {
            SettingsSelectionDialog(
                title = stringResource(R.string.danmu_font_weight_title),
                options = fontWeightOptions,
                values = fontWeightValues,
                selectedValue = weight,
                onSelect = { viewModel.setDanmuFontWeight(it) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.STROKE_WIDTH -> {
            SettingsSelectionDialog(
                title = stringResource(R.string.danmu_stroke_title),
                options = strokeWidthOptions,
                values = strokeWidthValues,
                selectedValue = stroke,
                onSelect = { viewModel.setDanmuStrokeWidth(it) },
                onDismiss = { activeDialog = null },
                equalsPredicate = DanmakuSettingsPolicy::sameValue
            )
        }
        DanmuSettingDialog.DELAY -> {
            SettingsSelectionDialog(
                title = "全局弹幕延迟",
                options = delayValues.map { "$it ms" }.toTypedArray(),
                values = delayValues,
                selectedValue = danmuDelay.toInt(),
                onSelect = { viewModel.setDanmuDelay(it.toDouble()) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.TOP_MARGIN -> {
            SettingsSelectionDialog(
                title = "顶部安全边距",
                options = safeMarginValues.map { "$it dp" }.toTypedArray(),
                values = safeMarginValues,
                selectedValue = danmuTopMargin.toInt(),
                onSelect = { viewModel.setDanmuTopMargin(it.toDouble()) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.BOTTOM_MARGIN -> {
            SettingsSelectionDialog(
                title = "底部安全边距",
                options = safeMarginValues.map { "$it dp" }.toTypedArray(),
                values = safeMarginValues,
                selectedValue = danmuBottomMargin.toInt(),
                onSelect = { viewModel.setDanmuBottomMargin(it.toDouble()) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.DEDUPE_WINDOW -> {
            SettingsSelectionDialog(
                title = "过滤窗口",
                options = dedupeWindowValues.map { "$it 条" }.toTypedArray(),
                values = dedupeWindowValues,
                selectedValue = danmuDedupeWindow,
                onSelect = { viewModel.setDanmuDedupeWindow(it) },
                onDismiss = { activeDialog = null }
            )
        }
        DanmuSettingDialog.DEDUPE_STEP -> {
            SettingsSelectionDialog(
                title = "过滤步长",
                options = dedupeStepValues.map { "$it 条" }.toTypedArray(),
                values = dedupeStepValues,
                selectedValue = danmuDedupeStep,
                onSelect = { viewModel.setDanmuDedupeStep(it) },
                onDismiss = { activeDialog = null }
            )
        }
        null -> Unit
    }
}

private fun danmuLineCountText(value: Int): String =
    if (value <= 0) "不显示弹幕" else "$value 行"
