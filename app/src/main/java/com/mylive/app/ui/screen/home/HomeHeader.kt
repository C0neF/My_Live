package com.mylive.app.ui.screen.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

@Composable
internal fun HomeTabletTopBar(
    items: List<HomePlatformItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val trackColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFEEEEF2)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        val searchWidth = when {
            maxWidth >= 980.dp -> 260.dp
            maxWidth >= 760.dp -> 210.dp
            else -> 164.dp
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (items.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .testTag("home_platform_bar")
                        .clip(RoundedCornerShape(10.dp))
                        .background(trackColor, RoundedCornerShape(10.dp))
                        .padding(3.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        HomeTabletPlatformCapsule(
                            item = item,
                            isSelected = selectedIndex == index,
                            isDark = isDark,
                            onClick = { onSelect(index) }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.testTag("home_search_header"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .width(searchWidth)
                        .heightIn(min = 36.dp)
                        .testTag("home_search")
                        .clip(RoundedCornerShape(10.dp))
                        .background(trackColor, RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSearchClick
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "搜索",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTabletPlatformCapsule(
    item: HomePlatformItem,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val motionPolicy = LocalAppMotionPolicy.current
    val activeThumb = if (isDark) Color(0xFF2C2C2E) else Color.White
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) activeThumb else Color.Transparent,
        animationSpec = motionPolicy.effectsSpec(),
        label = "tabletPlatformContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motionPolicy.effectsSpec(),
        label = "tabletPlatformContent"
    )
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 30.dp)
            .testTag("home_platform_${item.id}")
            .clip(shape)
            .background(containerColor, shape)
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.name.removeSuffix("直播").ifEmpty { item.name },
            color = contentColor,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            ),
            fontSize = 13.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeSearchHeader(
    scrollState: TopAppBarState,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val searchFill = if (isDark) Color(0xFF1C1C1E) else Color(0xFFEEEEF2)
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
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(14.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .testTag("home_search")
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        color = searchFill,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSearchClick
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.cd_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "搜索直播间或主播",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.5.sp,
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
            .padding(bottom = 4.dp)
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
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motionPolicy.effectsSpec(),
        label = "tabContentColor"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = motionPolicy.effectsSpec(),
        label = "tabIndicatorColor"
    )
    Column(
        modifier = modifier
            .heightIn(min = 42.dp)
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
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            ),
            fontSize = 14.5.sp,
            lineHeight = 19.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .height(2.dp)
                .width(16.dp)
                .background(indicatorColor, RoundedCornerShape(1.dp))
        )
    }
}
