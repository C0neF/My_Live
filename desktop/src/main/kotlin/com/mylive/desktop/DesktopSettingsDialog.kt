package com.mylive.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DesktopSettingsDialog(repository: DesktopLibrary, state: DesktopLibraryState, onVolume: (Float) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    fun player(change: (DesktopPlayerPreferences) -> DesktopPlayerPreferences) { scope.launch { repository.updatePlayer(change) } }
    fun danmaku(change: (DesktopDanmakuPreferences) -> DesktopDanmakuPreferences) { scope.launch { repository.updateDanmaku(change) } }
    val p = state.player
    val d = state.danmaku
    val editable = state.writable
    DesktopDialog(onDismissRequest = onDismiss, title = "设置", preferredWidth = 760.dp,
        modifier = Modifier.testTag("settings-dialog"), content = {
            DesktopDialogTabBar(listOf("播放", "弹幕", "屏蔽", "外观", "快捷键"), tab) { tab = it }
            key(tab) {
                DesktopDialogScroll(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                    when (tab) {
                        0 -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    SettingsSlider("音量", p.volume, 0f..100f, editable, onChanging = onVolume) { value -> player { it.copy(volume = value) } }
                                }
                                Column(Modifier.weight(1f)) {
                                    SettingsSlider("聊天字号", p.chatTextSize, 10f..28f, editable) { value -> player { it.copy(chatTextSize = value) } }
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("画面缩放", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DesktopVideoScale.entries.forEach { scale ->
                                            DesktopChoiceChip(
                                                selected = p.scale == scale,
                                                enabled = editable,
                                                onClick = { player { it.copy(scale = scale) } },
                                                label = { Text(when (scale) { DesktopVideoScale.Fit -> "适应"; DesktopVideoScale.Crop -> "裁剪填充"; DesktopVideoScale.Stretch -> "拉伸" }) }
                                            )
                                        }
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("默认清晰度", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SettingsToggle("静音", p.muted, editable, Modifier.weight(1f)) { value -> player { it.copy(muted = value) } }
                                    SettingsToggle("硬件解码", p.hardwareDecode, editable, Modifier.weight(1f)) { value -> player { it.copy(hardwareDecode = value) } }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SettingsToggle("记住各平台选择的清晰度", p.rememberQuality, editable, Modifier.weight(1f)) { value -> player { it.copy(rememberQuality = value) } }
                                    SettingsToggle("显示实时聊天", p.chatVisible, editable, Modifier.weight(1f)) { value -> player { it.copy(chatVisible = value) } }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SettingsToggle("聊天自动滚动", p.chatAutoScroll, editable, Modifier.weight(1f)) { value -> player { it.copy(chatAutoScroll = value) } }
                                    SettingsToggle("显示付费留言", p.superChatVisible, editable, Modifier.weight(1f)) { value -> player { it.copy(superChatVisible = value) } }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SettingsToggle("窗口置顶", p.alwaysOnTop, editable, Modifier.weight(1f)) { value -> player { it.copy(alwaysOnTop = value) } }
                                    SettingsToggle("关闭窗口时最小化到托盘", p.minimizeToTray, editable, Modifier.weight(1f)) { value -> player { it.copy(minimizeToTray = value) } }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SettingsToggle("隐藏窗口后继续播放", p.backgroundPlayback, editable, Modifier.weight(1f)) { value -> player { it.copy(backgroundPlayback = value) } }
                                    SettingsToggle("定时停止播放", p.autoStopEnabled, editable, Modifier.weight(1f)) { value -> player { it.copy(autoStopEnabled = value) } }
                                }
                            }
                            if (p.autoStopEnabled) SettingsSlider("开始观看后停止（分钟）", p.autoStopMinutes.toFloat(), 1f..180f, editable) { value -> player { it.copy(autoStopMinutes = value.roundToInt()) } }
                        }
                        1 -> {
                            SettingsToggle("显示视频弹幕", p.danmakuEnabled, editable) { value -> player { it.copy(danmakuEnabled = value) } }
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    SettingsSlider("字号", d.fontSize, 12f..48f, editable) { value -> danmaku { it.copy(fontSize = value) } }
                                }
                                Column(Modifier.weight(1f)) {
                                    SettingsSlider("通过画面所需秒数", d.travelSeconds, 3f..30f, editable) { value -> danmaku { it.copy(travelSeconds = value) } }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    SettingsSlider("不透明度（%）", d.opacity * 100, 10f..100f, editable) { value -> danmaku { it.copy(opacity = value / 100) } }
                                }
                                Column(Modifier.weight(1f)) {
                                    SettingsSlider("滚动弹幕区域（%）", d.area * 100, 20f..100f, editable) { value -> danmaku { it.copy(area = value / 100) } }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    SettingsSlider("最大同时显示行数", d.lines.toFloat(), 1f..20f, editable) { value -> danmaku { it.copy(lines = value.roundToInt()) } }
                                }
                                if (d.deduplicate) Column(Modifier.weight(1f)) {
                                    SettingsSlider("去重窗口（秒）", d.dedupeSeconds.toFloat(), 1f..120f, editable) { value -> danmaku { it.copy(dedupeSeconds = value.roundToInt()) } }
                                } else Spacer(Modifier.weight(1f))
                            }
                            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SettingsToggle("彩色弹幕", d.colored, editable, Modifier.weight(1f)) { value -> danmaku { it.copy(colored = value) } }
                                    SettingsToggle("显示表情图片", d.renderEmoji, editable, Modifier.weight(1f)) { value -> danmaku { it.copy(renderEmoji = value) } }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SettingsToggle("滚动弹幕", d.scroll, editable, Modifier.weight(1f)) { value -> danmaku { it.copy(scroll = value) } }
                                    SettingsToggle("顶部弹幕", d.top, editable, Modifier.weight(1f)) { value -> danmaku { it.copy(top = value) } }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SettingsToggle("底部弹幕", d.bottom, editable, Modifier.weight(1f)) { value -> danmaku { it.copy(bottom = value) } }
                                    SettingsToggle("重复消息去重", d.deduplicate, editable, Modifier.weight(1f)) { value -> danmaku { it.copy(deduplicate = value) } }
                                }
                                if (d.deduplicate) {
                                    SettingsToggle("仅去重同一用户的相同消息", d.dedupeByUser, editable) { value -> danmaku { it.copy(dedupeByUser = value) } }
                                }
                            }
                        }
                        2 -> {
                            SettingsToggle("启用屏蔽", d.shieldEnabled, editable) { value -> danmaku { it.copy(shieldEnabled = value) } }
                            var keywords by remember(d.keywords) { mutableStateOf(d.keywords.joinToString("\n")) }
                            Text("关键词（每行一个，不区分大小写）", color = UiMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                            OutlinedTextField(keywords, { keywords = it }, label = { Text("关键词（每行一个）") }, minLines = 3, enabled = editable, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                            DesktopPrimaryButton(enabled = editable && keywords.lines().all { it.length <= 200 }, modifier = Modifier.padding(top = 10.dp), onClick = {
                                val values = keywords.lines().map(String::trim).filter(String::isNotBlank).distinct()
                                danmaku { it.copy(keywords = values) }
                            }) { Text("保存关键词") }
                            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = UiHairline)
                            var platform by remember { mutableStateOf("__all__") }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                mapOf("__all__" to "全部平台", "douyu" to "斗鱼", "huya" to "虎牙", "bilibili" to "哔哩哔哩", "douyin" to "抖音").forEach { (id, name) ->
                                    DesktopChoiceChip(platform == id, onClick = { platform = id }, label = { Text(name) })
                                }
                            }
                            var users by remember(platform, d.users) { mutableStateOf(d.users.filter { it.siteId == platform }.joinToString("\n") { it.userName }) }
                            Text("屏蔽昵称（每行一个，精确匹配）", color = UiMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                            OutlinedTextField(users, { users = it }, placeholder = { Text("输入要屏蔽的昵称，每行一个") }, minLines = 3, enabled = editable, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                            DesktopPrimaryButton(enabled = editable && users.lines().all { it.length <= 100 }, modifier = Modifier.padding(top = 10.dp), onClick = {
                                val values = users.lines().map(String::trim).filter(String::isNotBlank).distinct().map { DesktopBlockedUser(platform, it) }
                                danmaku { it.copy(users = it.users.filterNot { user -> user.siteId == platform } + values) }
                            }) { Text("保存屏蔽用户") }
                        }
                        3 -> AppearanceSettings(state.appearance, editable) { change -> scope.launch { repository.updateAppearance(change) } }
                        else -> Text("Ctrl+L：聚焦房间入口\nF10：全区域 / 正常布局\n双击画面 / F11：切换全屏\n空格：播放 / 暂停\nM：静音 / 取消静音\n↑ / ↓：调整音量\nP：小窗 / 普通窗口\nT：窗口置顶\nEsc：退出全屏、全区域或取消输入焦点\n\nCtrl+L 会先退出全屏、小窗和全区域，再聚焦输入框。\n编辑文本或打开设置时，播放、音量和小窗快捷键暂停生效。", lineHeight = 28.sp)
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp), fontSize = 12.sp) }
                }
            }
    }, confirmButton = { DesktopOutlinedButton(onClick = onDismiss) { Text("完成") } })
}

private val SettingsToggleShape = RoundedCornerShape(9.dp)

@Composable
internal fun SettingsToggle(label: String, checked: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hoveredState = interaction.collectIsHoveredAsState()
    val pressedState = interaction.collectIsPressedAsState()
    val accent = UiAccent
    val baseBorder = if (checked) accent.copy(alpha = .32f) else UiHairline
    val hoverBorder = if (checked) accent.copy(alpha = .45f) else UiHairlineStrong
    CompositionLocalProvider(LocalContentColor provides if (enabled) UiInk else UiMuted.copy(alpha = .42f)) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .desktopInteractiveSurface(
                    shape = SettingsToggleShape,
                    hoveredState = hoveredState,
                    pressedState = pressedState,
                    enabled = enabled,
                    baseColor = UiSurface,
                    hoverColor = UiElevated,
                    pressColor = accent.copy(alpha = .10f),
                    baseBorderColor = baseBorder,
                    hoverBorderColor = hoverBorder,
                    pressBorderColor = hoverBorder
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Switch,
                    onClick = { onChange(!checked) }
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f).padding(end = 8.dp, bottom = 1.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabled,
                modifier = Modifier.scale(0.80f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = accent,
                    checkedBorderColor = accent,
                    uncheckedThumbColor = UiMuted,
                    uncheckedTrackColor = UiElevated,
                    uncheckedBorderColor = UiHairlineStrong,
                    disabledCheckedTrackColor = accent.copy(alpha = .35f),
                    disabledUncheckedTrackColor = UiSurface.copy(alpha = .5f)
                )
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsSlider(label: String, current: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    onChanging: (Float) -> Unit = {}, onCommit: (Float) -> Unit) {
    var value by remember(current) { mutableFloatStateOf(current) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    .background(if (enabled) UiAccent else UiMuted, CircleShape)
                    .border(2.dp, UiElevated, CircleShape)
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(6.dp),
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
                colors = SliderDefaults.colors(
                    activeTrackColor = UiAccent,
                    inactiveTrackColor = UiSurface,
                    disabledActiveTrackColor = UiAccent.copy(alpha = .4f),
                    disabledInactiveTrackColor = UiSurface.copy(alpha = .5f)
                )
            )
        }
    )
}
