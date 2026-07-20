package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayerSurfaceReleasePolicyTest {

    @Test
    fun media3PlayerViewDetachesPlayerOnAndroidViewRelease() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val playerAndroidView = source.substringAfter("key(playerCompatMode)")
            .substringBefore("// Danmaku SurfaceView overlay")
        assertTrue(playerAndroidView.contains("onRelease = { view ->"))
        assertTrue(playerAndroidView.contains("view.player = null"))
    }
}
