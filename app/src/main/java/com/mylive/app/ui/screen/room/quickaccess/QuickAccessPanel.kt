package com.mylive.app.ui.screen.room.quickaccess

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val windowHeightDp = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp().value.toInt()
    }
    val compact = windowHeightDp < 480
    val panelHeight = resolveQuickAccessPanelHeightDp(windowHeightDp).dp
    val headerDirectionThresholdPx = with(LocalDensity.current) { 12.dp.toPx() }
    var headerVisible by remember { mutableStateOf(true) }
    var headerAccumulatedDeltaY by remember { mutableFloatStateOf(0f) }
    val headerNestedScrollConnection = remember(headerDirectionThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val next = reduceQuickAccessHeaderScroll(
                    QuickAccessHeaderScrollState(headerVisible, headerAccumulatedDeltaY),
                    available.y, headerDirectionThresholdPx
                )
                headerVisible = next.visible
                headerAccumulatedDeltaY = next.accumulatedDeltaY
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Downward distance left over after the content scrolls means it reached the top.
                // Include fling updates so the bar also returns after an inertial scroll to the top.
                if (available.y <= 0f) return Offset.Zero
                val next = reduceQuickAccessHeaderScroll(
                    QuickAccessHeaderScrollState(headerVisible, headerAccumulatedDeltaY),
                    available.y, headerDirectionThresholdPx, reachedTop = true
                )
                headerVisible = next.visible
                headerAccumulatedDeltaY = next.accumulatedDeltaY
                return Offset.Zero
            }
        }
    }
    var tabsInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(orderedKeys, sortStr) {
        if (sortStr == null) return@LaunchedEffect
        val target = if (!tabsInitialized) initialPage else
            orderedKeys.indexOf(viewModel.selectedTabKey.value).takeIf { it >= 0 } ?: 0
        pagerState.scrollToPage(target)
        tabsInitialized = true
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { page ->
            viewModel.selectTab(orderedKeys[page])
            headerVisible = true
            headerAccumulatedDeltaY = 0f
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(pagerState.settledPage, orderedKeys, currentSiteId, currentCategoryId) {
        if (orderedKeys.getOrNull(pagerState.settledPage) == "recommendation") {
            viewModel.ensureRecommendations(currentSiteId, currentCategoryId)
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
        sheetMaxWidth = if (compact) 560.dp else 640.dp,
        dragHandle = {
            QuickAccessDismissHandle(
                onDownwardDragStarted = { handleDismissAllowed = true },
                onDismiss = dismissPanel,
                onGestureActiveChange = { handleGestureActive = it }
            )
        }
    ) {
        BackHandler(onBack = dismissPanel)
        Column(Modifier.fillMaxWidth().height(panelHeight).navigationBarsPadding().imePadding()
            .roomPanelContentScrollIsolation().testTag("quick_access_panel")) {
            AnimatedVisibility(
                visible = headerVisible,
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
                            if (motionPolicy.reducedMotion) pagerState.scrollToPage(index)
                            else pagerState.animateScrollToPage(index)
                        }
                    }
                )
            }
            actionError?.let { message ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    IconButton(onClick = viewModel::clearActionError) { Icon(Icons.Default.Close, "关闭提示") }
                }
            }
            HorizontalPager(
                state = pagerState,
                key = { orderedKeys[it] },
                modifier = Modifier.fillMaxWidth().weight(1f).nestedScroll(headerNestedScrollConnection)
            ) { page ->
                val selectedKey = orderedKeys[page]
                Box(Modifier.fillMaxSize()) {
                    when (selectedKey) {
                        "follow" -> FollowQuickPanel(viewModel, library, isCurrent, navigate)
                        "history" -> HistoryQuickPanel(viewModel, library, isCurrent, navigate)
                        "recommendation" -> RecommendationQuickPanel(viewModel, library, currentSiteId,
                            currentRoomId, currentResolvedRoomId, pagerState.settledPage == page, navigate)
                        else -> extraTabsByKey[selectedKey]?.content?.invoke()
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuickAccessDismissHandle(
    onDownwardDragStarted: () -> Unit,
    onDismiss: () -> Unit,
    onGestureActiveChange: (Boolean) -> Unit
) {
    Box(
        Modifier.fillMaxWidth().height(48.dp)
            .roomPanelDismissHandleGesture(onDownwardDragStarted, onGestureActiveChange).semantics {
            contentDescription = "下滑关闭快速入口"
            dismiss { onDismiss(); true }
        }, contentAlignment = Alignment.Center
    ) {
        BottomSheetDefaults.DragHandle()
        Text("快速入口", Modifier.align(Alignment.CenterStart).padding(start = 20.dp),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)
            .padding(end = 8.dp).testTag("quick_access_close")) {
            Icon(Icons.Default.Close, "关闭快速入口", modifier = Modifier.size(20.dp))
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
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)) {
        Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            orderedKeys.forEachIndexed { index, key ->
                val extra = extraTabsByKey[key]
                val name = extra?.label ?: when (key) { "follow" -> "关注"; "history" -> "历史"; else -> "推荐" }
                val count = extra?.badgeCount ?: counts[key]
                val label = if (count != null && count > 0) "$name ${if (count > 99) "99+" else count}" else name
                val icon = extra?.icon ?: when (key) { "follow" -> Icons.Default.Favorite; "history" -> Icons.Default.History; else -> Icons.Default.Category }
                val selected = selectedTab == index
                val accent = quickAccessAccentColor()
                val container by animateColorAsState(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = motion.effectsSpec(), label = "quickTabBackground")
                val foreground by animateColorAsState(if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = motion.effectsSpec(), label = "quickTabForeground")
                Column(Modifier.weight(1f).height(if (compact) 48.dp else 60.dp).clip(RoundedCornerShape(24.dp))
                    .background(container).selectable(selected, role = Role.Tab, onClick = { onSelectedTabChange(index) })
                    .testTag("quick_access_tab_$key").padding(horizontal = 3.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (!compact) Icon(icon, null, Modifier.size(20.dp), tint = foreground)
                    Text(label, style = MaterialTheme.typography.labelLarge, color = foreground,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
