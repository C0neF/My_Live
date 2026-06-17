package com.mylive.app.ui.screen.room

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class LiveRoomAutoFullscreenPolicyTest {

    @Test
    fun liveRoomScreenAppliesAutoFullscreenOncePerRoute() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertTrue(source.contains("val autoFullScreen by settingsViewModel.autoFullScreen.collectAsState"))
        assertTrue(source.contains("autoFullscreenAppliedRoute"))
        assertTrue(source.contains("autoFullscreenAppliedRoute = route"))
        assertTrue(source.contains("playerController?.toggleFullscreen()"))
    }

    @Test
    fun liveRoomScreenRestoresFullscreenSystemUiWhenDisposed() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertTrue(source.contains("private fun applyLiveRoomFullscreen("))
        assertTrue(source.contains("private fun restoreLiveRoomSystemUi("))
        assertTrue(source.contains("DisposableEffect(activity)"))
        assertTrue(source.contains("onDispose {"))
        assertTrue(source.contains("restoreLiveRoomSystemUi(act)"))
        assertTrue(source.contains("ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED"))
    }

    @Test
    fun playerViewDoesNotOwnActivityFullscreenSideEffects() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val playerViewBody = source.substringAfter("fun PlayerView(")
            .substringBefore("// Intercept back key when in fullscreen")

        assertFalse(playerViewBody.contains("requestedOrientation"))
        assertFalse(playerViewBody.contains("WindowInsetsCompat.Type.systemBars()"))
    }

    @Test
    fun pictureInPictureDanmakuHonorsHideSetting() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val pipBranch = source.substringAfter("if (isInPip) {")
            .substringBefore("} else if (isLandscape || isFullscreen)")

        assertTrue(source.contains("val pipHideDanmu by settingsViewModel.pipHideDanmu.collectAsState"))
        assertTrue(source.contains("pipDanmakuController"))
        assertTrue(pipBranch.contains("danmuEnable = pipDanmuEnable && !pipHideDanmu"))
        assertTrue(pipBranch.contains("onDanmakuControllerCreated = { pipDanmakuController = it }"))
    }
}
