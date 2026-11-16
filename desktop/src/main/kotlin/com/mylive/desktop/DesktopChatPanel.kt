package com.mylive.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.core.model.*
import kotlinx.coroutines.launch

@Composable
internal fun DesktopChatPanel(messages: List<ChatEntry>, paid: List<LiveSuperChatMessage>, status: String,
    preferences: DesktopPlayerPreferences, filter: DesktopDanmakuPreferences, siteId: String,
    onAutoScroll: (Boolean) -> Unit, modifier: Modifier = Modifier, onClose: () -> Unit) {
    val visible = remember(messages, filter, siteId) { messages.filter { filter.allows(it.message, siteId) } }
    val visiblePaid = remember(paid, filter, siteId) { paid.filter { filter.allows(LiveMessage(LiveMessageType.SUPER_CHAT, it.userName, it.message), siteId) } }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var following by remember { mutableStateOf(true) }
    var lastSeen by remember { mutableLongStateOf(0L) }
    val scrollbar = remember { MutableInteractionSource() }
    val dragging by scrollbar.collectIsDraggedAsState()
    val userScroll = remember(list) { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y > 0) following = false
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && !list.canScrollForward) following = true
            return Offset.Zero
        }
    } }
    LaunchedEffect(dragging) { if (dragging) following = false else if (!list.canScrollForward) following = true }
    LaunchedEffect(preferences.chatAutoScroll) { if (preferences.chatAutoScroll) following = true }
    LaunchedEffect(visible.isEmpty()) { if (visible.isEmpty()) { following = true; lastSeen = 0 } }
    LaunchedEffect(visible.lastOrNull()?.id, preferences.chatAutoScroll, following) {
        if (preferences.chatAutoScroll && following && visible.isNotEmpty()) { list.scrollToItem(visible.lastIndex); lastSeen = visible.last().id }
    }
    Surface(modifier, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, UiHairlineStrong), color = UiSurface) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("实时聊天", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Box(Modifier.size(5.dp).background(if (status == "已连接") UiAccent else UiMuted, CircleShape))
                        Text(status, style = MaterialTheme.typography.labelSmall, color = UiMuted, maxLines = 2)
                    }
                }
                DesktopIconButton(Icons.Default.Close, "关闭聊天", onClick = onClose)
            }
            HorizontalDivider(color = UiHairline)
            if (preferences.superChatVisible && visiblePaid.isNotEmpty()) {
                Text("付费留言", fontSize = 11.sp, color = UiAccent, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp))
                LazyColumn(Modifier.heightIn(max = 132.dp).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(visiblePaid, key = { it.id ?: "${it.userName}:${it.startTime}:${it.message}" }) { message ->
                        Surface(color = UiAccent.copy(alpha = .1f), shape = RoundedCornerShape(8.dp)) {
                            SelectionContainer { Text("¥${message.price} · ${message.userName}\n${message.message}", fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(9.dp)) }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 12.dp)) {
                if (visible.isEmpty()) Column(Modifier.align(Alignment.Center).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(28.dp), tint = UiMuted.copy(alpha = .5f))
                    Text(if (messages.isNotEmpty()) "消息已被屏蔽规则过滤" else "暂无聊天消息", color = UiMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                }
                SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp).testTag("chat-list").nestedScroll(userScroll).onPreviewKeyEvent { event ->
                        val delta = when (event.key) { Key.PageUp -> -300f; Key.PageDown -> 300f; Key.DirectionUp -> -40f; Key.DirectionDown -> 40f; else -> null }
                        if (event.type == KeyEventType.KeyDown && delta != null && !event.isCtrlPressed && !event.isAltPressed) {
                            following = false
                            scope.launch { list.animateScrollBy(delta); if (!list.canScrollForward) following = true }
                            true
                        } else false
                    }, state = list, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        items(visible, key = { it.id }) { entry -> Column {
                            Text(entry.message.userName, color = UiAccent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
                            DesktopMessageText(entry.message, preferences.chatTextSize.sp, images = filter.renderEmoji)
                        } }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight().testTag("chat-scrollbar"),
                    interactionSource = scrollbar, style = defaultScrollbarStyle().copy(thickness = 5.dp, unhoverColor = UiMuted.copy(alpha = .2f), hoverColor = UiMuted.copy(alpha = .6f)))
            }
            if ((!following || !preferences.chatAutoScroll) && list.canScrollForward) DesktopTonalButton(onClick = {
                following = true
                scope.launch { if (visible.isNotEmpty()) { list.scrollToItem(visible.lastIndex); lastSeen = visible.last().id } }
            }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp).testTag("chat-latest")) {
                Icon(Icons.Default.ArrowDownward, null, Modifier.size(15.dp)); Text("回到最新", Modifier.padding(start = 6.dp))
                val unread = visible.count { it.id > lastSeen }
                if (unread > 0) Text(" · $unread", fontSize = 11.sp)
            }
            HorizontalDivider(color = UiHairline)
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(preferences.chatAutoScroll, onAutoScroll, Modifier.size(32.dp))
                Text("自动滚动", fontSize = 11.sp, color = UiMuted)
                Spacer(Modifier.weight(1f)); Text("最近 ${visible.size} 条", fontSize = 10.sp, color = UiMuted)
            }
        }
    }
}
