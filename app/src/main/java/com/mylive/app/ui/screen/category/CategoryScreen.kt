package com.mylive.app.ui.screen.category

import androidx.compose.animation.AnimatedContent
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
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.layout.readableFontScale
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.mylive.app.ui.component.status.CategoryListSkeleton
import com.mylive.app.ui.component.status.EmptyState
import com.mylive.app.ui.component.status.ErrorState
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import com.mylive.app.ui.motion.verticalContentTransform
import com.mylive.app.ui.theme.Icons
import com.mylive.app.ui.theme.livePlatformAccentColor
import com.mylive.app.ui.theme.livePlatformOnAccentColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    navigator: Navigator,
    refreshSignal: Int = 0,
    contentBottomPadding: Dp = 96.dp,
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val motionPolicy = LocalAppMotionPolicy.current
    val siteTabs by viewModel.siteList.collectAsStateWithLifecycle()
    val selectedSiteIndex by viewModel.selectedSiteIndex.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val selectedTab = selectedSiteIndex.coerceIn(0, siteTabs.lastIndex.coerceAtLeast(0))
    val latestSelectedTab by rememberUpdatedState(selectedTab)
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
        if (latestSelectedTab == boundedIndex) return
        viewModel.selectSite(boundedIndex)
    }

    LaunchedEffect(selectedTab, siteTabs.size) {
        if (siteTabs.isNotEmpty() && pagerState.currentPage != selectedTab) {
            pagerState.scrollToPage(selectedTab)
        }
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

    val isTablet = LocalAppLayout.current.tablet
    val handleSiteTabClick: (Int) -> Unit = { siteIndex ->
        coroutineScope.launch {
            val targetIndex = siteIndex.coerceIn(0, siteTabs.lastIndex)
            if (pagerState.currentPage == targetIndex) {
                selectCategorySite(targetIndex)
                return@launch
            }
            if (motionPolicy.reducedMotion) {
                pagerState.scrollToPage(targetIndex)
            } else {
                AppMotion.preJumpPageForTarget(
                    currentPage = pagerState.currentPage,
                    targetPage = targetIndex
                )?.let { preJumpPage ->
                    pagerState.scrollToPage(preJumpPage)
                }
                pagerState.animateScrollToPage(
                    page = targetIndex,
                    animationSpec = AppMotion.pagerSpec()
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isTablet) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.nav_category),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (siteTabs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        siteTabs.forEachIndexed { siteIndex, site ->
                            CategoryPlatformTab(
                                platformId = site.id,
                                name = categoryPlatformDisplayName(site.name).removeSuffix("直播"),
                                isSelected = selectedTab == siteIndex,
                                onClick = { handleSiteTabClick(siteIndex) }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { navigator.navigate(Route.Search) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.nav_category),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Segmented control style platform selector on phone.
            if (siteTabs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    siteTabs.forEachIndexed { siteIndex, site ->
                        CategoryPlatformTab(
                            platformId = site.id,
                            name = categoryPlatformDisplayName(site.name).removeSuffix("直播"),
                            isSelected = selectedTab == siteIndex,
                            modifier = Modifier.weight(1f),
                            onClick = { handleSiteTabClick(siteIndex) }
                        )
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshFromPull() },
            modifier = Modifier.weight(1f)
        ) {
            // HorizontalPager — same pattern as HomeScreen
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = siteTabs.size > 1
            ) { page ->
                val selectedSite = siteTabs.getOrNull(page)
                val pageAccentColor = categoryPlatformAccentColor(
                    selectedSite?.id.orEmpty()
                ) ?: MaterialTheme.colorScheme.primary

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
                        pageLoading -> CategoryListSkeleton(modifier = Modifier.fillMaxSize())
                        pageError != null -> ErrorState(
                            message = pageError ?: "加载失败",
                            onRetry = { viewModel.retry() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                        pageCategories.isEmpty() -> {
                            EmptyState(
                                message = stringResource(R.string.category_no_categories),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        else -> {
                            CategoryList(
                                categories = pageCategories,
                                accentColor = pageAccentColor,
                                contentBottomPadding = contentBottomPadding,
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
private fun CategoryPlatformTab(
    platformId: String,
    name: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    com.mylive.shared.ui.PlatformTab(
        name = name,
        isSelected = isSelected,
        accentColor = categoryPlatformAccentColor(platformId) ?: MaterialTheme.colorScheme.primary,
        modifier = modifier.testTag("category_platform_$platformId"),
        onClick = onClick
    )
}

@Composable
private fun CategoryList(
    categories: List<LiveCategory>,
    accentColor: Color,
    contentBottomPadding: Dp = 96.dp,
    onSubCategoryClick: (LiveSubCategory) -> Unit
) {
    var selectedParentIndex by rememberSaveable(categories) { mutableIntStateOf(0) }
    val isTablet = LocalAppLayout.current.tablet
    val motionPolicy = LocalAppMotionPolicy.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = if (isTablet) 16.dp else 0.dp)
    ) {
        // Left: parent category rail
        LazyColumn(
            modifier = Modifier
                .width(if (isTablet) 152.dp else 108.dp)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isTablet) 0.35f else 0.22f),
                    shape = if (isTablet) RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp) else RoundedCornerShape(0.dp)
                ),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(
                items = categories,
                key = { _, category -> category.id },
                contentType = { _, _ -> "category_parent" }
            ) { index, category ->
                val isSelected = selectedParentIndex == index

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                    },
                    animationSpec = motionPolicy.effectsSpec(),
                    label = "parentTabTextColor"
                )

                val backgroundColor = categoryParentTabBackgroundColor(
                    isSelected = isSelected,
                    selectedColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unselectedColor = Color.Transparent
                )
                val itemShape = RoundedCornerShape(10.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isTablet) 44.dp else 42.dp)
                        .clip(itemShape)
                        .background(backgroundColor, itemShape)
                        .clickable { selectedParentIndex = index },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = category.name,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = if (isTablet) 14.sp else 13.sp
                        ),
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Right: subcategory grid
        val boundedParentIndex = selectedParentIndex.coerceIn(0, categories.lastIndex.coerceAtLeast(0))
        AnimatedContent(
            targetState = boundedParentIndex,
            transitionSpec = {
                verticalContentTransform(
                    AppMotion.indexDirection(initialState, targetState),
                    motionPolicy
                )
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            label = "subCategoryGrid"
        ) { activeParentIndex ->
            val currentCategory = categories.getOrNull(activeParentIndex)
            val subCategories = currentCategory?.children.orEmpty()

            if (subCategories.isEmpty() && currentCategory != null) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(((if (LocalAppLayout.current.tablet) 136 else 100) * readableFontScale(LocalAppLayout.current.fontScale)).dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 8.dp,
                        end = 12.dp,
                        bottom = contentBottomPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(
                        key = currentCategory.id,
                        contentType = "category_current"
                    ) {
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
                    columns = GridCells.Adaptive(((if (LocalAppLayout.current.tablet) 136 else 100) * readableFontScale(LocalAppLayout.current.fontScale)).dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 8.dp,
                        end = 12.dp,
                        bottom = contentBottomPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                items(
                    items = subCategories,
                    key = { it.id },
                    contentType = { "category_subcategory" }
                ) { subCategory ->
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
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val isTablet = LocalAppLayout.current.tablet
    val badgeSize = if (isTablet) 60.dp else 52.dp
    val badgeShape = RoundedCornerShape(if (isTablet) 15.dp else 13.dp)

    Column(
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
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(badgeSize),
            shape = badgeShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!imageUrl.isNullOrBlank()) {
                    NetImage(
                        url = imageUrl,
                        modifier = Modifier.clip(badgeShape),
                        contentDescription = name,
                        size = badgeSize
                    )
                } else {
                    Icon(
                        imageVector = categoryFallbackIcon(name),
                        contentDescription = name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (isTablet) 26.dp else 22.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Normal,
                fontSize = if (isTablet) 12.5.sp else 11.5.sp
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

internal fun categoryPlatformDisplayName(siteName: String): String {
    if (siteName == "哔哩哔哩直播") {
        return "哔哩哔哩"
    }
    return siteName
}

internal fun categoryPlatformAccentColor(platformId: String): Color? {
    return livePlatformAccentColor(platformId)
}

internal fun categoryPlatformChipContainerColor(
    platformId: String,
    selectedContainerColor: Color,
    unselectedContainerColor: Color,
    isSelected: Boolean
): Color {
    if (!isSelected) {
        return unselectedContainerColor
    }
    return categoryPlatformAccentColor(platformId) ?: selectedContainerColor
}

internal fun categoryPlatformChipContentColor(
    platformId: String,
    selectedContentColor: Color,
    unselectedContentColor: Color,
    isSelected: Boolean
): Color {
    if (!isSelected) {
        return unselectedContentColor
    }
    return livePlatformOnAccentColor(platformId, selectedContentColor)
}

internal fun categoryParentTabBackgroundColor(
    isSelected: Boolean,
    selectedColor: Color,
    unselectedColor: Color
): Color {
    return if (isSelected) selectedColor else unselectedColor
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
