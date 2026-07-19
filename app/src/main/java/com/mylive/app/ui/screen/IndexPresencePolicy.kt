package com.mylive.app.ui.screen

import androidx.navigation3.runtime.NavKey
import com.mylive.app.ui.navigation.Route

/**
 * Index is intentionally retained under NavDisplay for instant return UX.
 * When a non-Index route covers Index, skip composing tab content so Home/Follow/etc.
 * collectors and grid work do not keep running while a live room is on top.
 *
 * Scaffold chrome may still reserve layout; only heavy tab content is gated.
 */
internal fun shouldComposeIndexTabContent(topRoute: NavKey?): Boolean {
    return topRoute == null || topRoute is Route.Index
}

/**
 * Whether Index should treat itself as the visible interaction surface.
 * Same as [shouldComposeIndexTabContent] today; kept separate for future "state retained,
 * composition paused" variants.
 */
internal fun isIndexInteractionSurface(topRoute: NavKey?): Boolean {
    return shouldComposeIndexTabContent(topRoute)
}
