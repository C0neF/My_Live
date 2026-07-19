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
    fun playerControllerImplementsEnginePort() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerController.kt")
            .readText()
        assertTrue(source.contains(": LivePlaybackEngine"))
    }

    @Test
    fun sessionBindsEngineThroughPort() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/LivePlaybackSession.kt")
            .readText()
        assertTrue(source.contains("val engine: LivePlaybackEngine"))
        assertTrue(source.contains("viewModel.bindPlaybackEngine(engine)"))
        assertTrue(source.contains("viewModel?.unbindPlaybackEngine(engine)"))
    }
}
