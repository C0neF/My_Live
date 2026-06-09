package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuLayoutPolicyTest {

    @Test
    fun lineCountZeroHidesDanmakuTracks() {
        val result = resolveDanmakuTrackLayout(
            viewportHeightPx = 400,
            density = 2f,
            fontSizeSp = 16f,
            area = 1f,
            requestedLineCount = 0,
            topMarginDp = 0f,
            bottomMarginDp = 0f
        )

        assertEquals(0, result.trackCount)
        assertEquals(0f, result.topOffsetPx, 0.01f)
    }

    @Test
    fun requestedLinesAreClampedByAreaAndSafeMargins() {
        val result = resolveDanmakuTrackLayout(
            viewportHeightPx = 240,
            density = 2f,
            fontSizeSp = 20f,
            area = 0.5f,
            requestedLineCount = 8,
            topMarginDp = 8f,
            bottomMarginDp = 8f
        )

        assertEquals(1, result.trackCount)
        assertEquals(16f, result.topOffsetPx, 0.01f)
    }

    @Test
    fun unsetRequestedLinesUsesAreaLimit() {
        val result = resolveDanmakuTrackLayout(
            viewportHeightPx = 480,
            density = 2f,
            fontSizeSp = 16f,
            area = 0.75f,
            requestedLineCount = 8,
            topMarginDp = 0f,
            bottomMarginDp = 0f
        )

        assertEquals(7, result.trackCount)
    }
}
