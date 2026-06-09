package com.mylive.app.ui.screen.room

import androidx.compose.ui.graphics.Color

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
