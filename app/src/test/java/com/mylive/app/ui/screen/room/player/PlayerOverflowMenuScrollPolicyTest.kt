package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerOverflowMenuScrollPolicyTest {

    @Test
    fun edgeFadesShowOnlyWhereMoreMenuContentCanContinue() {
        assertEquals(
            PlayerOverflowMenuScrollEdges(showTop = false, showBottom = true),
            resolvePlayerOverflowMenuScrollEdges(scrollValue = 0, maxScrollValue = 100)
        )
        assertEquals(
            PlayerOverflowMenuScrollEdges(showTop = true, showBottom = true),
            resolvePlayerOverflowMenuScrollEdges(scrollValue = 50, maxScrollValue = 100)
        )
        assertEquals(
            PlayerOverflowMenuScrollEdges(showTop = true, showBottom = false),
            resolvePlayerOverflowMenuScrollEdges(scrollValue = 100, maxScrollValue = 100)
        )
        assertEquals(
            PlayerOverflowMenuScrollEdges(showTop = false, showBottom = false),
            resolvePlayerOverflowMenuScrollEdges(scrollValue = 0, maxScrollValue = 0)
        )
    }

    @Test
    fun playbackDiagnosticsRemainDebugOnlyAndRequireAController() {
        assertTrue(shouldShowPlaybackDiagnostics(debugBuild = true, hasController = true))
        assertFalse(shouldShowPlaybackDiagnostics(debugBuild = false, hasController = true))
        assertFalse(shouldShowPlaybackDiagnostics(debugBuild = true, hasController = false))
    }
}
