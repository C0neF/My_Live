package com.mylive.app.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.mylive.app.core.model.AppLayoutMode

@Immutable
data class AppLayoutInfo(
    val mode: AppLayoutMode = AppLayoutMode.Auto,
    val tablet: Boolean = false,
    val widthDp: Int = 360,
    val heightDp: Int = 800,
    val fontScale: Float = 1f
) {
    val useNavigationRail: Boolean get() = tablet && widthDp >= 360
    val navigationRailWidthDp: Int get() = if (widthDp < 600) 80 else 96
    val pageMaxWidthDp: Int get() = if (tablet) 1440 else 560
    val settingsMaxWidthDp: Int get() = if (tablet) 960 else 560
    val indexContentWidthDp: Int
        get() = (widthDp - if (useNavigationRail) navigationRailWidthDp else 0)
            .coerceIn(1, pageMaxWidthDp)
    val liveRoomMinCellWidthDp: Float get() = liveRoomMinCellWidthDp(tablet, fontScale)
    val liveRoomColumns: Int
        get() = adaptiveLiveRoomColumns(indexContentWidthDp, tablet, fontScale)
    val followColumns: Int
        get() = if (tablet && indexContentWidthDp >= 680 * readableFontScale(fontScale)) 2 else 1
    val splitLiveRoom: Boolean
        get() = tablet && widthDp >= 840 * readableFontScale(fontScale).coerceAtMost(1.25f) && heightDp >= 480
    val liveChatWidthDp: Int
        get() = (widthDp * 0.32f).toInt().coerceIn(
            (320 * readableFontScale(fontScale)).toInt(),
            (440 * readableFontScale(fontScale)).toInt()
        )
}

internal fun readableFontScale(fontScale: Float): Float =
    if (fontScale.isFinite()) fontScale.coerceIn(1f, 1.8f) else 1f

internal fun liveRoomMinCellWidthDp(tablet: Boolean, fontScale: Float): Float =
    (if (tablet) 208 else 136) * readableFontScale(fontScale)

internal fun adaptiveLiveRoomColumns(widthDp: Int, tablet: Boolean, fontScale: Float): Int {
    val minimum = liveRoomMinCellWidthDp(tablet, fontScale)
    return ((widthDp - 20) / (minimum + 12)).toInt().coerceIn(1, if (tablet) 6 else 3)
}

internal fun resolveAppLayout(
    mode: AppLayoutMode,
    widthDp: Int,
    heightDp: Int,
    smallestWidthDp: Int,
    fontScale: Float
): AppLayoutInfo {
    val tablet = when (mode) {
        AppLayoutMode.Phone -> false
        AppLayoutMode.Tablet -> true
        AppLayoutMode.Auto -> (smallestWidthDp >= 600 && widthDp >= 600) || (widthDp >= 840 && heightDp >= 480)
    }
    return AppLayoutInfo(mode, tablet, widthDp.coerceAtLeast(1), heightDp.coerceAtLeast(1), fontScale)
}

val LocalAppLayout = staticCompositionLocalOf { AppLayoutInfo() }

@Composable
fun ProvideAppLayout(mode: AppLayoutMode, content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val layout = resolveAppLayout(
        mode,
        with(density) { windowSize.width.toDp().value.toInt() },
        with(density) { windowSize.height.toDp().value.toInt() },
        configuration.smallestScreenWidthDp,
        density.fontScale
    )
    CompositionLocalProvider(LocalAppLayout provides layout, content = content)
}

/** Centers reading-oriented pages without changing the actual window configuration. */
@Composable
fun AdaptivePageFrame(
    modifier: Modifier = Modifier,
    maxWidthDp: Int = LocalAppLayout.current.pageMaxWidthDp,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidthDp.dp).fillMaxWidth()) { content() }
    }
}