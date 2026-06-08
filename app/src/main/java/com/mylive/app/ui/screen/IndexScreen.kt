package com.mylive.app.ui.screen

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.graphicsLayer
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
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .offset { IntOffset(x = 0, y = bottomBarOffsetPx.toInt()) },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bottomNavItems.forEachIndexed { index, item ->
                        val label = stringResource(item.labelRes)
                        val isSelected = selectedIndex == index

                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                            label = "navColor"
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.1f else 1.0f,
                            animationSpec = AppMotion.contentSpec(),
                            label = "navScale"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        selectedPageKey = item.key
                                    }
                                )
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                color = contentColor,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
                        onInitialLoadingEffectSettled = {
                            suppressHomeInitialLoadingEffect = false
                        }
                    )
                    "follow" -> FollowScreen(navigator = navigator)
                    "category" -> CategoryScreen(navigator = navigator)
                    "user" -> MineScreen(navigator = navigator)
                }
            }
        }
    }
}
