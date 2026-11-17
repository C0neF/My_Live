package com.mylive.app.ui.screen.room

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mylive.app.ui.theme.Icons
import com.mylive.app.ui.theme.livePlatformOnAccentColor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import com.mylive.app.ui.layout.LocalAppLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mylive.app.ui.navigation.Navigator
import coil3.compose.AsyncImage
import com.mylive.app.R
import com.mylive.app.core.common.buildLiveMessageDisplaySpans
import com.mylive.app.core.common.normalizeLiveMessageDisplaySpans
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import com.mylive.app.core.model.LiveMessageSpan
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.data.repository.LiveRoomPreferences
import com.mylive.app.ui.emoji.EmojiImage
import com.mylive.app.ui.screen.room.player.LiveDanmakuSurfaceFeed
import com.mylive.app.ui.screen.room.player.LivePlaybackHostContent
import com.mylive.app.ui.screen.room.player.DanmakuConfig
import com.mylive.app.ui.screen.room.player.shouldPrepareLivePlaybackExit
import com.mylive.app.data.local.entity.playbackRoomId
import com.mylive.app.core.model.DanmakuSettingsPolicy
import com.mylive.app.ui.component.FollowRefreshProgressLine
import com.mylive.app.ui.component.followRefreshHint
import com.mylive.app.ui.component.rememberFollowRefreshEntry
import com.mylive.app.ui.screen.room.player.PlayerDanmakuActions
import com.mylive.app.ui.screen.room.player.PlayerController
import com.mylive.app.ui.screen.room.player.PlayerView
import com.mylive.app.ui.screen.room.player.PlayerViewActions
import com.mylive.app.ui.screen.room.player.PlayerViewUiState
import com.mylive.app.ui.screen.room.player.DanmakuController
import com.mylive.app.ui.screen.room.player.rememberLivePlaybackHost
import com.mylive.app.ui.screen.room.player.shouldFeedLiveDanmakuSurface
import com.mylive.app.ui.screen.room.player.shouldFeedPipDanmakuSurface
import com.mylive.app.ui.screen.room.player.playbackContainerAspectRatio
import com.mylive.app.ui.screen.settings.SettingsViewModel
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clipToBounds
import com.mylive.app.ui.screen.follow.FollowViewModel
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.core.model.LiveSuperChatMessage
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.navigation.NavigationExitEffect
import com.mylive.app.ui.screen.room.quickaccess.QuickAccessExtraTab
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicLong


@Composable
fun RoomSettingsPanel(
    liveRoomPreferences: LiveRoomPreferences,
    isHuyaOrBilibili: Boolean,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    siteId: String = "",
    onOpenShieldSettings: () -> Unit = {},
    listState: LazyListState = rememberLazyListState()
) {
    val chatTextSize = liveRoomPreferences.chatTextSize
    val chatTextGap = liveRoomPreferences.chatTextGap
    val chatBubbleStyle = liveRoomPreferences.chatBubbleStyle
    val superChatSortDesc = liveRoomPreferences.superChatSortDesc
    val danmuEnable = liveRoomPreferences.danmuEnable
    val danmuRenderEmoji = liveRoomPreferences.danmuRenderEmoji
    val danmuSize = liveRoomPreferences.danmuSize
    val danmuSpeed = liveRoomPreferences.danmuSpeed
    val danmuArea = liveRoomPreferences.danmuArea
    val danmuLineCount = liveRoomPreferences.danmuLineCount
    val danmuDelay = liveRoomPreferences.danmuDelay
    val danmuDelayBySiteJson = liveRoomPreferences.danmuDelayBySiteJson
    val danmuOpacity = liveRoomPreferences.danmuOpacity
    val danmuStrokeWidth = liveRoomPreferences.danmuStrokeWidth
    val danmuTopMargin = liveRoomPreferences.danmuTopMargin
    val danmuBottomMargin = liveRoomPreferences.danmuBottomMargin
    val danmuDedupeEnable = liveRoomPreferences.danmuDedupeEnable
    val danmuDedupeWindow = liveRoomPreferences.danmuDedupeWindow
    val danmuDedupeStep = liveRoomPreferences.danmuDedupeStep
    val danmuDedupeStrictMode = liveRoomPreferences.danmuDedupeStrictMode
    val danmuShieldEnable = liveRoomPreferences.danmuShieldEnable
    val danmuKeywordShieldEnable = liveRoomPreferences.danmuKeywordShieldEnable
    val danmuUserShieldEnable = liveRoomPreferences.danmuUserShieldEnable
    val autoFullScreen = liveRoomPreferences.autoFullScreen
    val autoPipOnExit = liveRoomPreferences.autoPipOnExit
    val pipHideDanmu = liveRoomPreferences.pipHideDanmu
    val normalizedDanmuSize = roomSettingsNormalizedDanmuSize(danmuSize)
    val normalizedDanmuSpeed = roomSettingsNormalizedDanmuSpeed(danmuSpeed)
    val normalizedDanmuArea = DanmakuSettingsPolicy.area(danmuArea)
    val normalizedDanmuOpacity = DanmakuSettingsPolicy.opacity(danmuOpacity)
    val normalizedDanmuStroke = DanmakuSettingsPolicy.strokeWidth(danmuStrokeWidth)
    val siteDanmuDelay = remember(danmuDelayBySiteJson, siteId, danmuDelay) {
        resolveDanmuDelayMs(
            globalDelayMs = danmuDelay.toInt(),
            delayBySiteJson = danmuDelayBySiteJson,
            siteId = siteId
        )
    }
    var expandedSectionNames by rememberSaveable {
        mutableStateOf(defaultExpandedRoomSettingsSections().map { it.name })
    }

    fun isRoomSettingsSectionExpanded(sectionKey: RoomSettingsSectionKey): Boolean {
        return sectionKey.name in expandedSectionNames
    }

    fun setRoomSettingsSectionExpanded(sectionKey: RoomSettingsSectionKey, expanded: Boolean) {
        // Pin the clicked header in place while its content changes height, including sticky headers.
        val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == sectionKey.name }
        listState.requestScrollToItem(
            index = header?.index ?: sectionKey.ordinal * 3,
            scrollOffset = -(header?.offset ?: 0).coerceAtLeast(0)
        )
        expandedSectionNames = if (expanded) {
            (expandedSectionNames + sectionKey.name).distinct()
        } else {
            expandedSectionNames.filterNot { it == sectionKey.name }
        }
    }

    // Settings accent follows the live room's platform brand color (Douyu/Huya/Bilibili).
    val settingsAccentColor = resolveLiveRoomPlatformAccentColor(
        siteId = siteId,
        defaultAccentColor = MaterialTheme.colorScheme.primary
    )
    val settingsOnAccentColor = livePlatformOnAccentColor(siteId, MaterialTheme.colorScheme.onPrimary)

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = settingsAccentColor,
            onPrimary = settingsOnAccentColor
        )
    ) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("room_settings_list"),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
    ) {
        RoomSettingsCollapsibleSection(
            listState = listState,
            sectionKey = RoomSettingsSectionKey.PLAYER_DANMAKU,
            title = "播放器弹幕设置",
            subtitle = "滚动弹幕显示、表情、区域、延迟和边距",
            expanded = isRoomSettingsSectionExpanded(RoomSettingsSectionKey.PLAYER_DANMAKU),
            onExpandedChange = {
                setRoomSettingsSectionExpanded(RoomSettingsSectionKey.PLAYER_DANMAKU, it)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoomSettingsSwitchRow(
                    title = "显示播放器弹幕",
                    subtitle = "在视频画面上显示滚动弹幕",
                    checked = danmuEnable,
                    onCheckedChange = { viewModel.setDanmuEnable(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSwitchRow(
                    title = "渲染表情",
                    subtitle = "在播放器弹幕中显示平台表情图片",
                    checked = danmuRenderEmoji,
                    onCheckedChange = { viewModel.setDanmuRenderEmoji(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSliderRow(
                    title = "弹幕字号",
                    valueText = roomSettingsMultiplierText(normalizedDanmuSize),
                    value = normalizedDanmuSize.toFloat(),
                    onValueChange = { viewModel.setDanmuSize(it.toDouble()) },
                    valueRange = DanmakuSettingsPolicy.sizeRange,
                    steps = DanmakuSettingsPolicy.sizeValues.size - 2
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSliderRow(
                    title = "弹幕速度",
                    valueText = roomSettingsMultiplierText(normalizedDanmuSpeed),
                    value = normalizedDanmuSpeed.toFloat(),
                    onValueChange = { viewModel.setDanmuSpeed(it.toDouble()) },
                    valueRange = DanmakuSettingsPolicy.speedRange,
                    steps = DanmakuSettingsPolicy.speedValues.size - 2
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSliderRow(
                    title = "显示区域",
                    valueText = roomSettingsPercentText(normalizedDanmuArea),
                    value = normalizedDanmuArea.toFloat(),
                    onValueChange = { viewModel.setDanmuArea(it.toDouble()) },
                    valueRange = DanmakuSettingsPolicy.areaRange,
                    steps = 14
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSliderRow(
                    title = "显示几行",
                    valueText = if (danmuLineCount <= 0) "不显示" else "$danmuLineCount 行",
                    value = danmuLineCount.toFloat(),
                    onValueChange = { viewModel.setDanmuLineCount(it.toInt()) },
                    valueRange = 0f..12f,
                    steps = 11
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSliderRow(
                    title = "弹幕透明度",
                    valueText = roomSettingsPercentText(normalizedDanmuOpacity),
                    value = normalizedDanmuOpacity.toFloat(),
                    onValueChange = { viewModel.setDanmuOpacity(it.toDouble()) },
                    valueRange = DanmakuSettingsPolicy.opacityRange,
                    steps = DanmakuSettingsPolicy.opacityValues.size - 2
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSliderRow(
                    title = "弹幕描边",
                    valueText = roomSettingsStrokeText(normalizedDanmuStroke),
                    value = normalizedDanmuStroke.toFloat(),
                    onValueChange = { viewModel.setDanmuStrokeWidth(it.toDouble()) },
                    valueRange = DanmakuSettingsPolicy.strokeRange,
                    steps = DanmakuSettingsPolicy.strokeValues.size - 2
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSliderRow(
                    title = "全局弹幕延迟",
                    valueText = "${danmuDelay.toInt()} ms",
                    value = danmuDelay.toFloat(),
                    onValueChange = { viewModel.setDanmuDelay(it.toDouble()) },
                    valueRange = 0f..5000f,
                    steps = 49
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSliderRow(
                    title = "${danmuDelaySiteLabel(siteId)} 平台补偿延迟",
                    valueText = "$siteDanmuDelay ms",
                    value = siteDanmuDelay.toFloat(),
                    onValueChange = {
                        viewModel.setDanmuDelayBySiteJson(
                            updateDanmuDelayBySite(
                                delayBySiteJson = danmuDelayBySiteJson,
                                siteId = siteId,
                                delayMs = it.toInt()
                            )
                        )
                    },
                    valueRange = 0f..5000f,
                    steps = 49
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSliderRow(
                    title = "顶部安全边距",
                    valueText = "${danmuTopMargin.toInt()} dp",
                    value = danmuTopMargin.toFloat(),
                    onValueChange = { viewModel.setDanmuTopMargin(it.toDouble()) },
                    valueRange = 0f..48f,
                    steps = 11
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSliderRow(
                    title = "底部安全边距",
                    valueText = "${danmuBottomMargin.toInt()} dp",
                    value = danmuBottomMargin.toFloat(),
                    onValueChange = { viewModel.setDanmuBottomMargin(it.toDouble()) },
                    valueRange = 0f..48f,
                    steps = 11
                )
            }
        }

        RoomSettingsCollapsibleSection(
            listState = listState,
            sectionKey = RoomSettingsSectionKey.LIVE_BEHAVIOR,
            title = "直播间行为设置",
            subtitle = "全屏、小窗和小窗弹幕行为",
            expanded = isRoomSettingsSectionExpanded(RoomSettingsSectionKey.LIVE_BEHAVIOR),
            onExpandedChange = {
                setRoomSettingsSectionExpanded(RoomSettingsSectionKey.LIVE_BEHAVIOR, it)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoomSettingsSwitchRow(
                    title = "进入直播间自动全屏",
                    subtitle = "打开直播间后自动进入横屏全屏播放",
                    checked = autoFullScreen,
                    onCheckedChange = { viewModel.setAutoFullScreen(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSwitchRow(
                    title = "退出时自动小窗",
                    subtitle = "按 Home 键或系统手势退到后台时进入小窗",
                    checked = autoPipOnExit,
                    onCheckedChange = { viewModel.setAutoPipOnExit(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSwitchRow(
                    title = "进入小窗隐藏弹幕",
                    subtitle = "小窗播放时隐藏播放器滚动弹幕",
                    checked = pipHideDanmu,
                    onCheckedChange = { viewModel.setPipHideDanmu(it) }
                )
            }
        }

        RoomSettingsCollapsibleSection(
            listState = listState,
            sectionKey = RoomSettingsSectionKey.CHAT,
            title = "聊天区设置",
            subtitle = "文字大小、间距、气泡样式和 SC 排序",
            expanded = isRoomSettingsSectionExpanded(RoomSettingsSectionKey.CHAT),
            onExpandedChange = {
                setRoomSettingsSectionExpanded(RoomSettingsSectionKey.CHAT, it)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Font size slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "文字大小", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(text = "${chatTextSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    RoomSettingsSlider(
                        value = chatTextSize.toFloat(),
                        onValueChange = { viewModel.setChatTextSize(it.toDouble()) },
                        valueRange = 8f..36f,
                        steps = 27,
                        modifier = Modifier.semantics { contentDescription = "文字大小" }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Gap slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "上下间隔", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(text = "${chatTextGap.toInt()} dp", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    RoomSettingsSlider(
                        value = chatTextGap.toFloat(),
                        onValueChange = { viewModel.setChatTextGap(it.toDouble()) },
                        valueRange = 0f..12f,
                        steps = 11,
                        modifier = Modifier.semantics { contentDescription = "上下间隔" }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Bubble style switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "气泡样式", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(text = "使用类似社交聊天的气泡框渲染弹幕", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = chatBubbleStyle,
                        onCheckedChange = { viewModel.setChatBubbleStyle(it) }
                    )
                }

                if (isHuyaOrBilibili) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // SC sort desc
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "SC排序", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = if (superChatSortDesc) "按消失时间倒序" else "按消失时间正序",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = superChatSortDesc,
                            onCheckedChange = { viewModel.setSuperChatSortDesc(it) }
                        )
                    }
                }
            }
        }

        RoomSettingsCollapsibleSection(
            listState = listState,
            sectionKey = RoomSettingsSectionKey.SHIELD,
            title = "弹幕屏蔽设置",
            subtitle = "关键词、用户屏蔽和屏蔽管理入口",
            expanded = isRoomSettingsSectionExpanded(RoomSettingsSectionKey.SHIELD),
            onExpandedChange = {
                setRoomSettingsSectionExpanded(RoomSettingsSectionKey.SHIELD, it)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoomSettingsSwitchRow(
                    title = "启用弹幕屏蔽",
                    subtitle = "关闭后关键词和用户屏蔽都会暂时失效",
                    checked = danmuShieldEnable,
                    onCheckedChange = { viewModel.setDanmuShieldEnable(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSwitchRow(
                    title = "关键词屏蔽",
                    subtitle = "按普通文本或 /.../ 正则过滤聊天弹幕",
                    checked = danmuKeywordShieldEnable,
                    onCheckedChange = { viewModel.setDanmuKeywordShieldEnable(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                RoomSettingsSwitchRow(
                    title = "用户屏蔽",
                    subtitle = "按全平台或当前平台用户名过滤聊天弹幕",
                    checked = danmuUserShieldEnable,
                    onCheckedChange = { viewModel.setDanmuUserShieldEnable(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenShieldSettings),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "打开屏蔽管理", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(text = "添加、编辑关键词和屏蔽用户，管理屏蔽预设", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        RoomSettingsCollapsibleSection(
            listState = listState,
            sectionKey = RoomSettingsSectionKey.FILTER,
            title = "弹幕过滤设置",
            subtitle = "重复弹幕过滤、严格模式、窗口和步长",
            expanded = isRoomSettingsSectionExpanded(RoomSettingsSectionKey.FILTER),
            onExpandedChange = {
                setRoomSettingsSectionExpanded(RoomSettingsSectionKey.FILTER, it)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Deduplicate switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "重复弹幕过滤", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(text = "同一用户在最近若干条内重复刷同一句时只显示一次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = danmuDedupeEnable,
                        onCheckedChange = { viewModel.setDanmuDedupeEnable(it) }
                    )
                }

                if (danmuDedupeEnable) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Strict deduplication mode switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "严格去重模式", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(text = "全局过滤不同观众发送的相同弹幕（不区分发送人）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = danmuDedupeStrictMode,
                            onCheckedChange = { viewModel.setDanmuDedupeStrictMode(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Deduplicate window slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "过滤窗口", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(text = "${danmuDedupeWindow} 条", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        RoomSettingsSlider(
                            value = danmuDedupeWindow.toFloat(),
                            onValueChange = { viewModel.setDanmuDedupeWindow(it.toInt()) },
                            valueRange = 1f..100f,
                            steps = 98,
                            modifier = Modifier.semantics { contentDescription = "过滤窗口" }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "过滤步长", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(text = "$danmuDedupeStep 条", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        RoomSettingsSlider(
                            value = danmuDedupeStep.toFloat(),
                            onValueChange = { viewModel.setDanmuDedupeStep(it.toInt()) },
                            valueRange = 1f..20f,
                            steps = 18,
                            modifier = Modifier.semantics { contentDescription = "过滤步长" }
                        )
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.RoomSettingsCollapsibleSection(
    listState: LazyListState,
    sectionKey: RoomSettingsSectionKey,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    stickyHeader(key = sectionKey.name, contentType = "settings_header") { headerIndex ->
        val motionPolicy = LocalAppMotionPolicy.current
        val pinned by remember(listState, headerIndex, expanded) {
            derivedStateOf {
                expanded && (listState.firstVisibleItemIndex > headerIndex ||
                    (listState.firstVisibleItemIndex == headerIndex && listState.firstVisibleItemScrollOffset > 0))
            }
        }
        val arrowRotation by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = motionPolicy.contentSpec(),
            label = "roomSettingsSectionArrow_${sectionKey.name}"
        )
        // Include the opaque gutter in the pinned item so following cards cannot touch it
        // or show through it while they scroll underneath.
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(bottom = 12.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = if (pinned) 2.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .testTag("room_settings_section_${sectionKey.name}")
                        .clickable(role = Role.Button, onClickLabel = if (expanded) "收起$title" else "展开$title") {
                            onExpandedChange(!expanded)
                        }
                        .semantics { heading(); stateDescription = if (expanded) "已展开" else "已收起" }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Text(if (pinned) "当前分组 · 点击收起" else subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起$title" else "展开$title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = arrowRotation })
                }
            }
        }
    }
    item(key = "${sectionKey.name}_content", contentType = "settings_content") {
        val motionPolicy = LocalAppMotionPolicy.current
        AnimatedVisibility(
            visible = expanded,
            enter = if (motionPolicy.reducedMotion) fadeIn(motionPolicy.effectsSpec()) else
                expandVertically(motionPolicy.contentSpec(), expandFrom = Alignment.Top) + fadeIn(motionPolicy.effectsSpec()),
            exit = if (motionPolicy.reducedMotion) fadeOut(motionPolicy.effectsSpec()) else
                shrinkVertically(motionPolicy.contentSpec(), shrinkTowards = Alignment.Top) + fadeOut(motionPolicy.effectsSpec())
        ) {
            Column {
                Surface(modifier = Modifier.fillMaxWidth().testTag("room_settings_content_${sectionKey.name}"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface) {
                    Column(content = content)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    // An empty end marker releases the pinned heading at this group's boundary.
    // Collapsed groups therefore scroll as ordinary, separated cards.
    stickyHeader(key = "${sectionKey.name}_end", contentType = "settings_boundary") {
        Spacer(Modifier.fillMaxWidth().height(0.dp))
    }
}

@Composable
private fun RoomSettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun RoomSettingsSliderRow(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Text(text = valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        RoomSettingsSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.semantics { contentDescription = title }
        )
    }
}

private fun roomSettingsNormalizedDanmuSize(value: Double): Double =
    DanmakuSettingsPolicy.sizeMultiplier(value)

private fun roomSettingsNormalizedDanmuSpeed(value: Double): Double =
    DanmakuSettingsPolicy.speedMultiplier(value)

private fun roomSettingsMultiplierText(value: Double): String =
    DanmakuSettingsPolicy.multiplierText(value)

private fun roomSettingsPercentText(value: Double): String =
    DanmakuSettingsPolicy.percentText(value)

private fun roomSettingsStrokeText(value: Double): String =
    DanmakuSettingsPolicy.strokeText(value)

private fun roomSettingsOneDecimal(value: Double): String {
    val rounded = (value * 10).roundToInt()
    return "${rounded / 10}.${kotlin.math.abs(rounded % 10)}"
}

@Composable
internal fun RoomSettingsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
    val thumbRingColor = MaterialTheme.colorScheme.surface
    val valueStart = valueRange.start
    val valueEnd = valueRange.endInclusive
    val rangeSize = (valueEnd - valueStart).takeIf { it > 0f } ?: 1f
    val latestValue by rememberUpdatedState(value.coerceIn(valueStart, valueEnd))
    val commitValue by rememberUpdatedState(onValueChange)
    var pendingValue by remember(valueRange, steps) { mutableFloatStateOf(latestValue) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value, valueRange, steps) {
        if (!dragging) pendingValue = latestValue
    }
    // Keep the preview on release until the persisted value arrives, without snapping back.
    val coercedValue = pendingValue.coerceIn(valueStart, valueEnd)
    val fraction = ((coercedValue - valueStart) / rangeSize).coerceIn(0f, 1f)
    val density = LocalDensity.current
    val horizontalInset = 13.dp
    val horizontalInsetPx = with(density) { horizontalInset.toPx() }
    var widthPx by remember { mutableFloatStateOf(1f) }

    fun valueForPosition(x: Float): Float {
        val usableWidth = (widthPx - horizontalInsetPx * 2f).coerceAtLeast(1f)
        val rawFraction = ((x - horizontalInsetPx) / usableWidth).coerceIn(0f, 1f)
        return roomSettingsSliderValueForFraction(rawFraction, valueRange, steps)
    }

    fun commitPendingValue() {
        if (!dragging) return
        dragging = false
        if (pendingValue != latestValue) commitValue(pendingValue)
    }

    fun cancelPendingValue() {
        dragging = false
        pendingValue = latestValue
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(coercedValue, valueRange, steps)
                setProgress { target ->
                    if (!target.isFinite()) false else {
                        pendingValue = roomSettingsSliderSnapValue(target, valueRange, steps)
                        if (pendingValue != latestValue) commitValue(pendingValue)
                        true
                    }
                }
            }
            .pointerInput(valueRange, steps, widthPx) {
                detectTapGestures { offset ->
                    pendingValue = valueForPosition(offset.x)
                    if (pendingValue != latestValue) commitValue(pendingValue)
                }
            }
            .pointerInput(valueRange, steps, widthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        pendingValue = valueForPosition(offset.x)
                    },
                    onDragEnd = { commitPendingValue() },
                    onDragCancel = { cancelPendingValue() },
                    onHorizontalDrag = { change, _ ->
                        pendingValue = valueForPosition(change.position.x)
                        change.consume()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val startX = horizontalInset.toPx()
            val endX = size.width - horizontalInset.toPx()
            val activeX = startX + (endX - startX) * fraction
            val trackStroke = 6.dp.toPx()
            val thumbRadius = 7.dp.toPx()
            val thumbRingRadius = 10.dp.toPx()
            val thumbHaloRadius = 15.dp.toPx()

            drawLine(
                color = inactiveColor,
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = trackStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = activeColor,
                start = Offset(startX, centerY),
                end = Offset(activeX, centerY),
                strokeWidth = trackStroke,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = activeColor.copy(alpha = 0.16f),
                radius = thumbHaloRadius,
                center = Offset(activeX, centerY)
            )
            drawCircle(
                color = thumbRingColor,
                radius = thumbRingRadius,
                center = Offset(activeX, centerY)
            )
            drawCircle(
                color = activeColor,
                radius = thumbRadius,
                center = Offset(activeX, centerY)
            )
        }
    }
}

private fun roomSettingsSliderSnapValue(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
): Float {
    val valueStart = valueRange.start
    val valueEnd = valueRange.endInclusive
    val rangeSize = valueEnd - valueStart
    if (rangeSize <= 0f) return valueStart
    return roomSettingsSliderValueForFraction(
        rawFraction = ((value - valueStart) / rangeSize).coerceIn(0f, 1f),
        valueRange = valueRange,
        steps = steps
    )
}

private fun roomSettingsSliderValueForFraction(
    rawFraction: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
): Float {
    val valueStart = valueRange.start
    val valueEnd = valueRange.endInclusive
    val rangeSize = valueEnd - valueStart
    if (rangeSize <= 0f) return valueStart

    val fraction = rawFraction.coerceIn(0f, 1f)
    val snappedFraction = if (steps <= 0) {
        fraction
    } else {
        val intervals = (steps + 1).coerceAtLeast(1)
        (fraction * intervals).roundToInt() / intervals.toFloat()
    }
    return valueStart + rangeSize * snappedFraction
}

