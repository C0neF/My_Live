package com.mylive.app.ui.orientation

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import com.mylive.app.core.model.TabletOrientationMode

internal const val TabletOrientationMinSmallestWidthDp = 600

internal fun supportsTabletOrientationLock(smallestScreenWidthDp: Int): Boolean {
    return smallestScreenWidthDp >= TabletOrientationMinSmallestWidthDp
}

internal fun resolveTabletOrientationMode(
    storedMode: TabletOrientationMode,
    currentConfigurationOrientation: Int
): TabletOrientationMode {
    if (storedMode != TabletOrientationMode.Uninitialized) return storedMode

    return if (currentConfigurationOrientation == Configuration.ORIENTATION_LANDSCAPE) {
        TabletOrientationMode.Landscape
    } else {
        TabletOrientationMode.Portrait
    }
}

internal fun nextTabletOrientationMode(
    currentMode: TabletOrientationMode
): TabletOrientationMode {
    return when (currentMode) {
        TabletOrientationMode.Landscape -> TabletOrientationMode.Portrait
        TabletOrientationMode.Portrait,
        TabletOrientationMode.Uninitialized,
        TabletOrientationMode.FollowSystem -> TabletOrientationMode.Landscape
    }
}

internal fun tabletRequestedOrientation(mode: TabletOrientationMode): Int {
    return when (mode) {
        TabletOrientationMode.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        TabletOrientationMode.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        TabletOrientationMode.Uninitialized -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
        TabletOrientationMode.FollowSystem -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
