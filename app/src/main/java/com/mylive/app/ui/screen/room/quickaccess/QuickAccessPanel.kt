package com.mylive.app.ui.screen.room.quickaccess

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import com.mylive.app.ui.screen.room.roomPanelContentScrollIsolation
import com.mylive.app.ui.screen.room.roomPanelDismissHandleGesture
import com.mylive.app.ui.theme.Icons
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class QuickAccessExtraTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int? = null,
    val content: @Composable () -> Unit
)

internal data class QuickAccessHeaderScrollState(
    val visible: Boolean,
    val accumulatedDeltaY: Float = 0f
)

internal fun reduceQuickAccessHeaderScroll(
    state: QuickAccessHeaderScrollState,
    deltaY: Float,
    thresholdPx: Float,
    reachedTop: Boolean = false
): QuickAccessHeaderScrollState {
    if (!deltaY.isFinite() || deltaY == 0f) return state
    if (deltaY > 0f && reachedTop) return QuickAccessHeaderScrollState(visible = true)
    val threshold = thresholdPx.coerceAtLeast(0f)
    return if (state.visible) {
        if (deltaY > 0f) {
            state.copy(accumulatedDeltaY = 0f)
        } else {
            val accumulated = state.accumulatedDeltaY.coerceAtMost(0f) + deltaY
            if (-accumulated >= threshold) {
                QuickAccessHeaderScrollState(visible = false)
            } else {
                state.copy(accumulatedDeltaY = accumulated)
            }
        }
    } else {
        state.copy(accumulatedDeltaY = 0f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun quickAccessAllowsSheetValueChange(
    target: SheetValue,
    handleDismissAllowed: Boolean
): Boolean = target != SheetValue.Hidden || handleDismissAllowed

// ─── Main QuickAccess Panel ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessPanel(
    currentSiteId: String,
    currentRoomId: String,
    currentCategoryId: String?,
    extraTabs: List<QuickAccessExtraTab> = emptyList(),
    initialSelectedKey: String? = null,
    onNavigateToRoom: (siteId: String, roomId: String, initialIsFollowing: Boolean?) -> Unit,
    onDismiss: () -> Unit,
    currentResolvedRoomId: String? = null,
    currentCategoryParentId: String? = null,
    viewModel: QuickAccessViewModel = hiltViewModel()
) {
    val sortStr by viewModel.quickAccessSort.collectAsStateWithLifecycle()
    val enabled by viewModel.quickAccessEnabled.collectAsStateWithLifecycle()
    LaunchedEffect(enabled) { if (!enabled) onDismiss() }
    if (!enabled) return

    val extraTabsByKey = extraTabs.associateBy { it.key }
    val extraKeys = extraTabs.map { it.key }
    val orderedKeys = remember(sortStr, extraKeys) { orderedQuickAccessTabs(sortStr.orEmpty(), extraKeys) }
    val requestedKey = remember(initialSelectedKey) { initialSelectedKey ?: viewModel.selectedTabKey.value }
    val initialPage = remember(orderedKeys, requestedKey) {
        orderedKeys.indexOf(requestedKey).takeIf { it >= 0 } ?: 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { orderedKeys.size })
    val library by viewModel.library.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val motionPolicy = LocalAppMotionPolicy.current
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val containerHeightDp = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp().value.toInt()
    }
    val windowHeightDp = if (containerHeightDp > 0 && configuration.screenHeightDp > 0) {
        minOf(containerHeightDp, configuration.screenHeightDp)
    } else if (containerHeightDp > 0) {
        containerHeightDp
    } else {
        configuration.screenHeightDp
    }
    val compact = windowHeightDp < 480 || configuration.screenWidthDp > configuration.screenHeightDp
    val panelHeight = resolveQuickAccessPanelHeightDp(windowHeightDp).dp
    var searchingTabKey by remember { mutableStateOf<String?>(null) }
    var tabsInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(orderedKeys, sortStr) {
        if (sortStr == null) return@LaunchedEffect
        val target = if (!tabsInitialized) initialPage else
            orderedKeys.indexOf(viewModel.selectedTabKey.value).takeIf { it >= 0 } ?: 0
        pagerState.scrollToPage(target)
        tabsInitialized = true
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { page ->
            viewModel.selectTab(orderedKeys[page])
            searchingTabKey = null
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(pagerState.settledPage, orderedKeys, currentSiteId, currentCategoryId, currentCategoryParentId) {
        if (orderedKeys.getOrNull(pagerState.settledPage) == "recommendation") {
            viewModel.ensureRecommendations(currentSiteId, currentCategoryId, currentCategoryParentId)
        }
    }

    var handleDismissAllowed by remember { mutableStateOf(false) }
    var handleGestureActive by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val dismissPanel: () -> Unit = { focusManager.clearFocus(); onDismiss() }
    val navigate: (String, String, Boolean?) -> Unit = { siteId, roomId, following ->
        if (!leaving) {
            leaving = true
            focusManager.clearFocus()
            if (isCurrentQuickAccessRoom(siteId, roomId, currentSiteId, currentRoomId, currentResolvedRoomId)) {
                onDismiss()
            } else {
                onNavigateToRoom(siteId, roomId, following)
            }
        }
    }
    val isCurrent: (String, String) -> Boolean = { site, room ->
        isCurrentQuickAccessRoom(site, room, currentSiteId, currentRoomId, currentResolvedRoomId)
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            // Native dragging and hide() may consult this predicate more than once.
            // Keep the permission valid until the handle gesture has finished settling.
            quickAccessAllowsSheetValueChange(target, handleDismissAllowed)
        }
    )
    LaunchedEffect(handleGestureActive, handleDismissAllowed) {
        if (handleGestureActive || !handleDismissAllowed) return@LaunchedEffect
        withFrameNanos { }
        val settled = snapshotFlow { sheetState.currentValue to sheetState.targetValue }
            .first { (current, target) -> current == target }
        if (settled.first == SheetValue.Expanded) handleDismissAllowed = false
    }
    ModalBottomSheet(
        onDismissRequest = dismissPanel,
        sheetState = sheetState,
        sheetMaxWidth = if (compact) 620.dp else 640.dp,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                QuickAccessDismissHandle(
                    compact = compact,
                    onDownwardDragStarted = { handleDismissAllowed = true },
                    onDismiss = dismissPanel,
                    onGestureActiveChange = { handleGestureActive = it }
                )
            }
        }
    ) {
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
        }
        BackHandler(onBack = {
            if (searchingTabKey != null) {
                focusManager.clearFocus()
                searchingTabKey = null
            } else {
                dismissPanel()
            }
        })
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Column(Modifier.fillMaxWidth().height(panelHeight).navigationBarsPadding().imePadding()
                .roomPanelContentScrollIsolation().testTag("quick_access_panel")) {
                AnimatedVisibility(
                    visible = searchingTabKey == null,
                    enter = if (motionPolicy.reducedMotion) fadeIn(motionPolicy.effectsSpec()) else
                        expandVertically(motionPolicy.contentSpec(), expandFrom = Alignment.Top) + fadeIn(motionPolicy.effectsSpec()),
                    exit = if (motionPolicy.reducedMotion) fadeOut(motionPolicy.effectsSpec()) else
                        shrinkVertically(motionPolicy.contentSpec(), shrinkTowards = Alignment.Top) + fadeOut(motionPolicy.effectsSpec())
                ) {
                    QuickAccessIslandTabBar(
                        orderedKeys = orderedKeys,
                        selectedTab = pagerState.currentPage,
                        extraTabsByKey = extraTabsByKey,
                        counts = mapOf("follow" to library.follows.size, "history" to library.history.size),
                        compact = compact,
                        onSelectedTabChange = { index ->
                            coroutineScope.launch {
                                focusManager.clearFocus()
                                searchingTabKey = null
                                if (motionPolicy.reducedMotion) pagerState.scrollToPage(index)
                                else pagerState.animateScrollToPage(index)
                            }
                        }
                    )
                }
                var cachedActionError by remember { mutableStateOf(actionError.orEmpty()) }
                if (actionError != null) cachedActionError = actionError.orEmpty()
                AnimatedVisibility(
                    visible = actionError != null,
                    enter = if (motionPolicy.reducedMotion) fadeIn(motionPolicy.effectsSpec()) else
                        expandVertically(motionPolicy.contentSpec(), expandFrom = Alignment.Top) + fadeIn(motionPolicy.effectsSpec()),
                    exit = if (motionPolicy.reducedMotion) fadeOut(motionPolicy.effectsSpec()) else
                        shrinkVertically(motionPolicy.contentSpec(), shrinkTowards = Alignment.Top) + fadeOut(motionPolicy.effectsSpec())
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(cachedActionError, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        IconButton(onClick = viewModel::clearActionError) { Icon(Icons.Default.Close, "关闭提示") }
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    key = { orderedKeys[it] },
                    userScrollEnabled = searchingTabKey == null,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { page ->
                    val selectedKey = orderedKeys[page]
                    Box(Modifier.fillMaxSize()) {
                        when (selectedKey) {
                            "follow" -> FollowQuickPanel(
                                viewModel, library, isCurrent, navigate,
                                compact = compact,
                                searching = searchingTabKey == "follow",
                                onSearchingChange = { active ->
                                    if (!active) focusManager.clearFocus()
                                    searchingTabKey = if (active) "follow" else null
                                }
                            )
                            "history" -> HistoryQuickPanel(
                                viewModel, library, isCurrent, navigate,
                                compact = compact,
                                searching = searchingTabKey == "history",
                                onSearchingChange = { active ->
                                    if (!active) focusManager.clearFocus()
                                    searchingTabKey = if (active) "history" else null
                                }
                            )
                            "recommendation" -> RecommendationQuickPanel(viewModel, library, currentSiteId,
                                currentRoomId, currentResolvedRoomId, pagerState.settledPage == page, navigate, compact = compact)
                            else -> extraTabsByKey[selectedKey]?.content?.invoke()
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuickAccessDismissHandle(
    compact: Boolean = false,
    onDownwardDragStarted: () -> Unit,
    onDismiss: () -> Unit,
    onGestureActiveChange: (Boolean) -> Unit
) {
    Box(
        Modifier.fillMaxWidth()
            .roomPanelDismissHandleGesture(onDownwardDragStarted, onGestureActiveChange)
            .padding(
                start = 24.dp,
                end = 20.dp,
                top = if (compact) 10.dp else 12.dp,
                bottom = if (compact) 4.dp else 6.dp
            )
            .height(if (compact) 32.dp else 36.dp)
            .semantics {
                contentDescription = "下滑关闭快速入口"
                dismiss { onDismiss(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        BottomSheetDefaults.DragHandle()
        Text(
            "快速入口",
            Modifier.align(Alignment.CenterStart),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(if (compact) 30.dp else 34.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    RoundedCornerShape(50)
                )
                .testTag("quick_access_close")
        ) {
            Icon(Icons.Default.Close, "关闭快速入口", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun QuickAccessIslandTabBar(
    orderedKeys: List<String>, selectedTab: Int,
    extraTabsByKey: Map<String, QuickAccessExtraTab>, counts: Map<String, Int>, compact: Boolean,
    onSelectedTabChange: (Int) -> Unit
) {
    val motion = LocalAppMotionPolicy.current
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 1.dp else 4.dp),
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(if (compact) 2.dp else 3.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            orderedKeys.forEachIndexed { index, key ->
                val extra = extraTabsByKey[key]
                val name = extra?.label ?: when (key) { "follow" -> "关注"; "history" -> "历史"; else -> "推荐" }
                val count = extra?.badgeCount ?: counts[key]
                val label = if (count != null && count > 0) "$name ${if (count > 99) "99+" else count}" else name
                val icon = extra?.icon ?: when (key) { "follow" -> Icons.Default.Favorite; "history" -> Icons.Default.History; else -> Icons.Default.Category }
                val selected = selectedTab == index
                val container by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
                    animationSpec = motion.effectsSpec(), label = "quickTabBackground"
                )
                val foreground by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = motion.effectsSpec(), label = "quickTabForeground"
                )
                Row(Modifier.weight(1f).height(if (compact) 30.dp else 40.dp).clip(RoundedCornerShape(24.dp))
                    .background(container).selectable(selected, role = Role.Tab, onClick = { onSelectedTabChange(index) })
                    .testTag("quick_access_tab_$key").padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (!compact) {
                        Icon(icon, null, Modifier.size(15.dp), tint = foreground)
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelLarge, color = foreground,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
