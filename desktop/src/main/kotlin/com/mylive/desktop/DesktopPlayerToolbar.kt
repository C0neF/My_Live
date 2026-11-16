package com.mylive.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.core.model.LivePlayQuality

internal data class DesktopPlayerToolbarState(
    val playing: Boolean = true, val volume: Float = 70f, val muted: Boolean = false,
    val fullscreen: Boolean = false, val mini: Boolean = false, val chatVisible: Boolean = true,
    val danmaku: Boolean = true, val showInfo: Boolean = false,
    val quality: LivePlayQuality? = null, val qualities: List<LivePlayQuality> = emptyList(),
    val line: Int = 0, val lineCount: Int = 0, val chatAvailable: Boolean = true,
    val fullArea: Boolean = false
)

internal data class DesktopPlayerToolbarActions(
    val playPause: () -> Unit, val mute: () -> Unit,
    val volume: (Float) -> Unit, val saveVolume: (Float) -> Unit,
    val quality: (LivePlayQuality) -> Unit, val nextLine: () -> Unit,
    val chat: () -> Unit, val danmaku: () -> Unit, val info: () -> Unit,
    val mini: () -> Unit, val fullscreen: () -> Unit, val settings: () -> Unit,
    val fullArea: () -> Unit
)

@Composable
internal fun DesktopPlayerToolbar(state: DesktopPlayerToolbarState, actions: DesktopPlayerToolbarActions,
    modifier: Modifier = Modifier, diagnostics: String? = null, onMenuHold: (Boolean) -> Unit = {}) {
    var qualityMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var dragVolume by remember(state.volume) { mutableFloatStateOf(state.volume) }
    fun changeVolume(value: Float) { dragVolume = value; actions.volume(value) }
    val latestHold by rememberUpdatedState(onMenuHold)
    LaunchedEffect(qualityMenu, moreMenu, state.showInfo) { latestHold(qualityMenu || moreMenu || state.showInfo) }
    DisposableEffect(Unit) { onDispose { latestHold(false) } }
    BoxWithConstraints(modifier.testTag("player-toolbar").background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .92f))))) {
        val wide = maxWidth >= 960.dp && !state.mini
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (state.showInfo && diagnostics != null) Text(diagnostics, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DesktopIconButton(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playing) "暂停" else "播放", onClick = actions.playPause)
                DesktopIconButton(if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    if (state.muted) "取消静音 M" else "静音 M", selected = state.muted, onClick = actions.mute)
                if (wide) Slider(dragVolume, ::changeVolume, valueRange = 0f..100f, onValueChangeFinished = { actions.saveVolume(dragVolume) }, modifier = Modifier.width(96.dp).padding(horizontal = 6.dp))
                Spacer(Modifier.weight(1f))
                if (!state.mini && state.qualities.isNotEmpty()) Box {
                    PlayerToolbarChipButton(onClick = { qualityMenu = true }, modifier = Modifier.widthIn(max = if (wide) 160.dp else 116.dp)) {
                        Text(state.quality?.quality ?: "清晰度", color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ExpandMore, null, Modifier.padding(start = 2.dp).size(16.dp), tint = Color.White)
                    }
                    DropdownMenu(qualityMenu, { qualityMenu = false }) {
                        state.qualities.forEach { quality -> DropdownMenuItem(text = { Text(quality.quality) },
                            leadingIcon = { if (quality.quality == state.quality?.quality) Icon(Icons.Default.Check, null, Modifier.size(17.dp)) },
                            onClick = { qualityMenu = false; actions.quality(quality) }) }
                    }
                }
                if (wide) {
                    if (state.lineCount > 1) PlayerToolbarChipButton(onClick = actions.nextLine) {
                        Text("线路 ${state.line + 1}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                    DesktopIconButton(Icons.Default.Subtitles, "显示或隐藏弹幕", selected = state.danmaku, onClick = actions.danmaku)
                    DesktopIconButton(Icons.Default.Info, "播放信息", selected = state.showInfo, onClick = actions.info)
                }
                if (state.chatAvailable && !state.fullscreen && !state.mini && !state.fullArea) DesktopIconButton(Icons.Default.ChatBubbleOutline, "显示或隐藏聊天", selected = state.chatVisible, onClick = actions.chat)
                Box {
                    DesktopIconButton(Icons.Default.MoreHoriz, "更多播放选项") { moreMenu = true }
                    DropdownMenu(moreMenu, { moreMenu = false }, Modifier.width(250.dp).heightIn(max = 430.dp)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("音量 ${dragVolume.toInt()}%", style = MaterialTheme.typography.labelMedium)
                            Slider(dragVolume, ::changeVolume, valueRange = 0f..100f, onValueChangeFinished = { actions.saveVolume(dragVolume) })
                        }
                        HorizontalDivider()
                        if (state.mini && state.qualities.isNotEmpty()) {
                            Text("清晰度", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                            state.qualities.forEach { quality -> DropdownMenuItem(text = { Text(quality.quality) },
                                leadingIcon = { if (quality.quality == state.quality?.quality) Icon(Icons.Default.Check, null, Modifier.size(17.dp)) },
                                onClick = { moreMenu = false; actions.quality(quality) }) }
                            HorizontalDivider()
                        }
                        if (state.lineCount > 1) DropdownMenuItem(text = { Text("切换线路 · ${state.line + 1}/${state.lineCount}") },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) }, onClick = { moreMenu = false; actions.nextLine() })
                        DropdownMenuItem(text = { Text(if (state.danmaku) "关闭弹幕" else "打开弹幕") }, leadingIcon = { Icon(Icons.Default.Subtitles, null) }, onClick = { moreMenu = false; actions.danmaku() })
                        DropdownMenuItem(text = { Text("播放信息") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { moreMenu = false; actions.info() })
                        DropdownMenuItem(text = { Text(if (state.mini) "返回普通窗口" else "小窗播放") }, leadingIcon = { Icon(Icons.Default.PictureInPictureAlt, null) }, onClick = { moreMenu = false; actions.mini() })
                        DropdownMenuItem(text = { Text(if (state.fullArea) "退出全区域" else "全区域（填满窗口）") }, leadingIcon = { Icon(Icons.Default.FitScreen, null) }, onClick = { moreMenu = false; actions.fullArea() })
                        DropdownMenuItem(text = { Text("播放设置") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { moreMenu = false; actions.settings() })
                    }
                }
                if (wide || state.mini) DesktopIconButton(if (state.mini) Icons.Default.OpenInFull else Icons.Default.PictureInPictureAlt,
                    if (state.mini) "返回普通窗口 P" else "小窗 P", onClick = actions.mini)
                if (!state.mini) DesktopIconButton(Icons.Default.FitScreen,
                    if (state.fullArea) "退出全区域 F10" else "全区域 F10", selected = state.fullArea,
                    modifier = Modifier.testTag("toggle-full-area"), onClick = actions.fullArea)
                DesktopIconButton(if (state.fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    if (state.fullscreen) "退出全屏" else "全屏 F11", onClick = actions.fullscreen)
            }
        }
    }
}

private val PlayerChipShape = RoundedCornerShape(9.dp)

@Composable
private fun PlayerToolbarChipButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hoveredState = interaction.collectIsHoveredAsState()
    val pressedState = interaction.collectIsPressedAsState()
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Row(
            modifier = modifier
                .height(34.dp)
                .desktopInteractiveSurface(
                    shape = PlayerChipShape,
                    hoveredState = hoveredState,
                    pressedState = pressedState,
                    baseColor = Color.White.copy(alpha = .10f),
                    hoverColor = Color.White.copy(alpha = .16f),
                    pressColor = Color.White.copy(alpha = .22f),
                    baseBorderColor = Color.White.copy(alpha = .20f),
                    hoverBorderColor = Color.White.copy(alpha = .32f),
                    pressBorderColor = Color.White.copy(alpha = .40f)
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(horizontal = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
