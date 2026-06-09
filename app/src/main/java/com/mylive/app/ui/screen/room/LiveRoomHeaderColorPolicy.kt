package com.mylive.app.ui.screen.room

import androidx.compose.ui.graphics.Color
import com.mylive.app.ui.theme.livePlatformAccentColor

internal fun resolveLiveRoomRefreshIconTint(
    themeOnSurfaceColor: Color
): Color {
    return resolveLiveRoomToolbarIconTint(themeOnSurfaceColor)
}

internal fun resolveLiveRoomToolbarIconTint(
    themeOnSurfaceColor: Color
): Color {
    return themeOnSurfaceColor
}

internal fun resolveLiveRoomPlatformAccentColor(
    siteId: String,
    defaultAccentColor: Color
): Color {
    return livePlatformAccentColor(siteId) ?: defaultAccentColor
}

internal fun resolveLiveRoomAccentSiteId(
    routeSiteId: String,
    viewModelSiteId: String
): String {
    return routeSiteId.trim().ifEmpty { viewModelSiteId.trim() }
}
