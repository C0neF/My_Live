package com.mylive.app.ui.screen.category

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import com.mylive.app.core.model.LiveCategory
import com.mylive.app.core.model.LiveSubCategory
import com.mylive.app.ui.component.NetImage
import com.mylive.app.ui.component.status.ErrorState
import com.mylive.app.ui.component.status.LoadingState
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.theme.Icons
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    navigator: Navigator,
    refreshSignal: Int = 0,
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val siteTabs by viewModel.siteList.collectAsStateWithLifecycle()
    val selectedSiteIndex by viewModel.selectedSiteIndex.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { siteTabs.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0) {
            viewModel.refresh()
        }
    }

    fun selectCategorySite(index: Int) {
        if (siteTabs.isEmpty()) return
        val boundedIndex = index.coerceIn(0, siteTabs.lastIndex)
        if (selectedTab == boundedIndex) return
        selectedTab = boundedIndex
        viewModel.selectSite(boundedIndex)
    }

    // Sync pager swipes -> selectedTab
    LaunchedEffect(pagerState, siteTabs.size) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                selectCategorySite(page)
            }
    }

    // Guard against siteTabs shrinking
    LaunchedEffect(siteTabs.size) {
        if (siteTabs.isNotEmpty() && pagerState.currentPage > siteTabs.lastIndex) {
            val lastIndex = siteTabs.lastIndex
            pagerState.scrollToPage(lastIndex)
            selectCategorySite(lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.nav_category),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Platform selector chips — same as HomeScreen
        if (siteTabs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                siteTabs.forEachIndexed { siteIndex, site ->
                    CategoryPlatformChip(
                        name = categoryPlatformDisplayName(site.name),
                        isSelected = selectedTab == siteIndex,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        onClick = {
                            coroutineScope.launch {
                                val targetIndex = siteIndex.coerceIn(0, siteTabs.lastIndex)
                                if (pagerState.currentPage == targetIndex) {
                                    selectCategorySite(targetIndex)
                                    return@launch
                                }
                                AppMotion.preJumpPageForTarget(
                                    currentPage = pagerState.currentPage,
                                    targetPage = targetIndex
                                )?.let { preJumpPage ->
                                    pagerState.scrollToPage(preJumpPage)
                                }
                                val diff = targetIndex - pagerState.currentPage
                                pagerState.animateScrollToPage(
                                    page = targetIndex,
                                    animationSpec = AppMotion.pagerSpec(diff)
                                )
                            }
                        }
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.weight(1f)
        ) {
            // HorizontalPager — same pattern as HomeScreen
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = siteTabs.size > 1
            ) { page ->
                val selectedSite = siteTabs.getOrNull(page)

                // Use cached state for non-current pages (same as HomeScreen's HomeStateCache)
                val pageState = if (page == selectedTab) {
                    CategoryPageState(categories = categories, loading = loading, error = error)
                } else {
                    viewModel.getCachedState(page) ?: CategoryPageState(loading = true)
                }

                val pageCategories = pageState.categories
                val pageLoading = pageState.loading && pageCategories.isEmpty()
                val pageError = pageState.error

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        pageLoading -> LoadingState(modifier = Modifier.align(Alignment.Center))
                        pageError != null -> ErrorState(
                            message = pageError ?: "加载失败",
                            onRetry = { viewModel.retry() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                        pageCategories.isEmpty() -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.category_no_categories),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            CategoryList(
                                categories = pageCategories,
                                onSubCategoryClick = { subCategory ->
                                    if (selectedSite != null) {
                                        navigator.navigate(
                                            Route.CategoryDetail(
                                                siteId = selectedSite.id,
                                                categoryId = subCategory.id,
                                                categoryName = subCategory.name
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPlatformChip(
    name: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        },
        label = "containerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "contentColor"
    )

    Box(
        modifier = modifier
            .background(containerColor, shape = RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = if (isSelected) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun CategoryList(
    categories: List<LiveCategory>,
    onSubCategoryClick: (LiveSubCategory) -> Unit
) {
    var selectedParentIndex by remember(categories) { mutableIntStateOf(0) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left: parent category tabs
        LazyColumn(
            modifier = Modifier
                .width(104.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
        ) {
            itemsIndexed(categories) { index, category ->
                val isSelected = selectedParentIndex == index

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    },
                    label = "parentTabTextColor"
                )

                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.background
                    } else {
                        Color.Transparent
                    },
                    label = "parentTabBgColor"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(backgroundColor)
                        .clickable { selectedParentIndex = index },
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(20.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)
                                )
                        )
                    }

                    Text(
                        text = category.name,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier
                            .padding(start = 12.dp, end = 8.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Right: subcategory grid
        val currentCategory = categories.getOrNull(selectedParentIndex)
        val subCategories = currentCategory?.children.orEmpty()

        if (subCategories.isEmpty() && currentCategory != null) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 8.dp,
                    end = 12.dp,
                    bottom = 96.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CategoryCard(
                        name = currentCategory.name,
                        imageUrl = null,
                        onClick = {
                            onSubCategoryClick(
                                LiveSubCategory(
                                    name = currentCategory.name,
                                    id = currentCategory.id,
                                    parentId = currentCategory.id
                                )
                            )
                        }
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 8.dp,
                    end = 12.dp,
                    bottom = 96.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subCategories) { subCategory ->
                    CategoryCard(
                        name = subCategory.name,
                        imageUrl = subCategory.pic,
                        onClick = { onSubCategoryClick(subCategory) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    name: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    NetImage(
                        url = imageUrl,
                        contentDescription = name,
                        size = 40.dp,
                        isCircle = true
                    )
                } else {
                    Icon(
                        imageVector = categoryFallbackIcon(name),
                        contentDescription = name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

internal fun categoryPlatformDisplayName(siteName: String): String {
    if (siteName == "哔哩哔哩直播") {
        return "哔哩哔哩"
    }
    return siteName
}

internal fun categoryFallbackIconKey(name: String): String {
    return when {
        name.contains("聊天") -> "chat"
        name.contains("音乐") -> "music"
        name.contains("游戏") -> "game"
        name.contains("棋牌") || name.contains("卡牌") -> "cards"
        name.contains("运动") -> "sport"
        name.contains("文化") -> "culture"
        name.contains("舞蹈") -> "dance"
        name.contains("角色") || name.contains("二次元") -> "role"
        name.contains("赛事") || name.contains("竞技") -> "trophy"
        name.contains("生活") -> "life"
        else -> "category"
    }
}

private fun categoryFallbackIcon(name: String): ImageVector {
    return when (categoryFallbackIconKey(name)) {
        "chat" -> Icons.Default.Chat
        "music" -> Icons.Default.Music
        "game" -> Icons.Default.Game
        "cards" -> Icons.Default.Cards
        "sport" -> Icons.Default.Sport
        "culture" -> Icons.Default.Culture
        "dance" -> Icons.Default.Dance
        "role" -> Icons.Default.Role
        "trophy" -> Icons.Default.Trophy
        "life" -> Icons.Default.Person
        else -> Icons.Default.Category
    }
}
