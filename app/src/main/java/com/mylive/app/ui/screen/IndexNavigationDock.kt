package com.mylive.app.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import kotlin.math.roundToInt

private val DockEdgeMargin = 21.dp
private val DockPadding = 4.dp
private val DockShadowRoom = 8.dp

@Composable
internal fun indexNavigationDockHeight(): Dp = with(LocalDensity.current) {
    62.dp + (10.sp.toDp() - 10.dp).coerceAtLeast(0.dp)
}

@Composable
private fun indexNavigationDockBottomMargin(): Dp = with(LocalDensity.current) {
    maxOf(DockEdgeMargin, WindowInsets.navigationBars.getBottom(this).toDp())
}

@Composable
internal fun indexNavigationDockContentPadding(): Dp =
    DockShadowRoom + indexNavigationDockHeight() + indexNavigationDockBottomMargin()

internal data class NavigationDockGeometry(
    val pitch: Float,
    val lensWidth: Float,
    val inset: Float
)

/** Keeps the lens inside the bar even when destinations become narrower than 84 dp. */
internal fun navigationDockGeometry(span: Float, count: Int): NavigationDockGeometry {
    if (count <= 1) return NavigationDockGeometry(span, span, 0f)
    val pitch = (span - 8f).coerceAtLeast(0f) / count
    val lens = (pitch + 8f).coerceAtLeast(84f).coerceAtMost(span)
    val closedPitch = (span - lens) / (count - 1)
    return NavigationDockGeometry(closedPitch, lens, (lens - closedPitch) / 2f)
}

@Composable
internal fun IndexNavigationDock(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    activeColor: Color,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val height = indexNavigationDockHeight()
    val hapticFeedback = LocalHapticFeedback.current
    val bottomMargin = indexNavigationDockBottomMargin()
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    // Apple frosted tab bar feel: clean translucent surface, whisper-thin 0.5dp edge, borderless lens.
    val barColor = if (dark) Color(0xEB1C1C1E) else Color(0xF2FFFFFF)
    val barBorderColor = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val lensColor = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.055f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .padding(start = DockEdgeMargin, top = DockShadowRoom, end = DockEdgeMargin, bottom = bottomMargin),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = if (items.size < 5) (items.size * 86 + 16).dp else Dp.Infinity)
                .fillMaxWidth()
                .height(height)
        ) {
            val density = LocalDensity.current
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val reducedMotion = LocalAppMotionPolicy.current.reducedMotion
            val geometry = navigationDockGeometry((maxWidth - DockPadding * 2).value, items.size)
            val pitchPx = with(density) { geometry.pitch.dp.toPx() }
            val startInsetPx = with(density) { (DockPadding + geometry.inset.dp).toPx() }
            val widthPx = with(density) { maxWidth.toPx() }
            val currentItems by rememberUpdatedState(items)
            val currentOnItemClick by rememberUpdatedState(onItemClick)
            val currentSelectedIndex by rememberUpdatedState(selectedIndex)
            val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
            var dragPosition by remember { mutableStateOf<Float?>(null) }
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val targetPosition = dragPosition ?: selectedIndex.toFloat()
            val lensPosition = if (reducedMotion) {
                rememberUpdatedState(targetPosition)
            } else {
                animateFloatAsState(
                    targetValue = targetPosition,
                    animationSpec = spring(
                        dampingRatio = 0.80f,
                        stiffness = if (dragPosition != null) 1_024f else 400f
                    ),
                    label = "dockLensPosition"
                )
            }
            val lift = animateFloatAsState(
                targetValue = if (!reducedMotion && (pressed || dragPosition != null)) 1f else 0f,
                animationSpec = if (reducedMotion) snap() else spring(
                    dampingRatio = 0.70f,
                    stiffness = 320f
                ),
                label = "dockPressLift"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("index_navigation_dock")
                    .graphicsLayer {
                        scaleX = 1f + lift.value * 0.03f
                        scaleY = 1f + lift.value * 0.03f
                        shape = CircleShape
                        shadowElevation = 10.dp.toPx()
                        ambientShadowColor = Color.Black.copy(alpha = if (dark) 0.28f else 0.07f)
                        spotShadowColor = Color.Black.copy(alpha = if (dark) 0.16f else 0.04f)
                    }
                    .background(barColor, CircleShape)
                    .border(0.5.dp, barBorderColor, CircleShape)
                    .selectableGroup()
                    .pointerInput(geometry, rtl, widthPx) {
                        if (pitchPx <= 0f) return@pointerInput
                        var hoveredIndex = currentSelectedIndex
                        fun updateDragPosition(pointerX: Float) {
                            val x = if (rtl) widthPx - pointerX else pointerX
                            val position = ((x - startInsetPx) / pitchPx - 0.5f)
                                .coerceIn(0f, currentItems.lastIndex.toFloat())
                            dragPosition = position
                            val index = position.roundToInt()
                            if (index != hoveredIndex) {
                                hoveredIndex = index
                                currentHapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { point ->
                                hoveredIndex = currentSelectedIndex
                                updateDragPosition(point.x)
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                updateDragPosition(change.position.x)
                            },
                            onDragEnd = {
                                val index = dragPosition?.roundToInt()
                                dragPosition = null
                                index?.let { currentItems.getOrNull(it) }?.let(currentOnItemClick)
                            },
                            onDragCancel = { dragPosition = null }
                        )
                    }
            ) {
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                x = with(density) {
                                    (DockPadding + geometry.pitch.dp * lensPosition.value).roundToPx()
                                },
                                y = with(density) { DockPadding.roundToPx() }
                            )
                        }
                        .width(geometry.lensWidth.dp)
                        .height(height - DockPadding * 2)
                        .testTag("index_navigation_lens")
                        .graphicsLayer {
                            scaleX = 1f + lift.value * 0.06f
                            scaleY = 1f + lift.value * 0.06f
                        }
                        .background(lensColor, CircleShape)
                )
                items.forEachIndexed { index, item ->
                    DockDestination(
                        item = item,
                        selected = selectedIndex == index,
                        activeColor = activeColor,
                        interactionSource = interactionSource,
                        onClick = {
                            performBottomTabSwitchHapticIfNeeded(
                                currentKey = items.getOrNull(selectedIndex)?.key.orEmpty(),
                                clickedKey = item.key,
                                useSideNavigation = false
                            ) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onItemClick(item)
                        },
                        modifier = Modifier
                            .offset(x = DockPadding + geometry.inset.dp + geometry.pitch.dp * index)
                            .width(geometry.pitch.dp)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun DockDestination(
    item: BottomNavItem,
    selected: Boolean,
    activeColor: Color,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val label = stringResource(item.labelRes)
    val motionPolicy = LocalAppMotionPolicy.current
    val color by animateColorAsState(
        targetValue = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motionPolicy.effectsSpec(),
        label = "dockDestinationColor"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected && !motionPolicy.reducedMotion) 1.06f else 1f,
        animationSpec = if (motionPolicy.reducedMotion) snap() else motionPolicy.contentSpec(),
        label = "dockDestinationScale"
    )
    Column(
        modifier = modifier
            .testTag("index_nav_${item.key}")
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 10.sp,
                lineHeight = 14.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
