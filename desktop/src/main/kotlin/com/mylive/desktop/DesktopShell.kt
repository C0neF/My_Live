package com.mylive.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class DesktopShellAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
internal fun DesktopShell(
    selectedPage: DesktopHomePage?, follows: Int, history: Int, roomInput: String, inputError: String?,
    platform: String, onRoomInput: (String) -> Unit, onOpenRoom: () -> Boolean, onInputFocus: (Boolean) -> Unit,
    onPage: (DesktopHomePage) -> Unit, actions: List<DesktopShellAction>,
    platforms: List<Pair<String, String>> = emptyList(), onPlatform: (String) -> Unit = {}, focusRequest: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    val inputFocus = remember { FocusRequester() }
    var platformMenu by remember { mutableStateOf(false) }
    var roomDialog by remember { mutableStateOf(false) }
    var toolsMenu by remember { mutableStateOf(false) }
    fun dismissRoomEntry() { roomDialog = false; platformMenu = false; onInputFocus(false) }
    fun submitRoomEntry() { if (onOpenRoom()) dismissRoomEntry() }
    LaunchedEffect(focusRequest) { if (focusRequest > 0) roomDialog = true }
    LaunchedEffect(roomDialog, focusRequest) {
        onInputFocus(roomDialog)
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(UiBg).testTag("desktop-shell").onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.L) {
            if (roomDialog) inputFocus.requestFocus() else roomDialog = true
            true
        } else false
    }) {
        val expanded = maxWidth >= 1120.dp
        val compactTools = !expanded || maxHeight < 760.dp
        val contentPadding = if (maxWidth < 1000.dp) 16.dp else 24.dp
        Row(Modifier.fillMaxSize()) {
            Surface(Modifier.width(if (expanded) 208.dp else 84.dp).fillMaxHeight(), color = UiSurface) {
                Column(Modifier.fillMaxSize().padding(horizontal = if (expanded) 12.dp else 8.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource("icons/mylive.png"), DESKTOP_APP_NAME, Modifier.size(36.dp))
                        if (expanded) Column(Modifier.padding(start = 11.dp)) {
                            Text(DESKTOP_APP_NAME, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("直播聚合客户端", style = MaterialTheme.typography.labelSmall, color = UiMuted)
                        }
                    }
                    if (expanded) {
                        DesktopTonalButton(
                            onClick = { roomDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("open-room-entry"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Icon(Icons.Default.Link, null, Modifier.size(18.dp))
                            Text("打开直播间", Modifier.padding(start = 8.dp))
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                            DesktopIconButton(Icons.Default.Link, "打开直播间 Ctrl+L", Modifier.testTag("open-room-entry")) { roomDialog = true }
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DesktopHomePage.entries.forEach { page ->
                            val count = when (page) { DesktopHomePage.Follows -> follows; DesktopHomePage.History -> history; else -> 0 }
                            DesktopSidebarNavItem(
                                page = page,
                                expanded = expanded,
                                active = selectedPage == page,
                                count = count,
                                onClick = { onPage(page) }
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = UiHairline)
                    val quickActions = if (compactTools) actions.takeLast(2) else actions
                    if (compactTools && actions.size > 2) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (expanded) DesktopSidebarActionButton(Icons.Default.MoreHoriz, "更多工具") { toolsMenu = true }
                        else DesktopIconButton(Icons.Default.MoreHoriz, "更多工具") { toolsMenu = true }
                        DropdownMenu(toolsMenu, { toolsMenu = false }) {
                            actions.dropLast(2).forEach { action -> DropdownMenuItem(text = { Text(action.label) },
                                leadingIcon = { Icon(action.icon, null, Modifier.size(19.dp)) },
                                onClick = { toolsMenu = false; action.onClick() }) }
                        }
                    }
                    quickActions.forEach { action ->
                        if (expanded) DesktopSidebarActionButton(action.icon, action.label, action.onClick)
                        else Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                            DesktopIconButton(action.icon, action.label, onClick = action.onClick)
                        }
                    }
                }
            }
            VerticalDivider(color = UiHairline)
            Surface(Modifier.weight(1f).fillMaxHeight(), color = UiBg) {
                Column(Modifier.fillMaxSize().padding(horizontal = contentPadding, vertical = 20.dp), content = content)
            }
        }
    }
    if (roomDialog) AlertDialog(onDismissRequest = ::dismissRoomEntry, title = { Text("通过房间号或链接进入") }, text = {
        LaunchedEffect(focusRequest) {
            withFrameNanos { }
            inputFocus.requestFocus()
        }
        Column(Modifier.width(500.dp)) {
            OutlinedTextField(roomInput, onRoomInput, Modifier.fillMaxWidth().testTag("room-input").focusRequester(inputFocus),
                singleLine = true, isError = inputError != null, placeholder = { Text("粘贴直播间链接，或输入房间号", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    if (platforms.isEmpty()) Icon(Icons.Default.Link, null, Modifier.size(19.dp))
                    else Box {
                        DesktopTextButton(onClick = { platformMenu = true }, contentPadding = PaddingValues(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)) {
                            Text(platform.removeSuffix("直播"), fontSize = 12.sp)
                            Icon(Icons.Default.ExpandMore, "选择房间号所属平台", Modifier.padding(start = 4.dp).size(15.dp))
                        }
                        DropdownMenu(platformMenu, { platformMenu = false }) {
                            platforms.forEach { (id, name) -> DropdownMenuItem(text = { Text(name.removeSuffix("直播")) }, onClick = { platformMenu = false; onPlatform(id) }) }
                        }
                    }
                }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitRoomEntry() }), shape = RoundedCornerShape(11.dp))
            if (inputError != null) Text(inputError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            Text("Ctrl+L 可随时打开此入口。", color = UiMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }, confirmButton = { DesktopPrimaryButton(::submitRoomEntry, enabled = roomInput.isNotBlank()) { Text("进入直播间") } },
        dismissButton = { DesktopTextButton(::dismissRoomEntry) { Text("取消") } })
}

private val DesktopNavItemShape = RoundedCornerShape(11.dp)
private val DesktopActionItemShape = RoundedCornerShape(10.dp)

@Composable
private fun DesktopSidebarNavItem(
    page: DesktopHomePage,
    expanded: Boolean,
    active: Boolean,
    count: Int,
    onClick: () -> Unit
) {
    val label = when (page) {
        DesktopHomePage.Recommend -> "首页"
        DesktopHomePage.Search -> "搜索"
        DesktopHomePage.Categories -> "分类"
        DesktopHomePage.Follows -> "关注"
        DesktopHomePage.History -> "历史"
    }
    val icon = when (page) {
        DesktopHomePage.Recommend -> Icons.Default.Explore
        DesktopHomePage.Search -> Icons.Default.Search
        DesktopHomePage.Categories -> Icons.Default.GridView
        DesktopHomePage.Follows -> Icons.Default.FavoriteBorder
        DesktopHomePage.History -> Icons.Default.History
    }
    val navInteraction = remember(page) { MutableInteractionSource() }
    val navHoveredState = navInteraction.collectIsHoveredAsState()
    val navPressedState = navInteraction.collectIsPressedAsState()
    val navFocusedState = navInteraction.collectIsFocusedAsState()
    val accent = UiAccent
    val baseColor = if (active) accent.copy(alpha = .16f) else Color.Transparent
    val hoverColor = if (active) accent.copy(alpha = .20f) else UiElevated
    val pressColor = if (active) accent.copy(alpha = .24f) else accent.copy(alpha = .12f)
    val borderColor = if (active) accent.copy(alpha = .35f) else Color.Transparent
    val contentColor = if (active) accent else UiMuted
    val itemModifier = Modifier
        .fillMaxWidth()
        .testTag("nav-" + page.name)
        .semantics { selected = active }
        .desktopInteractiveSurface(
            shape = DesktopNavItemShape,
            hoveredState = navHoveredState,
            pressedState = navPressedState,
            focusedState = navFocusedState,
            baseColor = baseColor,
            hoverColor = hoverColor,
            pressColor = pressColor,
            baseBorderColor = borderColor,
            hoverBorderColor = borderColor,
            pressBorderColor = borderColor,
            focusBorderColor = accent
        )
        .clickable(
            interactionSource = navInteraction,
            indication = null,
            role = Role.Tab,
            onClick = onClick
        )
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        if (expanded) {
            Row(
                itemModifier.padding(horizontal = 13.dp).height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, Modifier.size(20.dp))
                Text(
                    label,
                    Modifier.weight(1f).padding(start = 12.dp),
                    color = if (active) accent else UiInk.copy(alpha = .86f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
                if (count > 0) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) accent else UiMuted
                    )
                }
            }
        } else {
            Column(
                itemModifier.height(58.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BadgedBox(
                    badge = {
                        if (count > 0) {
                            Badge(containerColor = UiElevated, contentColor = UiMuted) {
                                Text(if (count > 99) "99+" else count.toString(), fontSize = 9.sp)
                            }
                        }
                    }
                ) {
                    Icon(icon, null, Modifier.size(21.dp))
                }
                Text(label, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun DesktopSidebarActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hoveredState = interaction.collectIsHoveredAsState()
    val pressedState = interaction.collectIsPressedAsState()
    CompositionLocalProvider(LocalContentColor provides UiMuted) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .desktopInteractiveSurface(
                    shape = DesktopActionItemShape,
                    hoveredState = hoveredState,
                    pressedState = pressedState,
                    baseColor = Color.Transparent,
                    hoverColor = UiElevated,
                    pressColor = UiAccent.copy(alpha = .12f)
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(18.dp))
            Text(
                label,
                Modifier.weight(1f).padding(start = 12.dp),
                color = UiInk.copy(alpha = .84f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
