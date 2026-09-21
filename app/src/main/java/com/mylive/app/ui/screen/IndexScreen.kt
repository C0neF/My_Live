package com.mylive.app.ui.screen

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.R
import com.mylive.app.core.model.TabletOrientationMode
import com.mylive.app.ui.motion.adaptiveNavigationContentTransform
import com.mylive.app.ui.orientation.nextTabletOrientationMode
import com.mylive.app.ui.orientation.resolveTabletOrientationMode
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.layout.AdaptivePageFrame
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mylive.app.ui.screen.category.CategoryScreen
import com.mylive.app.ui.screen.follow.FollowScreen
import com.mylive.app.ui.screen.follow.followCardGridColumns
import com.mylive.app.ui.screen.home.HomeScreen
import com.mylive.app.ui.screen.mine.MineScreen
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import com.mylive.app.ui.navigation.Route
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.mylive.app.ui.screen.settings.SettingsViewModel

data class BottomNavItem(
    val key: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
)

private val allBottomNavItems = mapOf(
    "recommend" to BottomNavItem("recommend", R.string.nav_home, Icons.Default.Home),
    "follow" to BottomNavItem("follow", R.string.nav_follow, Icons.Default.Favorite),
    "category" to BottomNavItem("category", R.string.nav_category, Icons.Default.Category),
    "user" to BottomNavItem("user", R.string.nav_mine, Icons.Default.Person),
)

private val defaultHomeOrder = listOf("recommend", "follow", "category", "user")

private data class BottomNavSelection(val index: Int, val key: String)

@Composable
fun IndexScreen(
    navigator: Navigator,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val homeSortStr by viewModel.homeSort.collectAsStateWithLifecycle()
    val sortedKeys = remember(homeSortStr) {
        val keys = homeSortStr.split(",").filter { it.isNotBlank() }
        val full = keys.toMutableList()
        defaultHomeOrder.forEach { k -> if (k !in full) full.add(k) }
        full
    }
    val bottomNavItems = remember(sortedKeys) {
        sortedKeys.mapNotNull { key -> allBottomNavItems[key] }
    }
    var selectedPageKey by rememberSaveable { mutableStateOf("recommend") }
    var homeRefreshSignal by rememberSaveable { mutableIntStateOf(0) }
    var followRefreshSignal by rememberSaveable { mutableIntStateOf(0) }
    var categoryRefreshSignal by rememberSaveable { mutableIntStateOf(0) }
    val tabStateHolder = rememberSaveableStateHolder()
    var homePlatformAccentColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(bottomNavItems) {
        if (bottomNavItems.none { it.key == selectedPageKey }) {
            selectedPageKey = bottomNavItems.firstOrNull()?.key ?: "recommend"
        }
    }
    val selectedIndex = bottomNavItems.indexOfFirst { it.key == selectedPageKey }
        .takeIf { it >= 0 }
        ?: 0
    val selectedContent = BottomNavSelection(
        index = selectedIndex,
        key = bottomNavItems.getOrNull(selectedIndex)?.key ?: "recommend"
    )
    val bottomNavActiveColor = indexBottomNavActiveColor(
        homePlatformAccentColor = homePlatformAccentColor,
        defaultActiveColor = MaterialTheme.colorScheme.primary
    )
    val configuration = LocalConfiguration.current
    val hapticFeedback = LocalHapticFeedback.current
    val motionPolicy = LocalAppMotionPolicy.current
    val appLayout = LocalAppLayout.current
    val useSideNavigation = appLayout.useNavigationRail
    val storedTabletOrientationMode by
        viewModel.tabletOrientationMode.collectAsStateWithLifecycle()
    val tabletOrientationMode = if (
        appLayout.tablet
    ) {
        resolveTabletOrientationMode(
            storedMode = storedTabletOrientationMode,
            currentConfigurationOrientation = configuration.orientation
        )
    } else {
        null
    }
    val contentBottomPadding = indexTopLevelContentBottomPaddingDp(useSideNavigation).dp
    val homeLiveRoomGridColumns = appLayout.liveRoomColumns
    val followCardColumns = appLayout.followColumns
    val topRoute = navigator.backStack.lastOrNull()
    // When a non-Index route covers Index, skip heavy tab composition so Home/Follow
    // collectors and grids do not keep working under a live room.
    val composeIndexTabContent = shouldComposeIndexTabContent(topRoute)
    var sawLiveRoomOnTop by remember { mutableStateOf(false) }
    var suppressHomeInitialLoadingEffect by remember { mutableStateOf(false) }

    LaunchedEffect(topRoute) {
        when (topRoute) {
            is Route.LiveRoomDetail -> {
                sawLiveRoomOnTop = true
                suppressHomeInitialLoadingEffect = false
            }
            is Route.Index -> {
                if (sawLiveRoomOnTop) {
                    suppressHomeInitialLoadingEffect = true
                    sawLiveRoomOnTop = false
                }
            }
            else -> {
                sawLiveRoomOnTop = false
                suppressHomeInitialLoadingEffect = false
            }
        }
    }

    // Scroll-to-hide bottom bar state
    val maxOffsetPx = with(LocalDensity.current) { 100.dp.toPx() }
    var bottomBarOffsetPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(useSideNavigation) { bottomBarOffsetPx = 0f }

    val nestedScrollConnection = remember(maxOffsetPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                bottomBarOffsetPx = (bottomBarOffsetPx - delta).coerceIn(0f, maxOffsetPx)
                return Offset.Zero
            }
        }
    }

    fun handleMainTabClick(item: BottomNavItem) {
        performBottomTabSwitchHapticIfNeeded(
            currentKey = selectedPageKey,
            clickedKey = item.key,
            useSideNavigation = useSideNavigation
        ) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        val repeatAction = bottomTabRepeatAction(
            currentKey = selectedPageKey,
            clickedKey = item.key,
            isCurrentPageAtTop = true
        )
        selectedPageKey = item.key
        when (repeatAction) {
            BottomTabRepeatAction.Refresh -> when (item.key) {
                "recommend" -> homeRefreshSignal += 1
                "follow" -> followRefreshSignal += 1
                "category" -> categoryRefreshSignal += 1
            }
            BottomTabRepeatAction.None -> Unit
        }
    }

    Scaffold(
        modifier = if (useSideNavigation) Modifier else Modifier.nestedScroll(nestedScrollConnection),
        bottomBar = {
            if (!useSideNavigation) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = appLayout.pageMaxWidthDp.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .offset { IntOffset(x = 0, y = bottomBarOffsetPx.toInt()) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bottomNavItems.forEachIndexed { index, item ->
                            IndexNavigationItem(
                                item = item,
                                selected = selectedIndex == index,
                                activeColor = bottomNavActiveColor,
                                onClick = { handleMainTabClick(item) },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                }
            }
        }
    ) { paddingValues ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (useSideNavigation) {
                IndexSideNavigationRail(
                    items = bottomNavItems,
                    selectedIndex = selectedIndex,
                    activeColor = bottomNavActiveColor,
                    onItemClick = { handleMainTabClick(it) },
                    tabletOrientationMode = tabletOrientationMode,
                    onToggleTabletOrientation = tabletOrientationMode?.let { currentMode ->
                        {
                            viewModel.setTabletOrientationMode(
                                nextTabletOrientationMode(currentMode)
                            )
                        }
                    }
                )
            }

            AnimatedContent(
                targetState = selectedContent,
                transitionSpec = {
                    val direction = AppMotion.indexDirection(
                        fromIndex = initialState.index,
                        toIndex = targetState.index
                    )
                    adaptiveNavigationContentTransform(
                        direction = direction,
                        useSideNavigation = useSideNavigation,
                        motionPolicy = motionPolicy
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = paddingValues.calculateTopPadding()),
                label = "bottomNavContent"
            ) { selection ->
                val revealBottomBar = if (useSideNavigation) {
                    {}
                } else {
                    {
                        bottomBarOffsetPx = indexBottomBarOffsetAfterRevealRequest(bottomBarOffsetPx)
                    }
                }

                AdaptivePageFrame(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("index_screen_${selection.key}")
                ) {
                    if (composeIndexTabContent) {
                        tabStateHolder.SaveableStateProvider(selection.key) {
                            when (selection.key) {
                                "recommend" -> HomeScreen(
                                    navigator = navigator,
                                    suppressInitialLoadingEffect = suppressHomeInitialLoadingEffect,
                                    refreshSignal = homeRefreshSignal,
                                    onInitialLoadingEffectSettled = {
                                        suppressHomeInitialLoadingEffect = false
                                    },
                                    onPlatformAccentColorChange = {
                                        homePlatformAccentColor = it
                                    },
                                    onRevealBottomBar = revealBottomBar,
                                    contentBottomPadding = contentBottomPadding,
                                    homeLiveRoomGridColumns = homeLiveRoomGridColumns
                                )
                                "follow" -> FollowScreen(
                                    navigator = navigator,
                                    refreshSignal = followRefreshSignal,
                                    onRevealBottomBar = revealBottomBar,
                                    contentBottomPadding = contentBottomPadding,
                                    followCardColumns = followCardColumns
                                )
                                "category" -> CategoryScreen(
                                    navigator = navigator,
                                    refreshSignal = categoryRefreshSignal,
                                    contentBottomPadding = contentBottomPadding
                                )
                                "user" -> MineScreen(
                                    navigator = navigator,
                                    contentBottomPadding = contentBottomPadding
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun indexBottomNavActiveColor(
    homePlatformAccentColor: Color?,
    defaultActiveColor: Color
): Color {
    return homePlatformAccentColor ?: defaultActiveColor
}

internal fun indexBottomBarOffsetAfterRevealRequest(currentOffsetPx: Float): Float {
    return 0f
}

internal fun indexTopLevelContentBottomPaddingDp(useSideNavigation: Boolean): Int {
    return if (useSideNavigation) 24 else 96
}

@Composable
private fun IndexNavigationItem(
    item: BottomNavItem,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(item.labelRes)
    val selectionState = selected
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            activeColor
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        },
        label = "navColor"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) {
            activeColor.copy(alpha = 0.16f)
        } else {
            Color.Transparent
        },
        label = "navIndicator"
    )

    Column(
        modifier = modifier
            .testTag("index_nav_${item.key}")
            .semantics { this.selected = selectionState }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(28.dp)
                .background(indicatorColor, shape = RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            fontSize = if (LocalAppLayout.current.tablet) 12.sp else 10.sp
        )
    }
}

@Composable
private fun IndexSideNavigationRail(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    activeColor: Color,
    onItemClick: (BottomNavItem) -> Unit,
    tabletOrientationMode: TabletOrientationMode? = null,
    onToggleTabletOrientation: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(LocalAppLayout.current.navigationRailWidthDp.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 10.dp, top = 12.dp, bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEachIndexed { index, item ->
                    IndexNavigationItem(
                        item = item,
                        selected = selectedIndex == index,
                        activeColor = activeColor,
                        onClick = { onItemClick(item) },
                        modifier = Modifier
                            .heightIn(min = 64.dp)
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }

            if (tabletOrientationMode != null && onToggleTabletOrientation != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                TabletOrientationToggleButton(
                    currentMode = tabletOrientationMode,
                    onClick = onToggleTabletOrientation
                )
            }
        }
    }
}

@Composable
private fun TabletOrientationToggleButton(
    currentMode: TabletOrientationMode,
    onClick: () -> Unit
) {
    val targetMode = nextTabletOrientationMode(currentMode)
    val switchDescription = stringResource(
        if (currentMode == TabletOrientationMode.FollowSystem) {
            R.string.tablet_orientation_switch_from_system
        } else if (targetMode == TabletOrientationMode.Landscape) {
            R.string.tablet_orientation_switch_to_landscape
        } else {
            R.string.tablet_orientation_switch_to_portrait
        }
    )
    val actionLabel = stringResource(
        if (targetMode == TabletOrientationMode.Landscape) {
            R.string.tablet_orientation_action_landscape
        } else {
            R.string.tablet_orientation_action_portrait
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("tablet_orientation_toggle")
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = switchDescription,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(
                        if (targetMode == TabletOrientationMode.Landscape) 90f else 0f
                    )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = actionLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontSize = if (LocalAppLayout.current.tablet) 12.sp else 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
