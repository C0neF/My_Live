package com.mylive.app.ui.screen.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import com.mylive.app.ui.component.LiveRoomCard
import com.mylive.app.ui.component.LiveRoomGridMinCellWidth
import com.mylive.app.ui.component.NetImage
import com.mylive.app.ui.component.status.EmptyState
import com.mylive.app.ui.component.status.ErrorState
import com.mylive.app.ui.component.status.LiveRoomGridSkeleton
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.motion.horizontalContentTransform
import com.mylive.app.ui.navigation.navigateToRoom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navigator: Navigator,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
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
                .imePadding()
        ) {
            // Premium Pill Search bar
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(52.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchText.trim().isNotEmpty()) {
                            viewModel.search(searchText.trim())
                        }
                    }
                )
            )

            // Platform Selector Chip LazyRow
            if (viewModel.siteTabs.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(viewModel.siteTabs) { index, site ->
                        val isSelected = selectedTab == index

                        // Color animations
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
                            modifier = Modifier
                                .height(36.dp)
                                .background(containerColor, shape = RoundedCornerShape(18.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        selectedTab = index
                                        viewModel.selectSite(index)
                                    }
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = site.name,
                                color = contentColor,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Search type toggle
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FilterChip(
                    selected = uiState.searchType == 0,
                    onClick = { viewModel.setSearchType(0) },
                    label = { Text(stringResource(R.string.search_type_room)) },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = uiState.searchType == 1,
                    onClick = { viewModel.setSearchType(1) },
                    label = { Text(stringResource(R.string.search_type_anchor)) }
                )
            }

            AnimatedContent(
                targetState = selectedTab to uiState.searchType,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val direction = if (initialState.first != targetState.first) {
                        AppMotion.indexDirection(initialState.first, targetState.first)
                    } else {
                        AppMotion.indexDirection(initialState.second, targetState.second)
                    }
                    horizontalContentTransform(direction)
                },
                label = "searchResultsContent"
            ) { contentState ->
                val animatedTab = contentState.first
                val animatedSearchType = contentState.second

                // Results
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.isLoading && uiState.rooms.isEmpty() && uiState.anchors.isEmpty() -> {
                            LiveRoomGridSkeleton(
                                columns = 2,
                                itemCount = 6,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        uiState.error != null -> {
                            ErrorState(
                                message = uiState.error ?: "",
                                onRetry = { viewModel.search(searchText) },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        animatedSearchType == 0 && uiState.rooms.isEmpty() -> {
                            EmptyState(
                                message = stringResource(R.string.search_no_results),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        animatedSearchType == 1 && uiState.anchors.isEmpty() -> {
                            EmptyState(
                                message = stringResource(R.string.search_no_results),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        animatedSearchType == 0 -> {
                            val currentSiteName = viewModel.siteTabs.getOrNull(animatedTab)?.name
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(LiveRoomGridMinCellWidth),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.rooms) { room ->
                                    LiveRoomCard(
                                        title = room.title,
                                        userName = room.userName,
                                        faceUrl = room.faceUrl,
                                        online = room.online,
                                        coverUrl = room.cover,
                                        siteName = currentSiteName,
                                        onClick = {
                                            val siteId = viewModel.siteTabs.getOrNull(animatedTab)?.id ?: ""
                                            navigator.navigateToRoom(siteId = siteId, roomId = room.roomId)
                                        }
                                    )
                                }
                            }
                        }
                        animatedSearchType == 1 -> {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.anchors) { anchor ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val siteId = viewModel.siteTabs.getOrNull(animatedTab)?.id ?: ""
                                                navigator.navigateToRoom(siteId = siteId, roomId = anchor.roomId)
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(48.dp)) {
                                                NetImage(
                                                    url = anchor.avatar,
                                                    contentDescription = anchor.userName,
                                                    size = 48.dp,
                                                    isCircle = true
                                                )
                                                if (anchor.liveStatus) {
                                                    Surface(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .size(12.dp),
                                                        shape = androidx.compose.foundation.shape.CircleShape,
                                                        color = MaterialTheme.colorScheme.error,
                                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface)
                                                    ) {}
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(16.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = anchor.userName,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = if (anchor.liveStatus) stringResource(R.string.follow_group_live)
                                                           else stringResource(R.string.follow_group_offline),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (anchor.liveStatus) MaterialTheme.colorScheme.error
                                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontWeight = if (anchor.liveStatus) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }

                                            if (anchor.liveStatus) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Go to Live",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
