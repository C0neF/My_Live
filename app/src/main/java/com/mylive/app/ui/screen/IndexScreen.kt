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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.R
import com.mylive.app.ui.motion.horizontalContentTransform
import com.mylive.app.ui.screen.category.CategoryScreen
import com.mylive.app.ui.screen.follow.FollowScreen
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
    val topRoute = navigator.backStack.lastOrNull()
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
    val density = LocalContext.current.resources.displayMetrics.density
    val maxOffsetPx = 100f * density
    var bottomBarOffsetPx by remember { mutableStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                bottomBarOffsetPx = (bottomBarOffsetPx - delta).coerceIn(0f, maxOffsetPx)
                return Offset.Zero
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(nestedScrollConnection),
        bottomBar = {
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
                        val label = stringResource(item.labelRes)
                        val isSelected = selectedIndex == index

                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                bottomNavActiveColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            },
                            label = "navColor"
                        )
                        // Active destination gets an M3-style pill behind the icon
                        // instead of a scale bump — calmer and more legible.
                        val indicatorColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                bottomNavActiveColor.copy(alpha = 0.16f)
                            } else {
                                Color.Transparent
                            },
                            label = "navIndicator"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
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
                                )
                                .padding(vertical = 2.dp),
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
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = selectedContent,
            transitionSpec = {
                val direction = AppMotion.indexDirection(
                    fromIndex = initialState.index,
                    toIndex = targetState.index
                )
                horizontalContentTransform(direction)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            label = "bottomNavContent"
        ) { selection ->
            Box(modifier = Modifier.fillMaxSize()) {
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
                        onRevealBottomBar = {
                            bottomBarOffsetPx = indexBottomBarOffsetAfterRevealRequest(bottomBarOffsetPx)
                        }
                    )
                    "follow" -> FollowScreen(
                        navigator = navigator,
                        refreshSignal = followRefreshSignal,
                        onRevealBottomBar = {
                            bottomBarOffsetPx = indexBottomBarOffsetAfterRevealRequest(bottomBarOffsetPx)
                        }
                    )
                    "category" -> CategoryScreen(
                        navigator = navigator,
                        refreshSignal = categoryRefreshSignal
                    )
                    "user" -> MineScreen(navigator = navigator)
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
