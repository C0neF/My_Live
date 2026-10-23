package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.AppLayoutMode
import com.mylive.app.ui.layout.resolveAppLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRoomLayoutPolicyTest {
    @Test fun smallLandscapeTabletKeepsItsEmbeddedStackedLayout() {
        val layout = resolveAppLayout(AppLayoutMode.Auto, 800, 600, 600, 1f)
        assertEquals(LiveRoomLayout.Stacked, resolveLiveRoomLayout(layout, isLandscape = true, fullscreen = false))
    }

    @Test fun largerFontsCollapseTheSplitInsteadOfOpeningImmersivePlayback() {
        val normal = resolveAppLayout(AppLayoutMode.Tablet, 960, 800, 800, 1f)
        val large = resolveAppLayout(AppLayoutMode.Tablet, 960, 800, 800, 2f)
        assertEquals(LiveRoomLayout.Split, resolveLiveRoomLayout(normal, true, false))
        assertEquals(LiveRoomLayout.Stacked, resolveLiveRoomLayout(large, true, false))
    }

    @Test fun explicitFullscreenStillWinsOnTablets() {
        val layout = resolveAppLayout(AppLayoutMode.Tablet, 800, 600, 600, 1f)
        assertEquals(LiveRoomLayout.Immersive, resolveLiveRoomLayout(layout, true, true))
    }

    @Test fun phoneLandscapeBehaviorRemainsImmersive() {
        val layout = resolveAppLayout(AppLayoutMode.Phone, 800, 600, 600, 1f)
        assertEquals(LiveRoomLayout.Immersive, resolveLiveRoomLayout(layout, true, false))
        assertEquals(LiveRoomLayout.Stacked, resolveLiveRoomLayout(layout, false, false))
    }
}