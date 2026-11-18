package com.mylive.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import org.junit.Assert.*
import org.junit.Test

class DesktopWindowModeTest {
    @Test fun miniModeRestoresGeometryAndMaximizedMode() {
        val original = DpSize(1300.dp, 850.dp)
        val state = WindowState(placement = WindowPlacement.Maximized, size = original, position = WindowPosition(40.dp, 60.dp))
        val modes = DesktopWindowMode(state)
        modes.toggleMini()
        assertTrue(modes.isMini)
        assertEquals(WindowPlacement.Floating, state.placement)
        assertEquals(DpSize(480.dp, 300.dp), state.size)
        state.size = DpSize(600.dp, 350.dp)
        modes.toggleMini()
        assertFalse(modes.isMini)
        assertEquals(WindowPlacement.Maximized, state.placement)
        assertEquals(original, state.size)
        assertEquals(WindowPosition(40.dp, 60.dp), state.position)
    }

    @Test fun miniFromFullscreenAndFullscreenFromMiniPreserveNormalGeometry() {
        val original = DpSize(1280.dp, 820.dp)
        val state = WindowState(size = original)
        val modes = DesktopWindowMode(state)
        modes.toggle(); modes.toggleMini()
        assertTrue(modes.isMini)
        modes.toggle()
        assertFalse(modes.isMini)
        assertEquals(WindowPlacement.Fullscreen, state.placement)
        modes.exit()
        assertEquals(original, state.size)
    }

    @Test fun visibilityPolicyDoesNotResumeAnExplicitUserPauseOrStoppedMedia() {
        val policy = DesktopVisibilityPolicy()
        assertEquals(VisibilityPlaybackAction.Pause, policy.update(false, false, true, true))
        assertEquals(VisibilityPlaybackAction.Resume, policy.update(true, false, false, true))
        assertEquals(VisibilityPlaybackAction.None, policy.update(false, false, false, true))
        assertEquals(VisibilityPlaybackAction.None, policy.update(true, false, false, true))
        policy.update(false, false, true, true)
        policy.manuallyChanged()
        assertEquals(VisibilityPlaybackAction.None, policy.update(true, false, false, true))
        policy.update(false, false, true, true)
        assertEquals(VisibilityPlaybackAction.None, policy.update(false, false, false, false))
        assertEquals(VisibilityPlaybackAction.None, policy.update(true, false, false, false))
    }

    @Test fun enablingBackgroundPlaybackResumesOnlyAnAutomaticPause() {
        val policy = DesktopVisibilityPolicy()
        assertEquals(VisibilityPlaybackAction.None, policy.update(false, true, true, true))
        assertEquals(VisibilityPlaybackAction.Pause, policy.update(false, false, true, true))
        assertEquals(VisibilityPlaybackAction.Resume, policy.update(false, true, false, true))
        assertEquals(VisibilityPlaybackAction.None, policy.update(true, true, true, true))
    }
    @Test fun fullscreenRestoresFloatingGeometryDespiteNativeResizeNotifications() {
        val state = WindowState(size = DpSize(1280.dp, 820.dp), position = WindowPosition(100.dp, 80.dp))
        val modes = DesktopWindowMode(state)
        modes.toggle()
        assertEquals(WindowPlacement.Fullscreen, state.placement)
        state.size = DpSize(2048.dp, 1152.dp)
        state.position = WindowPosition(0.dp, 0.dp)
        modes.exit()
        assertEquals(WindowPlacement.Floating, state.placement)
        assertEquals(DpSize(1280.dp, 820.dp), state.size)
        assertEquals(WindowPosition(100.dp, 80.dp), state.position)
    }

    @Test fun enteringFromMaximizedReturnsToMaximized() {
        val state = WindowState(placement = WindowPlacement.Maximized)
        val modes = DesktopWindowMode(state)
        modes.toggle()
        modes.toggle()
        assertEquals(WindowPlacement.Maximized, state.placement)
        assertEquals(1L, modes.restoreSequence)
    }
}
