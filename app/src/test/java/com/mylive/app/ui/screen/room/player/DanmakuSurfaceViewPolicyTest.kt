package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DanmakuSurfaceViewPolicyTest {

    @Test
    fun surfaceLifecycleDoesNotBlockWaitingForRenderThread() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/DanmakuSurfaceView.kt")
            .readText()
        val stopBlock = source.substringAfter("private fun stopRenderThread()")
            .substringBefore("private fun joinStoppedRenderThread")

        assertFalse(stopBlock.contains(".join("))
        assertTrue(source.contains("joinStoppedRenderThread"))
    }
}
