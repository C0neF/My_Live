package com.mylive.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Fullscreen is temporary; preserve the user's floating/maximized mode and window geometry. */
internal class DesktopWindowMode(private val state: WindowState) {
    private var previousPlacement = state.placement
    private var miniPlacement = state.placement
    private var miniSize = state.size
    private var miniPosition = state.position
    var isMini by mutableStateOf(false)
        private set
    var floatingSize = state.size
        private set
    var floatingPosition = state.position
        private set
    var restoreSequence by mutableLongStateOf(0L)
        private set

    fun toggle() {
        if (isMini) exitMini()
        if (state.placement == WindowPlacement.Fullscreen) exit()
        else {
            previousPlacement = state.placement
            floatingSize = state.size
            floatingPosition = state.position
            state.placement = WindowPlacement.Fullscreen
        }
    }

    fun exit() {
        if (isMini) exitMini()
        if (state.placement != WindowPlacement.Fullscreen) return
        state.placement = previousPlacement
        state.size = floatingSize
        state.position = floatingPosition
        restoreSequence++
    }

    fun toggleMini() {
        if (isMini) { exitMini(); return }
        if (state.placement == WindowPlacement.Fullscreen) exit()
        miniPlacement = state.placement
        miniSize = state.size
        miniPosition = state.position
        isMini = true
        state.placement = WindowPlacement.Floating
        state.size = DpSize(480.dp, 300.dp)
    }

    private fun exitMini() {
        isMini = false
        state.placement = miniPlacement
        state.size = miniSize
        state.position = miniPosition
        floatingSize = miniSize
        floatingPosition = miniPosition
        restoreSequence++
    }
}
