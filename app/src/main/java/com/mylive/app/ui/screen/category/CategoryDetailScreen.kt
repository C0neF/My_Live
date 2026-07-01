package com.mylive.app.ui.screen.category

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import com.mylive.app.ui.component.LiveRoomCard
import com.mylive.app.ui.component.LiveRoomGridMinCellWidth
import com.mylive.app.ui.component.status.ErrorState
import com.mylive.app.ui.component.status.LiveRoomGridSkeleton
import com.mylive.app.ui.navigation.navigateToRoom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    navigator: Navigator,
    key: Route.CategoryDetail
) {
    val viewModel: CategoryDetailViewModel = hiltViewModel()
    LaunchedEffect(key) {
        viewModel.init(
            siteId = key.siteId,
            categoryId = key.categoryId,
            categoryName = key.categoryName
        )
    }
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var isExiting by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (!isExiting) {
            isExiting = true
            navigator.goBack()
        }
    }

    BackHandler(enabled = !isExiting) {
        handleBack()
    }

    val gridState = rememberLazyGridState()

    // Detect scroll to bottom for load more
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= rooms.size - 4 && hasMore && !loading && !loadingMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && rooms.isNotEmpty()) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.categoryName) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
        when {
            loading && rooms.isEmpty() -> {
                LiveRoomGridSkeleton(
                    columns = 2,
                    itemCount = 6
                )
            }
            error != null && rooms.isEmpty() -> ErrorState(
                message = error ?: "加载失败",
                onRetry = { viewModel.retry() }
            )
            rooms.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.category_no_rooms),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                val currentSiteName = when (viewModel.siteId.lowercase()) {
                    "bilibili" -> "Bilibili"
                    "douyu" -> "斗鱼"
                    "huya" -> "虎牙"
                    else -> viewModel.siteId.replaceFirstChar { it.uppercase() }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(LiveRoomGridMinCellWidth),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = rooms,
                        key = { it.roomId },
                        contentType = { "category_detail_room" }
                    ) { room ->
                        LiveRoomCard(
                            title = room.title,
                            userName = room.userName,
                            faceUrl = room.faceUrl,
                            online = room.online,
                            coverUrl = room.cover,
                            siteName = currentSiteName,
                            onClick = {
                                navigator.navigateToRoom(
                                    siteId = viewModel.siteId,
                                    roomId = room.roomId
                                )
                            }
                        )
                    }

                    // Loading more indicator
                    if (loadingMore) {
                        item(contentType = "category_detail_loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
