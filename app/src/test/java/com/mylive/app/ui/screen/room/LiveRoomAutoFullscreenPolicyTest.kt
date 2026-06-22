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
    fun liveRoomScreenKeepsScreenAwakeUntilDisposed() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val disposeEffect = source.substringAfter("DisposableEffect(activity)")
            .substringBefore("// Shared back handler")

        assertTrue(source.contains("WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON"))
        assertTrue(disposeEffect.contains("keepLiveRoomScreenAwake(act, true)"))
        assertTrue(disposeEffect.contains("keepLiveRoomScreenAwake(act, false)"))
        assertTrue(source.contains("activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"))
        assertTrue(source.contains("activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"))
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
    fun playerViewFullscreenButtonCanBeOwnedByParentLayout() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val signature = source.substringAfter("fun PlayerView(")
            .substringBefore(") {")
        val controlsCall = source.substringAfter("// Normal controls overlay")
            .substringAfter("PlayerBottomBar(")
            .substringBefore("onRefreshClick = onRefreshClick")

        assertTrue(signature.contains("isFullscreenOverride: Boolean? = null"))
        assertTrue(signature.contains("onFullscreenClick: (() -> Unit)? = null"))
        assertTrue(controlsCall.contains("isFullscreen = isFullscreenOverride ?: state.isFullscreen"))
        assertTrue(controlsCall.contains("onFullscreenClick = { onFullscreenClick?.invoke() ?: playerController?.toggleFullscreen() }"))
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
