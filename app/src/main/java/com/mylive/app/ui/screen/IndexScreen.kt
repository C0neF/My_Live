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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.R
import com.mylive.app.ui.motion.adaptiveNavigationContentTransform
import com.mylive.app.ui.screen.category.CategoryScreen
import com.mylive.app.ui.screen.follow.FollowScreen
import com.mylive.app.ui.screen.follow.followCardGridColumns
import com.mylive.app.ui.screen.home.HomeScreen
import com.mylive.app.ui.screen.mine.MineScreen
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.navigation.Route
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
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
    val useSideNavigation = indexUseSideNavigation(configuration.screenWidthDp)
    val contentBottomPadding = indexTopLevelContentBottomPaddingDp(useSideNavigation).dp
    val homeLiveRoomGridColumns = indexHomeLiveRoomGridColumns(configuration.screenWidthDp)
    val followCardColumns = followCardGridColumns(useSideNavigation)
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
                Surface(
                    modifier = Modifier
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
    ) { paddingValues ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (useSideNavigation) {
                IndexSideNavigationRail(
                    items = bottomNavItems,
                    selectedIndex = selectedIndex,
                    activeColor = bottomNavActiveColor,
                    onItemClick = { handleMainTabClick(it) }
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
                        useSideNavigation = useSideNavigation
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

                Box(modifier = Modifier.fillMaxSize()) {
                    if (composeIndexTabContent) {
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

internal fun indexBottomNavActiveColor(
    homePlatformAccentColor: Color?,
    defaultActiveColor: Color
): Color {
    return homePlatformAccentColor ?: defaultActiveColor
}

internal fun indexBottomBarOffsetAfterRevealRequest(currentOffsetPx: Float): Float {
    return 0f
}

internal fun indexUseSideNavigation(screenWidthDp: Int): Boolean {
    return screenWidthDp >= 600
}

internal fun indexTopLevelContentBottomPaddingDp(useSideNavigation: Boolean): Int {
    return if (useSideNavigation) 24 else 96
}

internal fun indexHomeLiveRoomGridColumns(screenWidthDp: Int): Int? {
    return when {
        screenWidthDp < 600 -> null
        screenWidthDp < 840 -> 3
        screenWidthDp < 1200 -> 4
        else -> 5
    }
}

internal fun indexSideNavigationRailWidthDp(): Int {
    return 96
}

internal fun indexSideNavigationItemWeight(itemCount: Int): Float {
    return if (itemCount > 0) 1f else 1f
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
        modifier = modifier.clickable(
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
            fontSize = 10.sp
        )
    }
}

@Composable
private fun IndexSideNavigationRail(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    activeColor: Color,
    onItemClick: (BottomNavItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(indexSideNavigationRailWidthDp().dp)
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround
        ) {
            items.forEachIndexed { index, item ->
                IndexNavigationItem(
                    item = item,
                    selected = selectedIndex == index,
                    activeColor = activeColor,
                    onClick = { onItemClick(item) },
                    modifier = Modifier
                        .weight(indexSideNavigationItemWeight(items.size))
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}
