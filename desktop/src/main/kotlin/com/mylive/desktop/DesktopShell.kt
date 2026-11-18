package com.mylive.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class DesktopShellAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
internal fun DesktopShell(
    selectedPage: DesktopHomePage?,
    follows: Int,
    history: Int,
    roomInput: String,
    inputError: String?,
    platform: String,
    onRoomInput: (String) -> Unit,
    onOpenRoom: () -> Boolean,
    onInputFocus: (Boolean) -> Unit,
    onPage: (DesktopHomePage) -> Unit,
    actions: List<DesktopShellAction>,
    platforms: List<Pair<String, String>> = emptyList(),
    onPlatform: (String) -> Unit = {},
    focusRequest: Int = 0,
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
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(UiBg)
            .testTag("desktop-shell")
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.L) {
                    if (roomDialog) inputFocus.requestFocus() else roomDialog = true
                    true
                } else false
            }
    ) {
        val expanded = maxWidth >= 1120.dp
        val compactTools = !expanded || maxHeight < 760.dp
        val contentPadding = if (maxWidth < 1000.dp) 18.dp else 26.dp
        val sidebarWidth by animateDpAsState(
            targetValue = if (expanded) 216.dp else 80.dp,
            animationSpec = tween(durationMillis = 200, easing = DesktopStandardEasing)
        )
        Row(Modifier.fillMaxSize()) {
            Surface(Modifier.width(sidebarWidth).fillMaxHeight(), color = UiSidebar) {
                Column(Modifier.fillMaxSize().padding(horizontal = if (expanded) 12.dp else 8.dp, vertical = 14.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = if (expanded) 8.dp else 0.dp),
                        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painterResource("icons/mylive.png"),
                            DESKTOP_APP_NAME,
                            Modifier.size(28.dp).clip(RoundedCornerShape(7.dp))
                        )
                        if (expanded) {
                            Text(
                                DESKTOP_APP_NAME,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                    if (expanded) {
                        DesktopTonalButton(
                            onClick = { roomDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("open-room-entry"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Icon(Icons.Default.Link, null, Modifier.size(16.dp))
                            Text("打开直播间", Modifier.padding(start = 7.dp))
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                            DesktopIconButton(Icons.Default.Link, "打开直播间 Ctrl+L", Modifier.testTag("open-room-entry")) { roomDialog = true }
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (expanded) {
                            Text(
                                "浏览",
                                style = MaterialTheme.typography.labelSmall,
                                color = UiMuted,
                                modifier = Modifier.padding(start = 10.dp, bottom = 4.dp)
                            )
                        }
                        DesktopHomePage.entries.forEach { page ->
                            val count = when (page) {
                                DesktopHomePage.Follows -> follows
                                DesktopHomePage.History -> history
                                else -> 0
                            }
                            DesktopSidebarNavItem(
                                page = page,
                                expanded = expanded,
                                active = selectedPage == page,
                                count = count,
                                onClick = { onPage(page) }
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = DesktopHairlineWidth, color = UiHairline)
                    val quickActions = if (compactTools) actions.takeLast(2) else actions
                    if (compactTools && actions.size > 2) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (expanded) DesktopSidebarActionButton(Icons.Default.MoreHoriz, "更多工具") { toolsMenu = true }
                        else DesktopIconButton(Icons.Default.MoreHoriz, "更多工具") { toolsMenu = true }
                        DropdownMenu(toolsMenu, { toolsMenu = false }) {
                            actions.dropLast(2).forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    leadingIcon = { Icon(action.icon, null, Modifier.size(18.dp)) },
                                    onClick = { toolsMenu = false; action.onClick() }
                                )
                            }
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
            VerticalDivider(thickness = DesktopHairlineWidth, color = UiHairline)
            Surface(Modifier.weight(1f).fillMaxHeight(), color = UiBg) {
                Column(Modifier.fillMaxSize().padding(horizontal = contentPadding, vertical = 20.dp), content = content)
            }
        }
    }
    if (roomDialog) {
        DesktopSheetModal(
            title = "通过房间号或链接进入",
            onDismissRequest = ::dismissRoomEntry,
            preferredWidth = 520.dp,
            content = {
                LaunchedEffect(focusRequest) {
                    withFrameNanos { }
                    inputFocus.requestFocus()
                }
                Column(Modifier.fillMaxWidth()) {
                    DesktopTextField(
                        value = roomInput,
                        onValueChange = onRoomInput,
                        modifier = Modifier.fillMaxWidth().testTag("room-input").focusRequester(inputFocus),
                        singleLine = true,
                        isError = inputError != null,
                        placeholder = {
                            Text("粘贴直播间链接，或输入房间号", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingIcon = {
                            if (platforms.isEmpty()) Icon(Icons.Default.Link, null, Modifier.size(18.dp))
                            else Box {
                                DesktopTextButton(
                                    onClick = { platformMenu = true },
                                    contentPadding = PaddingValues(start = 8.dp, end = 6.dp, top = 3.dp, bottom = 3.dp)
                                ) {
                                    Text(platform.removeSuffix("直播"), fontSize = 12.sp, color = UiInk)
                                    Icon(Icons.Default.ExpandMore, "选择房间号所属平台", Modifier.padding(start = 3.dp).size(15.dp), tint = UiMuted)
                                }
                                DropdownMenu(platformMenu, { platformMenu = false }) {
                                    platforms.forEach { (id, name) ->
                                        DropdownMenuItem(
                                            text = { Text(name.removeSuffix("直播")) },
                                            onClick = { platformMenu = false; onPlatform(id) }
                                        )
                                    }
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitRoomEntry() })
                    )
                    var cachedInputError by remember { mutableStateOf(inputError.orEmpty()) }
                    if (inputError != null) cachedInputError = inputError
                    AnimatedVisibility(
                        visible = inputError != null,
                        enter = desktopExpandEnter(),
                        exit = desktopCollapseExit()
                    ) {
                        Text(
                            cachedInputError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Text(
                        "Ctrl+L 可随时打开此入口。",
                        color = UiMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            },
            confirmButton = {
                DesktopPrimaryButton(::submitRoomEntry, enabled = roomInput.isNotBlank()) {
                    Text("进入直播间")
                }
            },
            dismissButton = {
                DesktopTextButton(::dismissRoomEntry) { Text("取消") }
            }
        )
    }
}

private val DesktopNavItemShape = RoundedCornerShape(9.dp)
private val DesktopActionItemShape = RoundedCornerShape(9.dp)

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
        DesktopHomePage.Follows -> if (active) Icons.Default.Favorite else Icons.Default.FavoriteBorder
        DesktopHomePage.History -> Icons.Default.History
    }
    val navInteraction = remember(page) { MutableInteractionSource() }
    val navHoveredState = navInteraction.collectIsHoveredAsState()
    val navPressedState = navInteraction.collectIsPressedAsState()
    val navFocusedState = navInteraction.collectIsFocusedAsState()
    val accent = UiAccent
    val isDark = MaterialTheme.colorScheme.background.luminance() < .5f
    val activeFill = if (isDark) Color(0xff2a2a2e) else Color.White
    val baseColor by animateColorAsState(
        targetValue = if (active) activeFill else Color.Transparent,
        animationSpec = tween(durationMillis = 170, easing = DesktopStandardEasing)
    )
    val hoverColor = if (active) activeFill else UiInk.copy(alpha = .06f)
    val pressColor = if (active) activeFill else UiInk.copy(alpha = .10f)
    val activeBorder by animateColorAsState(
        targetValue = when {
            !active -> Color.Transparent
            isDark -> Color.White.copy(alpha = .06f)
            else -> Color.Black.copy(alpha = .05f)
        },
        animationSpec = tween(durationMillis = 170, easing = DesktopStandardEasing)
    )
    val iconTint by animateColorAsState(
        targetValue = if (active) accent else UiMuted,
        animationSpec = tween(durationMillis = 170, easing = DesktopStandardEasing)
    )
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
            baseBorderColor = activeBorder,
            hoverBorderColor = activeBorder,
            pressBorderColor = activeBorder,
            focusBorderColor = accent
        )
        .clickable(
            interactionSource = navInteraction,
            indication = null,
            role = Role.Tab,
            onClick = onClick
        )
    CompositionLocalProvider(LocalContentColor provides iconTint) {
        if (expanded) {
            Row(
                itemModifier.padding(horizontal = 11.dp).height(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, Modifier.size(18.dp))
                Text(
                    label,
                    Modifier.weight(1f).padding(start = 10.dp),
                    color = if (active) UiInk else UiInk.copy(alpha = .85f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
                if (count > 0) {
                    Text(
                        if (count > 99) "99+" else count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = UiMuted
                    )
                }
            }
        } else {
            Column(
                itemModifier.height(52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box {
                    Icon(icon, null, Modifier.size(20.dp))
                    if (count > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-3).dp)
                                .background(accent.copy(alpha = .16f), CircleShape)
                                .padding(horizontal = 4.dp, vertical = 0.5.dp)
                        ) {
                            Text(if (count > 99) "99+" else count.toString(), fontSize = 9.sp, color = accent)
                        }
                    }
                }
                Text(
                    label,
                    fontSize = 11.sp,
                    color = if (active) UiInk else UiMuted,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.padding(top = 2.dp)
                )
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
                .height(34.dp)
                .desktopInteractiveSurface(
                    shape = DesktopActionItemShape,
                    hoveredState = hoveredState,
                    pressedState = pressedState,
                    baseColor = Color.Transparent,
                    hoverColor = UiInk.copy(alpha = .06f),
                    pressColor = UiInk.copy(alpha = .10f)
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(17.dp))
            Text(
                label,
                Modifier.weight(1f).padding(start = 10.dp),
                color = UiInk.copy(alpha = .85f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
