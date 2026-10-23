package com.mylive.app.ui.screen.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.R
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import com.mylive.app.ui.theme.Icons
import kotlin.math.roundToInt

internal data class HomePlatformItem(val id: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeSearchHeader(
    scrollState: TopAppBarState,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_search_header")
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val offset = scrollState.heightOffset.roundToInt()
                layout(placeable.width, (placeable.height + offset).coerceIn(0, placeable.height)) {
                    placeable.placeRelative(0, offset)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    val fraction = scrollState.collapsedFraction
                    scrollState.heightOffsetLimit = -size.height.toFloat()
                    scrollState.heightOffset = -size.height * fraction
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MyLive",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .testTag("home_search")
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSearchClick
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.cd_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "搜索直播间或主播...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun HomePlatformBar(
    items: List<HomePlatformItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_platform_bar")
            .padding(bottom = 8.dp)
            .height(IntrinsicSize.Min)
            .selectableGroup()
    ) {
        items.forEachIndexed { index, item ->
            HomePlatformTab(
                item = item,
                isSelected = selectedIndex == index,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun HomePlatformTab(
    item: HomePlatformItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val motionPolicy = LocalAppMotionPolicy.current
    val brandColor = homePlatformAccentColor(item.id) ?: MaterialTheme.colorScheme.primary
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        },
        animationSpec = motionPolicy.effectsSpec(),
        label = "tabContentColor"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (isSelected) brandColor else Color.Transparent,
        animationSpec = motionPolicy.effectsSpec(),
        label = "tabIndicatorColor"
    )
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("home_platform_${item.id}")
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = item.name,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .height(2.dp)
                .width(20.dp)
                .background(indicatorColor, RoundedCornerShape(1.dp))
        )
    }
}
