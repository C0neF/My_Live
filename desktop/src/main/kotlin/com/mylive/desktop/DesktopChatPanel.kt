package com.mylive.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.core.model.*
import kotlinx.coroutines.launch

@Composable
internal fun DesktopChatPanel(
    messages: List<ChatEntry>,
    paid: List<LiveSuperChatMessage>,
    status: String,
    preferences: DesktopPlayerPreferences,
    filter: DesktopDanmakuPreferences,
    siteId: String,
    onAutoScroll: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val visible = remember(messages, filter, siteId) { messages.filter { filter.allows(it.message, siteId) } }
    val visiblePaid = remember(paid, filter, siteId) {
        paid.filter { filter.allows(LiveMessage(LiveMessageType.SUPER_CHAT, it.userName, it.message), siteId) }
    }
    var superChatTab by remember { mutableStateOf(false) }
    LaunchedEffect(preferences.superChatVisible, visiblePaid.isEmpty()) {
        if (!preferences.superChatVisible || visiblePaid.isEmpty()) {
            superChatTab = false
        }
    }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var following by remember { mutableStateOf(true) }
    var lastSeen by remember { mutableLongStateOf(0L) }
    val scrollbar = remember { MutableInteractionSource() }
    val dragging by scrollbar.collectIsDraggedAsState()
    val userScroll = remember(list) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) following = false
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && !list.canScrollForward) following = true
                return Offset.Zero
            }
        }
    }
    val atBottom by remember { derivedStateOf { !list.canScrollForward } }
    LaunchedEffect(dragging) { if (dragging) following = false else if (!list.canScrollForward) following = true }
    LaunchedEffect(preferences.chatAutoScroll) { if (preferences.chatAutoScroll) following = true }
    LaunchedEffect(visible.isEmpty()) {
        if (visible.isEmpty()) {
            following = true
            lastSeen = 0
        }
    }
    LaunchedEffect(visible.lastOrNull()?.id, preferences.chatAutoScroll, following, atBottom) {
        val newestId = visible.lastOrNull()?.id ?: return@LaunchedEffect
        if (preferences.chatAutoScroll && following) {
            list.scrollToItem(visible.lastIndex)
            lastSeen = newestId
        } else if (atBottom) {
            lastSeen = maxOf(lastSeen, newestId)
        }
    }

    val connected = status == "已连接"
    val dotColor by animateColorAsState(
        targetValue = if (connected) DesktopLiveEmerald else UiMuted,
        animationSpec = tween(durationMillis = 180, easing = DesktopStandardEasing)
    )
    Surface(
        modifier,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(DesktopHairlineWidth, UiHairline),
        color = UiSurface
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 15.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("实时聊天", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(dotColor, CircleShape)
                        )
                        Text(status, style = MaterialTheme.typography.labelSmall, color = UiMuted, maxLines = 2)
                    }
                }
                DesktopIconButton(Icons.Default.Close, "关闭聊天", onClick = onClose)
            }
            AnimatedVisibility(
                visible = preferences.superChatVisible && visiblePaid.isNotEmpty(),
                enter = desktopExpandEnter(),
                exit = desktopCollapseExit()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = UiFieldFill
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        DesktopTabButton(
                            title = "弹幕",
                            selected = !superChatTab,
                            showActiveDot = true,
                            modifier = Modifier.weight(1f),
                            onClick = { superChatTab = false }
                        )
                        DesktopTabButton(
                            title = "SC (${visiblePaid.size})",
                            selected = superChatTab,
                            accentOverride = Color(0xfff59e0b),
                            showActiveDot = true,
                            modifier = Modifier.weight(1f),
                            onClick = { superChatTab = true }
                        )
                    }
                }
            }
            HorizontalDivider(thickness = DesktopHairlineWidth, color = UiHairline)

            val showScTab = superChatTab && preferences.superChatVisible && visiblePaid.isNotEmpty()
            AnimatedContent(
                targetState = showScTab,
                transitionSpec = {
                    desktopDirectionalTabTransform(if (targetState) 1 else -1)
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { isScPage ->
                if (isScPage) {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visiblePaid, key = { it.id ?: "${it.userName}:${it.startTime}:${it.message}" }) { message ->
                            SuperChatBubble(message)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible = preferences.superChatVisible && visiblePaid.isNotEmpty(),
                            enter = desktopExpandEnter(),
                            exit = desktopCollapseExit()
                        ) {
                            LazyColumn(
                                Modifier.heightIn(max = 118.dp).padding(start = 12.dp, end = 12.dp, top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(visiblePaid.takeLast(3), key = { it.id ?: "${it.userName}:${it.startTime}:${it.message}" }) { message ->
                                    SuperChatBubble(message)
                                }
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth().padding(start = 13.dp, end = 6.dp, top = 10.dp)) {
                            if (visible.isEmpty()) {
                                Column(
                                    Modifier.align(Alignment.Center).padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(28.dp), tint = UiMuted.copy(alpha = .5f))
                                    Text(
                                        if (messages.isNotEmpty()) "消息已被屏蔽规则过滤" else "暂无聊天消息",
                                        color = UiMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 12.dp)
                                    )
                                }
                            }
                            SelectionContainer {
                                LazyColumn(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(end = 12.dp)
                                        .testTag("chat-list")
                                        .nestedScroll(userScroll)
                                        .onPreviewKeyEvent { event ->
                                            val delta = when (event.key) {
                                                Key.PageUp -> -300f
                                                Key.PageDown -> 300f
                                                Key.DirectionUp -> -40f
                                                Key.DirectionDown -> 40f
                                                else -> null
                                            }
                                            if (event.type == KeyEventType.KeyDown && delta != null && !event.isCtrlPressed && !event.isAltPressed) {
                                                following = false
                                                scope.launch {
                                                    list.animateScrollBy(delta)
                                                    if (!list.canScrollForward) following = true
                                                }
                                                true
                                            } else false
                                        },
                                    state = list,
                                    verticalArrangement = Arrangement.spacedBy(9.dp),
                                    contentPadding = PaddingValues(bottom = 12.dp)
                                ) {
                                    items(visible, key = { it.id }) { entry ->
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(
                                                entry.message.userName,
                                                color = UiMuted,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                            DesktopMessageText(entry.message, preferences.chatTextSize.sp, images = filter.renderEmoji)
                                        }
                                    }
                                }
                            }
                            VerticalScrollbar(
                                rememberScrollbarAdapter(list),
                                Modifier.align(Alignment.CenterEnd).fillMaxHeight().testTag("chat-scrollbar"),
                                interactionSource = scrollbar,
                                style = defaultScrollbarStyle().copy(
                                    thickness = 5.dp,
                                    unhoverColor = UiMuted.copy(alpha = .2f),
                                    hoverColor = UiMuted.copy(alpha = .6f)
                                )
                            )
                        }
                        AnimatedVisibility(
                            visible = (!following || !preferences.chatAutoScroll) && list.canScrollForward,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            enter = desktopPopoverEnter(),
                            exit = desktopPopoverExit()
                        ) {
                            DesktopTonalButton(
                                onClick = {
                                    following = true
                                    scope.launch {
                                        if (visible.isNotEmpty()) {
                                            list.scrollToItem(visible.lastIndex)
                                            lastSeen = visible.last().id
                                        }
                                    }
                                },
                                modifier = Modifier.padding(vertical = 6.dp).testTag("chat-latest")
                            ) {
                                Icon(Icons.Default.ArrowDownward, null, Modifier.size(15.dp))
                                Text("回到最新", Modifier.padding(start = 6.dp))
                                val unread = visible.count { it.id > lastSeen }
                                if (unread > 0) Text(" · $unread", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = DesktopHairlineWidth, color = UiHairline)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .toggleable(
                            value = preferences.chatAutoScroll,
                            role = Role.Switch,
                            onValueChange = onAutoScroll
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DesktopSwitch(checked = preferences.chatAutoScroll)
                    Text("自动滚动", fontSize = 11.sp, color = UiMuted)
                }
                Spacer(Modifier.weight(1f))
                Text("最近 ${visible.size} 条", fontSize = 11.sp, color = UiMuted)
            }
        }
    }
}

@Composable
private fun SuperChatBubble(message: LiveSuperChatMessage) {
    val scAmber = Color(0xfff59e0b)
    Surface(
        color = scAmber.copy(alpha = .12f),
        border = BorderStroke(DesktopHairlineWidth, scAmber.copy(alpha = .32f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        SelectionContainer {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = scAmber.copy(alpha = .22f)
                    ) {
                        Text(
                            "¥${message.price}",
                            color = scAmber,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        message.userName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                if (message.message.isNotBlank()) {
                    Text(
                        message.message,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
