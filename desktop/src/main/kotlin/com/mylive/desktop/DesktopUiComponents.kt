package com.mylive.desktop

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collect

private val DesktopIconButtonShape = RoundedCornerShape(9.dp)
private val DesktopActionButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
private val DesktopCompactButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

internal val DesktopStandardEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

internal fun desktopExpandEnter(): EnterTransition =
    expandVertically(
        animationSpec = tween(durationMillis = 200, easing = DesktopStandardEasing),
        expandFrom = Alignment.Top
    ) + fadeIn(animationSpec = tween(durationMillis = 170, easing = LinearOutSlowInEasing))

internal fun desktopCollapseExit(): ExitTransition =
    shrinkVertically(
        animationSpec = tween(durationMillis = 170, easing = DesktopStandardEasing),
        shrinkTowards = Alignment.Top
    ) + fadeOut(animationSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing))

internal fun desktopPopoverEnter(): EnterTransition =
    scaleIn(
        initialScale = 0.95f,
        animationSpec = tween(durationMillis = 190, easing = DesktopStandardEasing)
    ) + slideInVertically(
        animationSpec = tween(durationMillis = 190, easing = DesktopStandardEasing),
        initialOffsetY = { it / 5 }
    ) + fadeIn(animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing))

internal fun desktopPopoverExit(): ExitTransition =
    scaleOut(
        targetScale = 0.95f,
        animationSpec = tween(durationMillis = 150, easing = DesktopStandardEasing)
    ) + slideOutVertically(
        animationSpec = tween(durationMillis = 150, easing = DesktopStandardEasing),
        targetOffsetY = { it / 5 }
    ) + fadeOut(animationSpec = tween(durationMillis = 120, easing = FastOutLinearInEasing))

internal fun <S> AnimatedContentTransitionScope<S>.desktopDirectionalTabTransform(
    direction: Int
): ContentTransform {
    if (direction == 0) {
        return (fadeIn(tween(170, easing = LinearOutSlowInEasing)) togetherWith
            fadeOut(tween(120, easing = FastOutLinearInEasing)))
            .using(SizeTransform(clip = false))
    }
    return ((slideInHorizontally(
        animationSpec = tween(durationMillis = 210, easing = DesktopStandardEasing),
        initialOffsetX = { fullWidth -> (fullWidth / 14).coerceIn(12, 32) * if (direction > 0) 1 else -1 }
    ) + fadeIn(tween(durationMillis = 170, easing = LinearOutSlowInEasing))) togetherWith
        (slideOutHorizontally(
            animationSpec = tween(durationMillis = 160, easing = DesktopStandardEasing),
            targetOffsetX = { fullWidth -> (fullWidth / 18).coerceIn(10, 24) * if (direction > 0) -1 else 1 }
        ) + fadeOut(tween(durationMillis = 120, easing = FastOutLinearInEasing))))
        .using(SizeTransform(clip = false))
}

@Composable
internal fun DesktopSearchIcon() {
    Icon(
        Icons.Default.Search,
        null,
        Modifier.size(17.dp).testTag("search-leading-icon"),
        tint = UiMuted
    )
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
    borderWidth: Dp = DesktopHairlineWidth
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
    borderWidth: Dp = DesktopHairlineWidth
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
    val ink = UiInk
    val baseColor = if (selected) accent.copy(alpha = .16f) else Color.Transparent
    val hoverColor = if (selected) accent.copy(alpha = .22f) else ink.copy(alpha = .08f)
    val pressColor = if (selected) accent.copy(alpha = .28f) else ink.copy(alpha = .14f)
    val iconTint = when {
        selected -> accent
        enabled -> ink
        else -> UiMuted.copy(alpha = .40f)
    }
    val buttonContent: @Composable () -> Unit = {
        Box(
            modifier = modifier
                .size(34.dp)
                .desktopInteractiveSurface(
                    shape = DesktopIconButtonShape,
                    hoveredState = hoveredState,
                    pressedState = pressedState,
                    focusedState = focusedState,
                    enabled = enabled,
                    baseColor = baseColor,
                    hoverColor = hoverColor,
                    pressColor = pressColor,
                    baseBorderColor = Color.Transparent,
                    hoverBorderColor = Color.Transparent,
                    pressBorderColor = Color.Transparent,
                    focusBorderColor = accent.copy(alpha = .7f)
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
            Icon(icon, label, Modifier.size(18.dp), tint = iconTint)
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
    val accent = UiAccent
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
                        focusBorderColor = accent
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
    val baseStyle = MaterialTheme.typography.labelLarge
    val textStyle = remember(baseStyle) { baseStyle.copy(fontWeight = FontWeight.SemiBold) }
    DesktopBaseButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = 35.dp,
        shape = DesktopButtonShape,
        baseColor = if (enabled) accent else UiFieldFill,
        hoverColor = accent.copy(alpha = .90f),
        pressColor = accent.copy(alpha = .80f),
        baseBorderColor = if (enabled) Color.White.copy(alpha = .14f) else Color.Transparent,
        hoverBorderColor = Color.White.copy(alpha = .20f),
        pressBorderColor = Color.Transparent,
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
        minHeight = 35.dp,
        shape = DesktopButtonShape,
        baseColor = if (enabled) accent.copy(alpha = .14f) else UiFieldFill.copy(alpha = .6f),
        hoverColor = accent.copy(alpha = .20f),
        pressColor = accent.copy(alpha = .26f),
        baseBorderColor = Color.Transparent,
        hoverBorderColor = Color.Transparent,
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < .5f
    val restFill = if (isDark) UiElevated else UiSurface
    val hoverFill = if (isDark) Color(0xff36363a) else UiElevated
    DesktopBaseButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = 34.dp,
        shape = DesktopButtonShape,
        baseColor = if (enabled) restFill else UiFieldFill.copy(alpha = .45f),
        hoverColor = hoverFill,
        pressColor = UiInk.copy(alpha = .12f),
        baseBorderColor = if (enabled) UiHairlineStrong.copy(alpha = .55f) else UiHairline.copy(alpha = .35f),
        hoverBorderColor = UiHairlineStrong.copy(alpha = .75f),
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
    DesktopBaseButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = 33.dp,
        shape = DesktopButtonShape,
        baseColor = Color.Transparent,
        hoverColor = UiInk.copy(alpha = .07f),
        pressColor = UiInk.copy(alpha = .12f),
        baseBorderColor = Color.Transparent,
        hoverBorderColor = Color.Transparent,
        contentColor = if (enabled) UiAccent else UiMuted.copy(alpha = .40f),
        textStyle = MaterialTheme.typography.labelLarge,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
internal fun DesktopTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = DesktopFieldShape
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val accent = UiAccent
    val errorColor = MaterialTheme.colorScheme.error
    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> UiFieldFill.copy(alpha = .5f)
            focused -> UiSurface
            else -> UiFieldFill
        },
        animationSpec = tween(durationMillis = 150, easing = DesktopStandardEasing)
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> errorColor
            focused -> accent
            hovered && enabled -> UiHairlineStrong.copy(alpha = .7f)
            else -> UiHairline.copy(alpha = .65f)
        },
        animationSpec = tween(durationMillis = 150, easing = DesktopStandardEasing)
    )
    val borderWidth = if (focused || isError) 1.5.dp else DesktopHairlineWidth
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = if (enabled) UiInk else UiMuted.copy(alpha = .55f)
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        cursorBrush = SolidColor(if (isError) errorColor else accent),
        decorationBox = { innerTextField ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (singleLine) 38.dp else 72.dp)
                    .clip(shape)
                    .background(fill, shape)
                    .border(borderWidth, borderColor, shape)
                    .padding(
                        horizontal = if (leadingIcon != null) 10.dp else 12.dp,
                        vertical = if (singleLine) 6.dp else 9.dp
                    ),
                verticalArrangement = Arrangement.Center
            ) {
                if (label != null) {
                    CompositionLocalProvider(LocalContentColor provides if (focused) accent else UiMuted) {
                        ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                            Box(Modifier.padding(bottom = 2.dp)) { label() }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (leadingIcon != null) {
                        CompositionLocalProvider(LocalContentColor provides UiMuted) {
                            leadingIcon()
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
                    ) {
                        if (value.isEmpty() && placeholder != null) {
                            CompositionLocalProvider(LocalContentColor provides UiMuted.copy(alpha = .75f)) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                    placeholder()
                                }
                            }
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        CompositionLocalProvider(LocalContentColor provides UiMuted) {
                            trailingIcon()
                        }
                    }
                }
            }
        }
    )
}

@Composable
internal fun DesktopSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val accent = UiAccent
    val isDark = MaterialTheme.colorScheme.background.luminance() < .5f
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled && checked -> accent.copy(alpha = .38f)
            !enabled -> UiInk.copy(alpha = .08f)
            checked -> accent
            isDark -> Color(0xff39393d)
            else -> Color(0xffdddddf)
        },
        animationSpec = tween(durationMillis = 170, easing = DesktopStandardEasing)
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 2.dp,
        animationSpec = tween(durationMillis = 180, easing = DesktopStandardEasing)
    )
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(CircleShape)
            .background(trackColor),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.White.copy(alpha = .7f))
        )
    }
}

@Composable
internal fun DesktopThinProgressBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    color: Color = UiAccent
) {
    Box(modifier.fillMaxWidth().height(2.dp)) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(durationMillis = 150, easing = LinearOutSlowInEasing)),
            exit = fadeOut(tween(durationMillis = 200, easing = FastOutLinearInEasing))
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = color,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
internal fun DesktopChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = DesktopChipShape,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    label: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hoveredState = interaction.collectIsHoveredAsState()
    val pressedState = interaction.collectIsPressedAsState()
    val accent = UiAccent
    val isLight = MaterialTheme.colorScheme.background.luminance() > .5f
    val baseColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = .15f) else UiFieldFill,
        animationSpec = tween(durationMillis = 160, easing = DesktopStandardEasing)
    )
    val hoverColor = if (selected) accent.copy(alpha = .21f) else UiElevated
    val pressColor = if (selected) accent.copy(alpha = .26f) else accent.copy(alpha = .12f)
    val baseBorder by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            selected -> accent.copy(alpha = .36f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 160, easing = DesktopStandardEasing)
    )
    val hoverBorder = if (selected) accent.copy(alpha = .50f) else UiHairline
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> UiMuted.copy(alpha = .42f)
            selected -> if (isLight) accent else UiInk
            else -> UiInk.copy(alpha = .85f)
        },
        animationSpec = tween(durationMillis = 160, easing = DesktopStandardEasing)
    )
    val baseLabelStyle = MaterialTheme.typography.labelLarge
    val textStyle = remember(baseLabelStyle, selected) {
        baseLabelStyle.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .heightIn(min = 32.dp)
                .semantics {
                    role = Role.Checkbox
                    this.selected = selected
                }
                .desktopInteractiveSurface(
                    shape = shape,
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
                .padding(horizontal = 13.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leading != null) leading()
            ProvideTextStyle(textStyle, content = label)
            if (trailing != null) trailing()
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
        shape = RoundedCornerShape(9.dp),
        color = UiFieldFill
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
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
    accentOverride: Color? = null,
    showActiveDot: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hoveredState = interaction.collectIsHoveredAsState()
    val pressedState = interaction.collectIsPressedAsState()
    val focusedState = interaction.collectIsFocusedAsState()
    val isDark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accent = accentOverride ?: UiAccent
    val selectedThumb = if (isDark) Color(0xff38383d) else Color.White
    val baseColor by animateColorAsState(
        targetValue = if (selected) selectedThumb else Color.Transparent,
        animationSpec = tween(durationMillis = 170, easing = DesktopStandardEasing)
    )
    val hoverColor = if (selected) selectedThumb else UiInk.copy(alpha = .05f)
    val pressColor = if (selected) selectedThumb else UiInk.copy(alpha = .09f)
    val thumbBorder by animateColorAsState(
        targetValue = when {
            !selected -> Color.Transparent
            isDark -> Color.White.copy(alpha = .08f)
            else -> Color.Black.copy(alpha = .06f)
        },
        animationSpec = tween(durationMillis = 170, easing = DesktopStandardEasing)
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> UiMuted.copy(alpha = .42f)
            selected -> UiInk
            else -> UiMuted
        },
        animationSpec = tween(durationMillis = 170, easing = DesktopStandardEasing)
    )
    Row(
        modifier = modifier
            .heightIn(min = 30.dp)
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
                baseBorderColor = thumbBorder,
                hoverBorderColor = thumbBorder,
                pressBorderColor = thumbBorder,
                focusBorderColor = accent
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = showActiveDot && selected,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.6f, animationSpec = tween(150, easing = DesktopStandardEasing)),
            exit = fadeOut(tween(110)) + scaleOut(targetScale = 0.6f, animationSpec = tween(110, easing = DesktopStandardEasing))
        ) {
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(5.dp)
                    .background(accent, CircleShape)
            )
        }
        Text(
            text = title,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
internal fun DesktopErrorBanner(message: String, action: String = "重试", onRetry: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(DesktopHairlineWidth, MaterialTheme.colorScheme.error.copy(alpha = .28f))
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            Text(message, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyMedium)
            DesktopTextButton(onRetry) { Text(action) }
        }
    }
}

@Composable
internal fun DesktopEmptyState(
    title: String,
    detail: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(16.dp),
                color = UiAccent.copy(alpha = .10f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(26.dp), tint = UiAccent)
                }
            }
            Text(title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(detail, Modifier.padding(top = 6.dp), color = UiMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (action != null && onAction != null) {
                DesktopTonalButton(onAction, Modifier.padding(top = 16.dp)) { Text(action) }
            }
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
        if (failed) Icon(Icons.Default.LiveTv, null, Modifier.align(Alignment.Center).size(36.dp), tint = UiMuted.copy(alpha = .24f))
        else AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { failed = true })
    }
}

@Composable
internal fun DesktopRoomGrid(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    minCardWidth: Dp = 232.dp,
    content: LazyGridScope.() -> Unit
) {
    Box(modifier) {
        LazyVerticalGrid(
            GridCells.Adaptive(minCardWidth),
            Modifier.fillMaxSize().testTag("room-grid").padding(end = 14.dp),
            state = state,
            contentPadding = PaddingValues(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
        VerticalScrollbar(
            rememberScrollbarAdapter(state),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = defaultScrollbarStyle().copy(
                thickness = 6.dp,
                unhoverColor = UiMuted.copy(alpha = .20f),
                hoverColor = UiMuted.copy(alpha = .55f)
            )
        )
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
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(52.dp).background(UiAccent, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Text(DESKTOP_APP_NAME, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
            CircularProgressIndicator(Modifier.padding(top = 20.dp).size(20.dp), strokeWidth = 2.dp)
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
    save = { positions ->
        buildList {
            positions.forEach { (key, position) -> add(key); add(position.first); add(position.second) }
        }
    },
    restore = { saved ->
        linkedMapOf<String, Pair<Int, Int>>().apply {
            saved.chunked(3).forEach { put(it[0] as String, (it[1] as Int) to (it[2] as Int)) }
        }
    }
)
