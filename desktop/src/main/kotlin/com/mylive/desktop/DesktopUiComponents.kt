package com.mylive.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collect

private val DesktopIconButtonShape = RoundedCornerShape(9.dp)
private val DesktopActionButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
private val DesktopCompactButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

@Composable
internal fun DesktopSearchIcon() {
    Icon(Icons.Default.Search, null,
        Modifier.padding(start = 14.dp).size(19.dp).testTag("search-leading-icon"))
}

internal fun Modifier.desktopInteractiveSurface(
    shape: Shape,
    hoveredState: State<Boolean>,
    pressedState: State<Boolean>? = null,
    focusedState: State<Boolean>? = null,
    enabled: Boolean = true,
    baseColor: Color,
    hoverColor: Color = baseColor,
    pressColor: Color = hoverColor,
    baseBorderColor: Color = Color.Transparent,
    hoverBorderColor: Color = baseBorderColor,
    pressBorderColor: Color = hoverBorderColor,
    focusBorderColor: Color = hoverBorderColor,
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val strokePx = borderWidth.toPx()
        val stroke = Stroke(width = strokePx)
        onDrawBehind {
            val pressed = enabled && (pressedState?.value == true)
            val focused = enabled && focusedState?.value == true
            val hovered = enabled && (hoveredState.value || focused)
            val fill = when {
                pressed -> pressColor
                hovered -> hoverColor
                else -> baseColor
            }
            if (fill.alpha > 0f) {
                drawOutline(outline, color = fill)
            }
            val border = when {
                focused -> focusBorderColor
                pressed -> pressBorderColor
                hovered -> hoverBorderColor
                else -> baseBorderColor
            }
            if (border.alpha > 0f && strokePx > 0f) {
                drawOutline(outline, color = border, style = stroke)
            }
        }
    }

internal fun Modifier.desktopCardSurface(
    shape: Shape,
    hoveredState: State<Boolean>,
    focusedState: State<Boolean>,
    containerColor: Color,
    baseBorderColor: Color,
    highlightBorderColor: Color,
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val strokePx = borderWidth.toPx()
        val stroke = Stroke(width = strokePx)
        onDrawWithContent {
            if (containerColor.alpha > 0f) {
                drawOutline(outline, color = containerColor)
            }
            drawContent()
            val border = if (hoveredState.value || focusedState.value) highlightBorderColor else baseBorderColor
            if (border.alpha > 0f && strokePx > 0f) {
                drawOutline(outline, color = border, style = stroke)
            }
        }
    }
    // Reuse the card content when only its hover or focus border changes.
    .graphicsLayer()

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DesktopIconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    showTooltip: Boolean = true,
    onClick: () -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    val hoveredState = interactions.collectIsHoveredAsState()
    val pressedState = interactions.collectIsPressedAsState()
    val focusedState = interactions.collectIsFocusedAsState()
    val accent = UiAccent
    val elevated = UiElevated
    val hairlineStrong = UiHairlineStrong
    val baseColor = if (selected) accent.copy(alpha = .18f) else Color.Transparent
    val hoverColor = if (selected) accent.copy(alpha = .22f) else elevated
    val pressColor = if (selected) accent.copy(alpha = .26f) else accent.copy(alpha = .14f)
    val baseBorder = if (selected) accent.copy(alpha = .48f) else Color.Transparent
    val hoverBorder = if (selected) accent.copy(alpha = .58f) else hairlineStrong
    val iconTint = when {
        selected -> accent
        enabled -> UiInk
        else -> UiMuted.copy(alpha = .42f)
    }
    val buttonContent: @Composable () -> Unit = {
        Box(
            modifier = modifier
                .size(36.dp)
                .desktopInteractiveSurface(
                    shape = DesktopIconButtonShape,
                    hoveredState = hoveredState,
                    pressedState = pressedState,
                    focusedState = focusedState,
                    enabled = enabled,
                    baseColor = baseColor,
                    hoverColor = hoverColor,
                    pressColor = pressColor,
                    baseBorderColor = baseBorder,
                    hoverBorderColor = hoverBorder,
                    pressBorderColor = hoverBorder,
                    focusBorderColor = accent
                )
                .clickable(
                    interactionSource = interactions,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, Modifier.size(19.dp), tint = iconTint)
        }
    }
    if (showTooltip) {
        TooltipBox(
            TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(label) } },
            state = rememberTooltipState(),
            content = buttonContent
        )
    } else {
        buttonContent()
    }
}

@Composable
private fun DesktopBaseButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    minHeight: Dp,
    shape: Shape,
    baseColor: Color,
    hoverColor: Color,
    pressColor: Color,
    baseBorderColor: Color,
    hoverBorderColor: Color,
    pressBorderColor: Color = hoverBorderColor,
    contentColor: Color,
    textStyle: TextStyle,
    contentPadding: PaddingValues,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hoveredState = interaction.collectIsHoveredAsState()
    val pressedState = interaction.collectIsPressedAsState()
    val focusedState = interaction.collectIsFocusedAsState()
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        ProvideTextStyle(textStyle) {
            Row(
                modifier = modifier
                    .heightIn(min = minHeight)
                    .desktopInteractiveSurface(
                        shape = shape,
                        hoveredState = hoveredState,
                        pressedState = pressedState,
                        focusedState = focusedState,
                        enabled = enabled,
                        baseColor = baseColor,
                        hoverColor = hoverColor,
                        pressColor = pressColor,
                        baseBorderColor = baseBorderColor,
                        hoverBorderColor = hoverBorderColor,
                        pressBorderColor = pressBorderColor,
                        focusBorderColor = contentColor
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick
                    )
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
internal fun DesktopPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = DesktopActionButtonPadding,
    content: @Composable RowScope.() -> Unit
) {
    val accent = UiAccent
    val surface = UiSurface
    val hairline = UiHairline
    val baseStyle = MaterialTheme.typography.labelLarge
    val textStyle = remember(baseStyle) { baseStyle.copy(fontWeight = FontWeight.SemiBold) }
    DesktopBaseButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = 36.dp,
        shape = DesktopButtonShape,
        baseColor = if (enabled) accent else surface,
        hoverColor = accent.copy(alpha = .92f),
        pressColor = accent.copy(alpha = .82f),
        baseBorderColor = if (enabled) accent.copy(alpha = .7f) else hairline.copy(alpha = .6f),
        hoverBorderColor = accent.copy(alpha = .85f),
        pressBorderColor = accent.copy(alpha = .95f),
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else UiMuted.copy(alpha = .42f),
        textStyle = textStyle,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
internal fun DesktopTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = DesktopActionButtonPadding,
    content: @Composable RowScope.() -> Unit
) {
    val accent = UiAccent
    val isLight = MaterialTheme.colorScheme.background.luminance() > .5f
    DesktopBaseButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = 36.dp,
        shape = DesktopButtonShape,
        baseColor = if (enabled) accent.copy(alpha = .15f) else UiSurface.copy(alpha = .5f),
        hoverColor = accent.copy(alpha = .22f),
        pressColor = accent.copy(alpha = .28f),
        baseBorderColor = if (enabled) accent.copy(alpha = .40f) else UiHairline.copy(alpha = .6f),
        hoverBorderColor = accent.copy(alpha = .62f),
        contentColor = when {
            !enabled -> UiMuted.copy(alpha = .42f)
            isLight -> accent
            else -> UiInk
        },
        textStyle = MaterialTheme.typography.labelLarge,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
internal fun DesktopOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = DesktopActionButtonPadding,
    content: @Composable RowScope.() -> Unit
) {
    val accent = UiAccent
    DesktopBaseButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = 34.dp,
        shape = DesktopButtonShape,
        baseColor = if (enabled) UiSurface else Color.Transparent,
        hoverColor = UiElevated,
        pressColor = accent.copy(alpha = .15f),
        baseBorderColor = if (enabled) UiHairlineStrong else UiHairline.copy(alpha = .55f),
        hoverBorderColor = accent.copy(alpha = .50f),
        contentColor = if (enabled) UiInk else UiMuted.copy(alpha = .40f),
        textStyle = MaterialTheme.typography.labelLarge,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
internal fun DesktopTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = DesktopCompactButtonPadding,
    content: @Composable RowScope.() -> Unit
) {
    val accent = UiAccent
    val hairline = UiHairline
    DesktopBaseButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = 34.dp,
        shape = DesktopChipShape,
        baseColor = Color.Transparent,
        hoverColor = UiElevated,
        pressColor = accent.copy(alpha = .16f),
        baseBorderColor = Color.Transparent,
        hoverBorderColor = hairline,
        contentColor = if (enabled) UiInk.copy(alpha = .88f) else UiMuted.copy(alpha = .40f),
        textStyle = MaterialTheme.typography.labelLarge,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
internal fun DesktopChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    label: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hoveredState = interaction.collectIsHoveredAsState()
    val pressedState = interaction.collectIsPressedAsState()
    val accent = UiAccent
    val isLight = MaterialTheme.colorScheme.background.luminance() > .5f
    val baseColor = if (selected) accent.copy(alpha = .16f) else UiSurface
    val hoverColor = if (selected) accent.copy(alpha = .20f) else UiElevated
    val pressColor = if (selected) accent.copy(alpha = .24f) else accent.copy(alpha = .12f)
    val baseBorder = when {
        !enabled -> UiHairline.copy(alpha = .5f)
        selected -> accent.copy(alpha = .62f)
        else -> UiHairline
    }
    val hoverBorder = if (selected) accent.copy(alpha = .72f) else UiHairlineStrong
    val contentColor = when {
        !enabled -> UiMuted.copy(alpha = .42f)
        selected -> if (isLight) accent else UiInk
        else -> UiInk.copy(alpha = .82f)
    }
    val baseLabelStyle = MaterialTheme.typography.labelLarge
    val textStyle = remember(baseLabelStyle, selected) {
        baseLabelStyle.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .heightIn(min = 34.dp)
                .semantics {
                    role = Role.Checkbox
                    this.selected = selected
                }
                .desktopInteractiveSurface(
                    shape = DesktopChipShape,
                    hoveredState = hoveredState,
                    pressedState = pressedState,
                    enabled = enabled,
                    baseColor = baseColor,
                    hoverColor = hoverColor,
                    pressColor = pressColor,
                    baseBorderColor = baseBorder,
                    hoverBorderColor = hoverBorder,
                    pressBorderColor = hoverBorder
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onClick = onClick
                )
                .padding(start = 13.dp, top = 5.dp, end = 13.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (leading != null) Box(Modifier.offset(y = 1.dp)) { leading() }
            ProvideTextStyle(textStyle, content = label)
            if (trailing != null) Box(Modifier.offset(y = 1.dp)) { trailing() }
        }
    }
}

private val DesktopDialogTabShape = RoundedCornerShape(7.dp)

@Composable
internal fun DesktopDialogTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = UiSurface,
        border = BorderStroke(1.dp, UiHairline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                DesktopTabButton(
                    title = title,
                    selected = selectedIndex == index,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

@Composable
internal fun DesktopTabButton(
    title: String,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hoveredState = interaction.collectIsHoveredAsState()
    val pressedState = interaction.collectIsPressedAsState()
    val focusedState = interaction.collectIsFocusedAsState()
    val accent = UiAccent
    val baseColor = if (selected) accent.copy(alpha = .18f) else Color.Transparent
    val hoverColor = if (selected) accent.copy(alpha = .22f) else UiElevated
    val pressColor = if (selected) accent.copy(alpha = .26f) else accent.copy(alpha = .12f)
    val border = if (selected) accent.copy(alpha = .48f) else Color.Transparent
    val contentColor = when {
        !enabled -> UiMuted.copy(alpha = .42f)
        selected -> accent
        else -> UiMuted
    }
    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .desktopInteractiveSurface(
                shape = DesktopDialogTabShape,
                hoveredState = hoveredState,
                pressedState = pressedState,
                focusedState = focusedState,
                enabled = enabled,
                baseColor = baseColor,
                hoverColor = hoverColor,
                pressColor = pressColor,
                baseBorderColor = border,
                hoverBorderColor = border,
                pressBorderColor = border,
                focusBorderColor = accent
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
internal fun DesktopErrorBanner(message: String, action: String = "重试", onRetry: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .35f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .25f))) {
        Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            Text(message, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyMedium)
            DesktopTextButton(onRetry) { Text(action) }
        }
    }
}

@Composable
internal fun DesktopEmptyState(title: String, detail: String, icon: ImageVector, modifier: Modifier = Modifier,
    action: String? = null, onAction: (() -> Unit)? = null) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(58.dp).background(UiAccent.copy(alpha = .1f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(27.dp), tint = UiAccent)
            }
            Text(title, Modifier.padding(top = 18.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(detail, Modifier.padding(top = 8.dp), color = UiMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (action != null && onAction != null) DesktopOutlinedButton(onAction, Modifier.padding(top = 18.dp)) { Text(action) }
        }
    }
}

@Composable
internal fun DesktopCover(model: String, modifier: Modifier = Modifier) {
    var failed by remember(model) { mutableStateOf(model.isBlank()) }
    val elevated = UiElevated
    val surface = UiSurface
    val backgroundBrush = remember(elevated, surface) { Brush.linearGradient(listOf(elevated, surface)) }
    Box(modifier.background(backgroundBrush)) {
        if (failed) Icon(Icons.Default.LiveTv, null, Modifier.align(Alignment.Center).size(38.dp), tint = UiMuted.copy(alpha = .25f))
        else AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { failed = true })
    }
}

@Composable
internal fun DesktopRoomGrid(state: LazyGridState, modifier: Modifier = Modifier, minCardWidth: Dp = 232.dp, content: LazyGridScope.() -> Unit) {
    Box(modifier) {
        LazyVerticalGrid(GridCells.Adaptive(minCardWidth), Modifier.fillMaxSize().testTag("room-grid").padding(end = 14.dp), state = state,
            contentPadding = PaddingValues(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = defaultScrollbarStyle().copy(thickness = 6.dp, unhoverColor = UiMuted.copy(alpha = .22f), hoverColor = UiMuted.copy(alpha = .6f)))
    }
}

internal fun desktopPlatformName(id: String) = when (id) {
    "bilibili" -> "哔哩哔哩"
    "douyu" -> "斗鱼"
    "huya" -> "虎牙"
    "douyin" -> "抖音"
    else -> id
}

@Composable
internal fun DesktopLoadingScreen(message: String) {
    Surface(Modifier.fillMaxSize(), color = UiBg) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(52.dp).background(UiAccent, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Text(DESKTOP_APP_NAME, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
            CircularProgressIndicator(Modifier.padding(top = 22.dp).size(22.dp), strokeWidth = 2.dp)
            Text(message, color = UiMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
internal fun rememberDesktopGridState(sourceKey: String): LazyGridState {
    val positions = rememberSaveable(saver = DesktopGridPositionsSaver) { linkedMapOf<String, Pair<Int, Int>>() }
    val state = remember(sourceKey) {
        val position = positions[sourceKey] ?: (0 to 0)
        LazyGridState(position.first, position.second)
    }
    LaunchedEffect(sourceKey, state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { position ->
            positions.remove(sourceKey)
            positions[sourceKey] = position
            if (positions.size > 12) positions.remove(positions.keys.first())
        }
    }
    return state
}

private val DesktopGridPositionsSaver = listSaver<LinkedHashMap<String, Pair<Int, Int>>, Any>(
    save = { positions -> buildList {
        positions.forEach { (key, position) -> add(key); add(position.first); add(position.second) }
    } },
    restore = { saved -> linkedMapOf<String, Pair<Int, Int>>().apply {
        saved.chunked(3).forEach { put(it[0] as String, (it[1] as Int) to (it[2] as Int)) }
    } }
)
