package com.mylive.app.ui.screen.room.player

import com.mylive.app.core.model.LiveMessageDanmakuPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FixedDanmakuPolicyTest {

    @Test
    fun eachHideSwitchOnlyFiltersItsOwnPlacement() {
        assertFalse(
            shouldDisplayDanmakuPosition(
                position = LiveMessageDanmakuPosition.SCROLL,
                hideScroll = true,
                hideTop = false,
                hideBottom = false
            )
        )
        assertFalse(
            shouldDisplayDanmakuPosition(
                position = LiveMessageDanmakuPosition.TOP,
                hideScroll = false,
                hideTop = true,
                hideBottom = false
            )
        )
        assertFalse(
            shouldDisplayDanmakuPosition(
                position = LiveMessageDanmakuPosition.BOTTOM,
                hideScroll = false,
                hideTop = false,
                hideBottom = true
            )
        )
        assertTrue(
            shouldDisplayDanmakuPosition(
                position = LiveMessageDanmakuPosition.BOTTOM,
                hideScroll = true,
                hideTop = true,
                hideBottom = false
            )
        )
    }

    @Test
    fun fixedBaselinesUseActualViewportAndSafeMargins() {
        val layout = resolveDanmakuTrackLayout(
            viewportHeightPx = 800,
            density = 2f,
            fontSizeSp = 16f,
            area = 1f,
            requestedLineCount = 8,
            topMarginDp = 10f,
            bottomMarginDp = 20f
        )

        assertEquals(
            68f,
            resolveFixedDanmakuBaseline(
                position = LiveMessageDanmakuPosition.TOP,
                track = 0,
                viewportHeightPx = 800,
                fontHeightPx = 32f,
                layout = layout
            ),
            0.01f
        )
        assertEquals(
            744f,
            resolveFixedDanmakuBaseline(
                position = LiveMessageDanmakuPosition.BOTTOM,
                track = 0,
                viewportHeightPx = 800,
                fontHeightPx = 32f,
                layout = layout
            ),
            0.01f
        )
    }

    @Test
    fun fixedTracksRejectVerticalOverlapFromEitherEdge() {
        assertFalse(
            isFixedDanmakuTrackAvailable(
                candidateBaselinePx = 300f,
                occupiedBaselinesPx = listOf(260f),
                trackHeightPx = 48f
            )
        )
        assertTrue(
            isFixedDanmakuTrackAvailable(
                candidateBaselinePx = 300f,
                occupiedBaselinesPx = listOf(240f),
                trackHeightPx = 48f
            )
        )
    }

    @Test
    fun controllerCentersFixedDanmakuAndExpiresItAfterFiveSeconds() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/DanmakuController.kt").readText()

        assertTrue(source.contains("FIXED_DANMAKU_DURATION_MS = 5_000L"))
        assertTrue(source.contains("(w - itemWidth) / 2f"))
        assertTrue(source.contains("expiresAtMs = nowMs + FIXED_DANMAKU_DURATION_MS"))
    }
}
