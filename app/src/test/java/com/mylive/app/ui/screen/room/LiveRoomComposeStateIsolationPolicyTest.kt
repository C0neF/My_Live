package com.mylive.app.ui.screen.room

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveRoomComposeStateIsolationPolicyTest {

    @Test
    fun rootDoesNotCollectFullDanmakuMessageList() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val topLevel = source.substringAfter("fun LiveRoomScreen(")
            .substringBefore("private fun PortraitLayout(")

        assertFalse(
            "root LiveRoomScreen must not collect danmakuMessages; ChatPanel owns that scope",
            topLevel.contains("danmakuMessages.collectAsStateWithLifecycle()")
        )
        assertTrue(source.contains("messagesFlow = viewModel.danmakuMessages"))
    }

    @Test
    fun rootCollectsFullscreenFlagInsteadOfFullPlayerState() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val topLevel = source.substringAfter("fun LiveRoomScreen(")
            .substringBefore("private fun PortraitLayout(")

        assertTrue(
            topLevel.contains("map { it.isFullscreen }") ||
                topLevel.contains(".map { state -> state.isFullscreen }") ||
                topLevel.contains("it.isFullscreen")
        )
        assertFalse(
            "root should not collect full PlayerState just for layout branching",
            topLevel.contains("playerController?.state?.collectAsStateWithLifecycle()?.value")
        )
        assertTrue(topLevel.contains("collectAsStateWithLifecycle(initialValue"))
    }

    @Test
    fun onlineCountLivesOnDedicatedStateFlow() {
        val viewModel = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt").readText()
        val uiStateBlock = viewModel.substringAfter("data class LiveRoomUiState(")
            .substringBefore("internal fun loadingLiveRoomUiState")
        assertTrue(viewModel.contains("val onlineCount: StateFlow<Int>"))
        assertFalse(uiStateBlock.contains("onlineCount"))
    }
}
