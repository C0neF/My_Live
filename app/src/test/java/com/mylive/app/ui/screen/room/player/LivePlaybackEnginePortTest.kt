package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LivePlaybackEnginePortTest {

    @Test
    fun viewModelDependsOnEnginePortNotRawController() {
        val viewModel = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt")
            .readText()
        assertTrue(viewModel.contains("LivePlaybackEngine"))
        assertTrue(viewModel.contains("fun bindPlaybackEngine("))
        assertTrue(viewModel.contains("fun unbindPlaybackEngine("))
        assertTrue(viewModel.contains("playbackEngine?.play("))
        assertTrue(viewModel.contains("playbackEngine?.showError("))
        assertFalse(viewModel.contains("import com.mylive.app.ui.screen.room.player.PlayerController"))
        assertFalse(viewModel.contains("var playerController: PlayerController"))
    }

    @Test
    fun sessionImplementsEnginePortAndKeepsControllerPrivate() {
        val controller = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerController.kt")
            .readText()
        val session = File("src/main/java/com/mylive/app/ui/screen/room/player/LivePlaybackSession.kt")
            .readText()
        assertFalse(controller.contains(": LivePlaybackEngine"))
        assertTrue(session.contains(": LivePlaybackEngine"))
    }

    @Test
    fun sessionBindsEngineThroughPort() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/LivePlaybackSession.kt")
            .readText()
        assertTrue(source.contains(": LivePlaybackEngine"))
        assertTrue(source.contains("get() = this"))
        assertTrue(source.contains("binding.bindPlaybackEngine(engine)"))
        assertTrue(source.contains("binding?.unbindPlaybackEngine(engine)"))
        assertTrue(source.contains("deferredPlayRequest"))
        val engineProperty = source.substringAfter("val engine: LivePlaybackEngine")
            .substringBefore("override val state")
        assertFalse(engineProperty.contains("get() = controller"))
    }
}
