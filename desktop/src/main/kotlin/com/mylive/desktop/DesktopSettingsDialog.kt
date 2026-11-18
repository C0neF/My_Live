package com.mylive.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private data class DesktopSettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color
)

private val DesktopSettingsCategories = listOf(
    DesktopSettingsCategory("播放", "音频、画质与窗口行为", Icons.Default.PlayCircleOutline, Color(0xff007aff)),
    DesktopSettingsCategory("弹幕", "字号、速度与防遮挡", Icons.Default.Subtitles, Color(0xff34c759)),
    DesktopSettingsCategory("屏蔽", "关键词与按平台过滤", Icons.Default.Block, Color(0xffff3b30)),
    DesktopSettingsCategory("外观", "主题、强调色与平台排序", Icons.Default.Palette, Color(0xffaf52de)),
    DesktopSettingsCategory("快捷键", "全局与直播间键盘操作", Icons.Default.Keyboard, Color(0xff8e8e93))
)

@Composable
fun DesktopSettingsDialog(
    repository: DesktopLibrary,
    state: DesktopLibraryState,
    onVolume: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    fun player(change: (DesktopPlayerPreferences) -> DesktopPlayerPreferences) {
        scope.launch { repository.updatePlayer(change) }
    }
    fun danmaku(change: (DesktopDanmakuPreferences) -> DesktopDanmakuPreferences) {
        scope.launch { repository.updateDanmaku(change) }
    }
    val p = state.player
    val d = state.danmaku
    val editable = state.writable

    DesktopDialog(
        onDismissRequest = onDismiss,
        title = "偏好设置",
        preferredWidth = 840.dp,
        modifier = Modifier.testTag("settings-dialog"),
        content = {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val twoPane = maxWidth >= 620.dp
                val detailContent: @Composable (Modifier) -> Unit = { scrollModifier ->
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { desktopDirectionalTabTransform(targetState.compareTo(initialState)) },
                        modifier = scrollModifier
                    ) { activeTab ->
                        DesktopDialogScroll(Modifier.fillMaxSize()) {
                            val currentCategory = DesktopSettingsCategories[activeTab]
                            Text(
                                currentCategory.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = UiMuted,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            when (activeTab) {
                                0 -> {
                                    SettingsSectionCard("音量与聊天显示") {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Column(Modifier.weight(1f)) {
                                                SettingsSlider("音量", p.volume, 0f..100f, editable, onChanging = onVolume) { value ->
                                                    player { it.copy(volume = value) }
                                                }
                                            }
                                            Column(Modifier.weight(1f)) {
                                                SettingsSlider("聊天字号", p.chatTextSize, 10f..28f, editable) { value ->
                                                    player { it.copy(chatTextSize = value) }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    SettingsSectionCard("画面与默认画质") {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Column(Modifier.weight(1f)) {
                                                Text("画面缩放", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
                                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    DesktopVideoScale.entries.forEach { scale ->
                                                        DesktopChoiceChip(
                                                            selected = p.scale == scale,
                                                            enabled = editable,
                                                            onClick = { player { it.copy(scale = scale) } },
                                                            label = {
                                                                Text(
                                                                    when (scale) {
                                                                        DesktopVideoScale.Fit -> "适应"
                                                                        DesktopVideoScale.Crop -> "裁剪填充"
                                                                        DesktopVideoScale.Stretch -> "拉伸"
                                                                    }
                                                                )
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text("默认清晰度", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
                                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    listOf("最高", "均衡", "省流").forEachIndexed { index, title ->
                                                        DesktopChoiceChip(
                                                            selected = p.qualityLevel == index,
                                                            enabled = editable,
                                                            onClick = { player { it.copy(qualityLevel = index) } },
                                                            label = { Text(title) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    SettingsSectionCard("播放与窗口选项") {
                                        Column {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                SettingsToggle("静音", p.muted, editable, Modifier.weight(1f)) { value -> player { it.copy(muted = value) } }
                                                SettingsToggle("硬件解码", p.hardwareDecode, editable, Modifier.weight(1f)) { value -> player { it.copy(hardwareDecode = value) } }
                                            }
                                            SettingsFormDivider()
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                SettingsToggle("记住各平台选择的清晰度", p.rememberQuality, editable, Modifier.weight(1f)) { value -> player { it.copy(rememberQuality = value) } }
                                                SettingsToggle("显示实时聊天", p.chatVisible, editable, Modifier.weight(1f)) { value -> player { it.copy(chatVisible = value) } }
                                            }
                                            SettingsFormDivider()
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                SettingsToggle("聊天自动滚动", p.chatAutoScroll, editable, Modifier.weight(1f)) { value -> player { it.copy(chatAutoScroll = value) } }
                                                SettingsToggle("显示付费留言", p.superChatVisible, editable, Modifier.weight(1f)) { value -> player { it.copy(superChatVisible = value) } }
                                            }
                                            SettingsFormDivider()
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                SettingsToggle("窗口置顶", p.alwaysOnTop, editable, Modifier.weight(1f)) { value -> player { it.copy(alwaysOnTop = value) } }
                                                SettingsToggle("关闭窗口时最小化到托盘", p.minimizeToTray, editable, Modifier.weight(1f)) { value -> player { it.copy(minimizeToTray = value) } }
                                            }
                                            SettingsFormDivider()
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                SettingsToggle("隐藏窗口后继续播放", p.backgroundPlayback, editable, Modifier.weight(1f)) { value -> player { it.copy(backgroundPlayback = value) } }
                                                SettingsToggle("定时停止播放", p.autoStopEnabled, editable, Modifier.weight(1f)) { value -> player { it.copy(autoStopEnabled = value) } }
                                            }
                                            AnimatedVisibility(
                                                visible = p.autoStopEnabled,
                                                enter = desktopExpandEnter(),
                                                exit = desktopCollapseExit()
                                            ) {
                                                Column {
                                                    SettingsFormDivider()
                                                    SettingsSlider("开始观看后停止（分钟）", p.autoStopMinutes.toFloat(), 1f..180f, editable) { value ->
                                                        player { it.copy(autoStopMinutes = value.roundToInt()) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    SettingsSectionCard("弹幕总开关与参数") {
                                        SettingsToggle("显示视频弹幕", p.danmakuEnabled, editable) { value -> player { it.copy(danmakuEnabled = value) } }
                                        SettingsFormDivider()
                                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Column(Modifier.weight(1f)) {
                                                SettingsSlider("字号", d.fontSize, 12f..48f, editable) { value -> danmaku { it.copy(fontSize = value) } }
                                            }
                                            Column(Modifier.weight(1f)) {
                                                SettingsSlider("通过画面所需秒数", d.travelSeconds, 3f..30f, editable) { value -> danmaku { it.copy(travelSeconds = value) } }
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Column(Modifier.weight(1f)) {
                                                SettingsSlider("不透明度（%）", d.opacity * 100, 10f..100f, editable) { value -> danmaku { it.copy(opacity = value / 100) } }
                                            }
                                            Column(Modifier.weight(1f)) {
                                                SettingsSlider("滚动弹幕区域（%）", d.area * 100, 20f..100f, editable) { value -> danmaku { it.copy(area = value / 100) } }
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Column(Modifier.weight(1f)) {
                                                SettingsSlider("最大同时显示行数", d.lines.toFloat(), 1f..20f, editable) { value -> danmaku { it.copy(lines = value.roundToInt()) } }
                                            }
                                            Column(Modifier.weight(1f)) {
                                                AnimatedVisibility(
                                                    visible = d.deduplicate,
                                                    enter = desktopExpandEnter(),
                                                    exit = desktopCollapseExit()
                                                ) {
                                                    Column {
                                                        SettingsSlider("去重窗口（秒）", d.dedupeSeconds.toFloat(), 1f..120f, editable) { value -> danmaku { it.copy(dedupeSeconds = value.roundToInt()) } }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    SettingsSectionCard("弹幕类型与去重") {
                                        Column {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                SettingsToggle("彩色弹幕", d.colored, editable, Modifier.weight(1f)) { value -> danmaku { it.copy(colored = value) } }
                                                SettingsToggle("显示表情图片", d.renderEmoji, editable, Modifier.weight(1f)) { value -> danmaku { it.copy(renderEmoji = value) } }
                                            }
                                            SettingsFormDivider()
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                SettingsToggle("滚动弹幕", d.scroll, editable, Modifier.weight(1f)) { value -> danmaku { it.copy(scroll = value) } }
                                                SettingsToggle("顶部弹幕", d.top, editable, Modifier.weight(1f)) { value -> danmaku { it.copy(top = value) } }
                                            }
                                            SettingsFormDivider()
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                SettingsToggle("底部弹幕", d.bottom, editable, Modifier.weight(1f)) { value -> danmaku { it.copy(bottom = value) } }
                                                SettingsToggle("重复消息去重", d.deduplicate, editable, Modifier.weight(1f)) { value -> danmaku { it.copy(deduplicate = value) } }
                                            }
                                            AnimatedVisibility(
                                                visible = d.deduplicate,
                                                enter = desktopExpandEnter(),
                                                exit = desktopCollapseExit()
                                            ) {
                                                Column {
                                                    SettingsFormDivider()
                                                    SettingsToggle("仅去重同一用户的相同消息", d.dedupeByUser, editable) { value -> danmaku { it.copy(dedupeByUser = value) } }
                                                }
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    SettingsSectionCard("关键词过滤") {
                                        SettingsToggle("启用屏蔽", d.shieldEnabled, editable) { value -> danmaku { it.copy(shieldEnabled = value) } }
                                        SettingsFormDivider()
                                        var keywords by remember(d.keywords) { mutableStateOf(d.keywords.joinToString("\n")) }
                                        Text(
                                            "每行一个关键词，不区分大小写",
                                            color = UiMuted,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                                        )
                                        DesktopTextField(
                                            value = keywords,
                                            onValueChange = { keywords = it },
                                            label = { Text("关键词（每行一个）") },
                                            minLines = 3,
                                            enabled = editable,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        DesktopPrimaryButton(
                                            enabled = editable && keywords.lines().all { it.length <= 200 },
                                            modifier = Modifier.padding(top = 10.dp),
                                            onClick = {
                                                val values = keywords.lines().map(String::trim).filter(String::isNotBlank).distinct()
                                                danmaku { it.copy(keywords = values) }
                                            }
                                        ) { Text("保存关键词") }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    SettingsSectionCard("用户屏蔽名单") {
                                        var platform by remember { mutableStateOf("__all__") }
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            mapOf(
                                                "__all__" to "全部平台",
                                                "douyu" to "斗鱼",
                                                "huya" to "虎牙",
                                                "bilibili" to "哔哩哔哩",
                                                "douyin" to "抖音"
                                            ).forEach { (id, name) ->
                                                DesktopChoiceChip(platform == id, onClick = { platform = id }, label = { Text(name) })
                                            }
                                        }
                                        var users by remember(platform, d.users) {
                                            mutableStateOf(d.users.filter { it.siteId == platform }.joinToString("\n") { it.userName })
                                        }
                                        Text(
                                            "屏蔽昵称（每行一个，精确匹配）",
                                            color = UiMuted,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                                        )
                                        DesktopTextField(
                                            value = users,
                                            onValueChange = { users = it },
                                            placeholder = { Text("输入要屏蔽的昵称，每行一个") },
                                            minLines = 3,
                                            enabled = editable,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        DesktopPrimaryButton(
                                            enabled = editable && users.lines().all { it.length <= 100 },
                                            modifier = Modifier.padding(top = 10.dp),
                                            onClick = {
                                                val values = users.lines().map(String::trim).filter(String::isNotBlank).distinct().map {
                                                    DesktopBlockedUser(platform, it)
                                                }
                                                danmaku { it.copy(users = it.users.filterNot { user -> user.siteId == platform } + values) }
                                            }
                                        ) { Text("保存屏蔽用户") }
                                    }
                                }
                                3 -> AppearanceSettings(state.appearance, editable) { change ->
                                    scope.launch { repository.updateAppearance(change) }
                                }
                                else -> {
                                    SettingsSectionCard("键盘快捷指令") {
                                        val shortcuts = listOf(
                                            "Ctrl + L" to "聚焦房间入口（自动退出全屏/小窗/全区域）",
                                            "F5 / Ctrl + R" to "刷新当前页面列表或关注状态",
                                            "F10" to "切换全区域模式 / 正常布局",
                                            "F11 / 双击画面" to "切换全屏播放",
                                            "Space" to "播放 / 暂停",
                                            "M" to "静音 / 取消静音",
                                            "↑ / ↓" to "增减播放器音量",
                                            "P" to "切换小窗 / 普通窗口",
                                            "T" to "切换窗口置顶",
                                            "Esc" to "退出全屏、小窗、全区域或取消输入焦点"
                                        )
                                        Column {
                                            shortcuts.forEachIndexed { index, (keys, desc) ->
                                                Row(
                                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(7.dp),
                                                        color = UiSurface,
                                                        border = BorderStroke(DesktopHairlineWidth, UiHairlineStrong),
                                                        modifier = Modifier.widthIn(min = 128.dp)
                                                    ) {
                                                        Text(
                                                            keys,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = UiInk,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                    Text(desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                                }
                                                if (index < shortcuts.lastIndex) {
                                                    SettingsFormDivider()
                                                }
                                            }
                                        }
                                        Text(
                                            "编辑文本或打开设置对话框时，播放、音量和小窗单键快捷键会自动暂停生效。",
                                            color = UiMuted,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(top = 12.dp)
                                        )
                                    }
                                }
                            }
                            state.error?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 12.dp),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                if (twoPane) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Surface(
                            modifier = Modifier.width(172.dp).fillMaxHeight(),
                            shape = RoundedCornerShape(13.dp),
                            color = UiFieldFill.copy(alpha = .75f),
                            border = BorderStroke(DesktopHairlineWidth, UiHairline)
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                DesktopSettingsCategories.forEachIndexed { index, category ->
                                    DesktopSettingsRailItem(
                                        category = category,
                                        selected = tab == index,
                                        onClick = { tab = index }
                                    )
                                }
                            }
                        }
                        detailContent(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        DesktopDialogTabBar(DesktopSettingsCategories.map { it.title }, tab) { tab = it }
                        detailContent(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = { DesktopOutlinedButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun DesktopSettingsRailItem(
    category: DesktopSettingsCategory,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember(category.title) { MutableInteractionSource() }
    val hoveredState = interaction.collectIsHoveredAsState()
    val pressedState = interaction.collectIsPressedAsState()
    val focusedState = interaction.collectIsFocusedAsState()
    val accent = UiAccent
    val isDark = MaterialTheme.colorScheme.background.luminance() < .5f
    val selectedFill = if (isDark) Color(0xff343439) else Color.White
    val baseFill by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) selectedFill else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 170, easing = DesktopStandardEasing)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .desktopInteractiveSurface(
                shape = RoundedCornerShape(9.dp),
                hoveredState = hoveredState,
                pressedState = pressedState,
                focusedState = focusedState,
                baseColor = baseFill,
                hoverColor = if (selected) selectedFill else UiInk.copy(alpha = .06f),
                pressColor = if (selected) selectedFill else UiInk.copy(alpha = .10f),
                baseBorderColor = if (selected) UiHairline.copy(alpha = .7f) else Color.Transparent,
                hoverBorderColor = if (selected) UiHairline else Color.Transparent,
                focusBorderColor = accent
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(category.tint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                category.icon,
                null,
                Modifier.size(14.dp),
                tint = Color.White
            )
        }
        Text(
            category.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) UiInk else UiInk.copy(alpha = .85f),
            maxLines = 1
        )
    }
}

@Composable
internal fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = UiMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = UiFieldFill.copy(alpha = .58f),
            border = BorderStroke(DesktopHairlineWidth, UiHairline)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                content()
            }
        }
    }
}

@Composable
internal fun SettingsFormDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        thickness = DesktopHairlineWidth,
        color = UiHairline
    )
}

private val SettingsToggleShape = RoundedCornerShape(8.dp)

@Composable
internal fun SettingsToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hoveredState = interaction.collectIsHoveredAsState()
    val pressedState = interaction.collectIsPressedAsState()
    val focusedState = interaction.collectIsFocusedAsState()
    val accent = UiAccent
    CompositionLocalProvider(LocalContentColor provides if (enabled) UiInk else UiMuted.copy(alpha = .42f)) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .desktopInteractiveSurface(
                    shape = SettingsToggleShape,
                    hoveredState = hoveredState,
                    pressedState = pressedState,
                    focusedState = focusedState,
                    enabled = enabled,
                    baseColor = Color.Transparent,
                    hoverColor = UiInk.copy(alpha = .04f),
                    pressColor = UiInk.copy(alpha = .08f),
                    focusBorderColor = accent
                )
                .toggleable(
                    value = checked,
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onChange
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            DesktopSwitch(checked = checked, enabled = enabled)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsSlider(
    label: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChanging: (Float) -> Unit = {},
    onCommit: (Float) -> Unit
) {
    var value by remember(current) { mutableFloatStateOf(current) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("${value.roundToInt()}", style = MaterialTheme.typography.labelLarge, color = UiAccent)
    }
    Slider(
        value = value,
        onValueChange = { value = it; onChanging(it) },
        enabled = enabled,
        valueRange = range,
        onValueChangeFinished = { onCommit(value) },
        modifier = Modifier.fillMaxWidth().height(24.dp),
        thumb = {
            Box(
                Modifier
                    .size(15.dp)
                    .background(Color.White, CircleShape)
                    .border(1.5.dp, if (enabled) UiAccent else UiMuted, CircleShape)
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(5.dp),
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
                colors = SliderDefaults.colors(
                    activeTrackColor = UiAccent,
                    inactiveTrackColor = UiInk.copy(alpha = .12f),
                    disabledActiveTrackColor = UiAccent.copy(alpha = .4f),
                    disabledInactiveTrackColor = UiInk.copy(alpha = .06f)
                )
            )
        }
    )
}
