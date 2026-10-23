package com.mylive.app.ui.screen.room.player

import android.content.pm.ActivityInfo
import com.mylive.app.core.model.TabletOrientationMode
import org.junit.Assert.*
import org.junit.Test

class PlaybackVideoGeometryTest {
    @Test fun usesActualDisplayRatioRatherThanAssumingNineBySixteen() {
        assertEquals(9f / 16f, playbackVideoAspectRatio(1080, 1920)!!, 0.0001f)
        assertEquals(3f / 4f, playbackVideoAspectRatio(720, 960)!!, 0.0001f)
        assertEquals(16f / 9f, playbackVideoAspectRatio(3840, 2160)!!, 0.0001f)
    }

    @Test fun accountsForPixelShapeAndOnlyUnappliedRotation() {
        assertEquals(9f / 16f, playbackVideoAspectRatio(1920, 1080, unappliedRotationDegrees = 90)!!, 0.0001f)
        assertEquals(9f / 16f, playbackVideoAspectRatio(1920, 1080, unappliedRotationDegrees = 270)!!, 0.0001f)
        assertEquals(9f / 16f, playbackVideoAspectRatio(1080, 1920, unappliedRotationDegrees = 0)!!, 0.0001f)
        assertEquals(1.5f, playbackVideoAspectRatio(720, 960, pixelWidthHeightRatio = 2f)!!, 0.0001f)
    }

    @Test fun unknownAndInvalidSizesDoNotProduceAnOrientation() {
        assertNull(playbackVideoAspectRatio(0, 1920))
        assertNull(playbackVideoAspectRatio(1080, 0))
        assertNull(playbackVideoAspectRatio(-1, 100))
        for (pixelRatio in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertNull(playbackVideoAspectRatio(100, 100, pixelRatio))
        }
        assertFalse(isPortraitVideo(null))
        assertFalse(isPortraitVideo(1f))
    }

    @Test fun verticalFullscreenWinsOverTabletLandscapePreferenceAndExitRestoresIt() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            livePlaybackHostRequestedOrientation(true, false,
                tabletOrientationMode = TabletOrientationMode.Landscape, videoAspectRatio = 9f / 16f))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            livePlaybackHostRequestedOrientation(false, false,
                tabletOrientationMode = TabletOrientationMode.Landscape, videoAspectRatio = 9f / 16f))
    }

    @Test fun lockingAndUnknownSizesKeepTheCurrentOrientationUntilAValidSourceArrives() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED,
            livePlaybackHostRequestedOrientation(true, false, videoAspectRatio = null))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED,
            livePlaybackHostRequestedOrientation(true, false, videoAspectRatio = 1f))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED,
            livePlaybackHostRequestedOrientation(true, true, videoAspectRatio = 9f / 16f))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            livePlaybackHostRequestedOrientation(true, false, videoAspectRatio = 16f / 9f))
    }

    @Test fun automaticFullscreenWaitsForSizeAndSupportsPortraitLockedTabletsForVerticalVideo() {
        assertFalse(shouldApplyLivePlaybackAutoFullscreen(true, null, null))
        assertTrue(shouldApplyLivePlaybackAutoFullscreen(true, TabletOrientationMode.Portrait, 9f / 16f))
        assertFalse(shouldApplyLivePlaybackAutoFullscreen(true, TabletOrientationMode.Portrait, 16f / 9f))
        assertFalse(shouldApplyLivePlaybackAutoFullscreen(false, null, 9f / 16f))
    }

    @Test fun manualAspectOverridesRemainExplicitAndPipStaysWithinPlatformLimits() {
        assertEquals(9f / 16f, playbackContainerAspectRatio(9f / 16f, 0), 0.0001f)
        assertEquals(4f / 3f, playbackContainerAspectRatio(9f / 16f, 3), 0.0001f)
        assertEquals(16f / 9f, playbackContainerAspectRatio(9f / 16f, 4), 0.0001f)
        assertEquals(9f / 16f, playbackPipAspectRatio(9f / 16f), 0.0001f)
        for (ratio in listOf(0.1f, 0.4f, 0.8f, 1f, 3f, 10f)) {
            val rationalValue = (playbackPipAspectRatio(ratio) * 10_000).toInt() / 10_000f
            assertTrue(rationalValue >= 1f / 2.39f && rationalValue <= 2.39f)
        }
    }

    @Test fun portraitFullscreenUsesCompactControlsOnPhoneWidths() {
        assertTrue(shouldUseCompactPlayerBottomBar(360, isPortrait = true, isFullscreen = true))
        assertFalse(shouldUseCompactPlayerBottomBar(360, isPortrait = true, isFullscreen = false))
        assertFalse(shouldUseCompactPlayerBottomBar(800, isPortrait = true, isFullscreen = true))
    }

    @Test fun anOldHostCannotClearTheNewRoomsPipGeometry() {
        val oldRoom = Any()
        val newRoom = Any()
        try {
            LivePlaybackHostSignals.setVideoAspectRatio(oldRoom, 9f / 16f)
            LivePlaybackHostSignals.setVideoAspectRatio(newRoom, 16f / 9f)
            LivePlaybackHostSignals.clearVideoAspectRatio(oldRoom)
            assertEquals(16f / 9f, LivePlaybackHostSignals.videoAspectRatioFlow.value!!, 0.0001f)
            LivePlaybackHostSignals.clearVideoAspectRatio(newRoom)
            assertNull(LivePlaybackHostSignals.videoAspectRatioFlow.value)
        } finally {
            LivePlaybackHostSignals.clearVideoAspectRatio(newRoom)
        }
    }
}
