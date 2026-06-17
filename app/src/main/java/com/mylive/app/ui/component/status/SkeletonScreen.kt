package com.mylive.app.ui.component.status

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shimmer animation brush for skeleton screens.
 */
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )
}

/**
 * A single skeleton line placeholder.
 */
@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Int = 16
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(shimmerBrush())
    )
}

/**
 * A circular skeleton placeholder (e.g., for avatars).
 */
@Composable
fun SkeletonCircle(
    modifier: Modifier = Modifier,
    size: Int = 48
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(shimmerBrush())
    )
}

/**
 * A rectangular skeleton placeholder (e.g., for images).
 */
@Composable
fun SkeletonRectangle(
    modifier: Modifier = Modifier,
    height: Int = 120,
    cornerRadius: Int = 8
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(shimmerBrush())
    )
}

/**
 * Skeleton screen that matches the LiveRoomCard layout.
 * Shows shimmer placeholders for: cover image, avatar, title, username.
 */
@Composable
fun LiveRoomCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column {
            // Cover image skeleton (16:9 aspect ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.777f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(shimmerBrush())
            ) {
                // Bottom gradient overlay placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f))
                            )
                        )
                )

                // Viewer count badge placeholder (bottom-end)
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.BottomEnd)
                        .width(60.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                )
            }

            // Avatar + text skeleton
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar circle
                SkeletonCircle(size = 36)
                Spacer(modifier = Modifier.width(10.dp))
                // Title + username
                Column(modifier = Modifier.weight(1f)) {
                    SkeletonLine(height = 14)
                    Spacer(modifier = Modifier.height(6.dp))
                    SkeletonLine(widthFraction = 0.5f, height = 12)
                }
            }
        }
    }
}

/**
 * Grid skeleton for live room cards.
 * Matches the LazyVerticalGrid layout used in HomeScreen, CategoryDetailScreen, SearchScreen.
 */
@Composable
fun LiveRoomGridSkeleton(
    modifier: Modifier = Modifier,
    columns: Int = 2,
    itemCount: Int = 6
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(itemCount) {
            LiveRoomCardSkeleton()
        }
    }
}

/**
 * Search result skeleton that keeps the same metadata rhythm as room search cards.
 */
@Composable
fun SearchRoomCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.777f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(shimmerBrush())
            ) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.BottomEnd)
                        .width(58.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.18f))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonCircle(size = 36)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SkeletonLine(height = 14)
                    Spacer(modifier = Modifier.height(6.dp))
                    SkeletonLine(widthFraction = 0.6f, height = 12)
                }
            }
        }
    }
}

/**
 * Search-specific grid skeleton that shares spacing and adaptive columns with real results.
 */
@Composable
fun SearchRoomGridSkeleton(
    minCellWidth: Dp,
    modifier: Modifier = Modifier,
    itemCount: Int = 6
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(itemCount) {
            SearchRoomCardSkeleton()
        }
    }
}

/**
 * Search anchor skeleton that matches the full-width anchor result row.
 */
@Composable
fun SearchAnchorItemSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonCircle(size = 48)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                SkeletonLine(widthFraction = 0.45f, height = 16)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonLine(widthFraction = 0.28f, height = 12)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(shimmerBrush())
            )
        }
    }
}

/**
 * Search-specific list skeleton for anchor results.
 */
@Composable
fun SearchAnchorListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 8
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(itemCount) {
            SearchAnchorItemSkeleton()
        }
    }
}
