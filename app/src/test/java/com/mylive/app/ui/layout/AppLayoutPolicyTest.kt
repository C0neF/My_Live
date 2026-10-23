package com.mylive.app.ui.layout

import com.mylive.app.core.model.AppLayoutMode
import org.junit.Assert.*
import org.junit.Test

class AppLayoutPolicyTest {
    @Test fun manualPhoneModeOverridesLargeScreenDetection() {
        val automatic = resolveAppLayout(AppLayoutMode.Auto, 1280, 800, 800, 1f)
        val phone = resolveAppLayout(AppLayoutMode.Phone, 1280, 800, 800, 1f)
        assertTrue(automatic.tablet)
        assertTrue(automatic.useNavigationRail)
        assertTrue(automatic.splitLiveRoom)
        assertFalse(phone.tablet)
        assertFalse(phone.useNavigationRail)
        assertFalse(phone.splitLiveRoom)
        assertTrue(phone.indexContentWidthDp <= 560)
    }

    @Test fun explicitTabletModeWorksOnPhonesWithoutSqueezingMultipleColumns() {
        val tablet = resolveAppLayout(AppLayoutMode.Tablet, 394, 853, 394, 1f)
        assertTrue(tablet.tablet)
        assertTrue(tablet.useNavigationRail)
        assertEquals(1, tablet.liveRoomColumns)
        assertEquals(1, tablet.followColumns)
        assertFalse(tablet.splitLiveRoom)
    }

    @Test fun rotatingAPhoneDoesNotAutomaticallyTurnItIntoATablet() {
        assertFalse(resolveAppLayout(AppLayoutMode.Auto, 853, 394, 394, 1f).tablet)
        assertTrue(resolveAppLayout(AppLayoutMode.Auto, 1024, 768, 768, 1f).tablet)
    }

    @Test fun smallWindowsKeepThePreferenceButCollapseUnsupportedPanes() {
        val small = resolveAppLayout(AppLayoutMode.Tablet, 320, 480, 320, 1f)
        assertTrue(small.tablet)
        assertFalse(small.useNavigationRail)
        assertFalse(small.splitLiveRoom)
        assertEquals(1, small.liveRoomColumns)
    }

    @Test fun automaticModeRespectsANarrowWindowOnALargeDevice() {
        val narrow = resolveAppLayout(AppLayoutMode.Auto, 420, 800, 800, 1f)
        assertFalse(narrow.tablet)
        assertFalse(narrow.useNavigationRail)
        assertFalse(narrow.splitLiveRoom)
    }

    @Test fun largerFontsReduceGridDensityAndCanCollapseTheChatPane() {
        val normal = resolveAppLayout(AppLayoutMode.Tablet, 960, 800, 800, 1f)
        val large = resolveAppLayout(AppLayoutMode.Tablet, 960, 800, 800, 2f)
        assertTrue(large.liveRoomColumns < normal.liveRoomColumns)
        assertTrue(normal.splitLiveRoom)
        assertFalse(large.splitLiveRoom)
        assertTrue(large.liveChatWidthDp > normal.liveChatWidthDp)
    }

    @Test fun readingWidthAndGridDensityRemainBoundedOnDesktopWindows() {
        val wide = resolveAppLayout(AppLayoutMode.Tablet, 2400, 1400, 1400, 1f)
        assertEquals(1440, wide.indexContentWidthDp)
        assertTrue(wide.liveRoomColumns <= 6)
        assertTrue(wide.liveChatWidthDp <= 440)
    }

    @Test fun persistedOverridesRoundTripAndUnknownValuesUseAutomaticMode() {
        AppLayoutMode.entries.forEach { assertEquals(it, AppLayoutMode.fromPersistedValue(it.persistedValue)) }
        assertEquals(AppLayoutMode.Auto, AppLayoutMode.fromPersistedValue(-1))
        assertEquals(AppLayoutMode.Auto, AppLayoutMode.fromPersistedValue(999))
    }
}