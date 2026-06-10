package com.mylive.app.ui.component.status

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * Skeleton placeholder that mirrors the flat LiveRoomCard: bare cover (no container),
 * two reserved title lines, and a small avatar row — so loading→loaded doesn't reflow.
 */
@Composable
fun LiveRoomCardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.777f)
                .clip(RoundedCornerShape(12.dp))
                .background(shimmerBrush())
        ) {
            // Viewer count badge placeholder (bottom-end).
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomEnd)
                    .width(56.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Two reserved title lines (mirrors minLines = 2 on the real card).
        SkeletonLine(height = 14, modifier = Modifier.padding(horizontal = 2.dp))
        Spacer(modifier = Modifier.height(9.dp))
        SkeletonLine(
            widthFraction = 0.65f,
            height = 14,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        Spacer(modifier = Modifier.height(5.dp))

        // Streamer row: small avatar + name.
        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonCircle(size = 20)
            Spacer(modifier = Modifier.width(6.dp))
            SkeletonLine(widthFraction = 0.4f, height = 11)
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
