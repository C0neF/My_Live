package com.mylive.app.ui.orientation

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import com.mylive.app.core.model.TabletOrientationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletOrientationPolicyTest {

    @Test
    fun orientationLockStartsAtTabletSmallestWidth() {
        assertFalse(supportsTabletOrientationLock(599))
        assertTrue(supportsTabletOrientationLock(600))
        assertTrue(supportsTabletOrientationLock(840))
    }

    @Test
    fun firstTabletLaunchCapturesCurrentOrientation() {
        assertEquals(
            TabletOrientationMode.Portrait,
            resolveTabletOrientationMode(
                storedMode = TabletOrientationMode.Uninitialized,
                currentConfigurationOrientation = Configuration.ORIENTATION_PORTRAIT
            )
        )
        assertEquals(
            TabletOrientationMode.Landscape,
            resolveTabletOrientationMode(
                storedMode = TabletOrientationMode.Uninitialized,
                currentConfigurationOrientation = Configuration.ORIENTATION_LANDSCAPE
            )
        )
    }

    @Test
    fun persistedOrientationAlwaysWinsOverSensorConfiguration() {
        assertEquals(
            TabletOrientationMode.Portrait,
            resolveTabletOrientationMode(
                storedMode = TabletOrientationMode.Portrait,
                currentConfigurationOrientation = Configuration.ORIENTATION_LANDSCAPE
            )
        )
        assertEquals(
            TabletOrientationMode.Landscape,
            resolveTabletOrientationMode(
                storedMode = TabletOrientationMode.Landscape,
                currentConfigurationOrientation = Configuration.ORIENTATION_PORTRAIT
            )
        )
    }

    @Test
    fun followSystemReleasesAnyPreviouslyRequestedOrientation() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            tabletRequestedOrientation(TabletOrientationMode.FollowSystem))
        assertEquals(TabletOrientationMode.FollowSystem,
            TabletOrientationMode.fromPersistedValue(TabletOrientationMode.FollowSystem.persistedValue))
    }

    @Test
    fun fixedButtonAlternatesPortraitAndLandscape() {
        assertEquals(
            TabletOrientationMode.Landscape,
            nextTabletOrientationMode(TabletOrientationMode.Portrait)
        )
        assertEquals(
            TabletOrientationMode.Portrait,
            nextTabletOrientationMode(TabletOrientationMode.Landscape)
        )
    }

    @Test
    fun explicitPortraitAndLandscapeKeepTheirRequestedAxis() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            tabletRequestedOrientation(TabletOrientationMode.Portrait)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            tabletRequestedOrientation(TabletOrientationMode.Landscape)
        )
    }
}
