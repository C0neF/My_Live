package com.mylive.app.ui.screen.room

import android.content.pm.ActivityInfo
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LiveRoomAutoFullscreenPolicyTest {

    @Test
    fun liveRoomScreenAppliesAutoFullscreenOncePerRoute() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertTrue(source.contains("val autoFullScreen = liveRoomPreferences.autoFullScreen"))
        assertTrue(source.contains("autoFullscreenAppliedRoute"))
        assertTrue(source.contains("autoFullscreenAppliedRoute = route"))
        assertTrue(source.contains("playerController?.toggleFullscreen()"))
    }

    @Test
    fun liveRoomScreenRestoresFullscreenSystemUiWhenDisposed() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertTrue(source.contains("private fun applyLiveRoomFullscreen("))
        assertTrue(source.contains("private fun restoreLiveRoomSystemUi("))
        assertTrue(source.contains("DisposableEffect(activity, liveRoomView)"))
        assertTrue(source.contains("onDispose {"))
        assertTrue(source.contains("restoreLiveRoomSystemUi(it)"))
        assertTrue(source.contains("ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED"))
    }

    @Test
    fun liveRoomScreenKeepsScreenAwakeUntilDisposed() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val disposeEffect = source.substringAfter("DisposableEffect(activity, liveRoomView)")
            .substringBefore("// Shared back handler")

        assertTrue(source.contains("WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON"))
        assertTrue(source.contains("liveRoomView.keepScreenOn = true"))
        assertTrue(disposeEffect.contains("keepLiveRoomScreenAwake(activity, true)"))
        assertTrue(disposeEffect.contains("keepLiveRoomScreenAwake(activity, false)"))
        assertTrue(disposeEffect.contains("liveRoomView.keepScreenOn = false"))
        assertTrue(source.contains("ON_RESUME"))
        assertTrue(source.contains("keepLiveRoomScreenAwake(activity, true)"))
    }

    @Test
    fun lockedControlsFreezeOrientationAndUnlockedFullscreenAllowsPortraitExit() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LOCKED,
            liveRoomRequestedOrientation(
                fullscreen = true,
                controlsLocked = true,
                isLandscape = true,
                hasReachedLandscapeInFullscreen = true
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            liveRoomRequestedOrientation(
                fullscreen = true,
                controlsLocked = false,
                isLandscape = false,
                hasReachedLandscapeInFullscreen = false
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            liveRoomRequestedOrientation(
                fullscreen = true,
                controlsLocked = false,
                isLandscape = true,
                hasReachedLandscapeInFullscreen = true
            )
        )
        assertFalse(
            shouldExitFullscreenOnPortraitRotation(
                fullscreen = true,
                controlsLocked = true,
                isLandscape = false,
                hasReachedLandscapeInFullscreen = true
            )
        )
        assertTrue(
            shouldExitFullscreenOnPortraitRotation(
                fullscreen = true,
                controlsLocked = false,
                isLandscape = false,
                hasReachedLandscapeInFullscreen = true
            )
        )
        assertFalse(
            shouldExitFullscreenOnPortraitRotation(
                fullscreen = true,
                controlsLocked = false,
                isLandscape = false,
                hasReachedLandscapeInFullscreen = false
            )
        )
    }

    @Test
    fun playerViewReportsControlsLockToParentAndDoesNotOwnOrientation() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val playerViewBody = source.substringAfter("fun PlayerView(")
            .substringBefore("// Intercept back key when in fullscreen")

        assertFalse(playerViewBody.contains("requestedOrientation"))
        assertFalse(playerViewBody.contains("WindowInsetsCompat.Type.systemBars()"))
        assertTrue(source.contains("onControlsLockChange: ((Boolean) -> Unit)? = null"))
        assertTrue(source.contains("onControlsLockChange?.invoke(isLocked)"))
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

        assertTrue(source.contains("val pipHideDanmu = liveRoomPreferences.pipHideDanmu"))
        assertTrue(source.contains("val pipDanmuEnable = liveRoomPreferences.danmuEnable"))
        assertTrue(source.contains("pipDanmakuController"))
        assertTrue(pipBranch.contains("danmuEnable = pipDanmuEnable && !pipHideDanmu"))
        assertTrue(pipBranch.contains("onDanmakuControllerCreated = { pipDanmakuController = it }"))
    }

    @Test
    fun tabletLiveRoomRoutingMatchesMainBranchPolicy() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertTrue(source.contains("} else if (isLandscape || isFullscreen) {"))
        assertFalse(source.contains("liveRoomUseTabletLayout("))
        assertFalse(source.contains("val useTabletLayout"))
        assertFalse(source.contains("screenWidthDp = configuration.screenWidthDp"))
        assertFalse(source.contains("} else if (useTabletLayout) {"))
        assertFalse(source.contains("private fun TabletLayout("))
        assertFalse(source.contains("LiveRoomTabletSidePanel("))
        assertFalse(source.contains("liveRoomTabletSidePanelWidthDp"))
    }

    @Test
    fun landscapeLiveRoomSidePanelMatchesMainSingleChatPolicy() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertEquals(listOf(LiveRoomTabType.CHAT), resolveLandscapeLiveRoomTabs())
        assertEquals(320f, liveRoomInitialSidePanelOffsetPx(320f), 0.001f)
        assertEquals(0f, liveRoomSidePanelOffsetAfterDrag(8f, -32f, 320f), 0.001f)
        assertEquals(320f, liveRoomSidePanelOffsetAfterDrag(300f, 40f, 320f), 0.001f)
        assertTrue(source.contains("val roomTabs = remember { resolveLandscapeLiveRoomTabs() }"))
        assertTrue(source.contains("LandscapeRoomSidePanelHeader("))
        assertTrue(source.contains("onQuickAccessClick = quickAccessAction"))
        assertTrue(source.contains("onFollowClick = { viewModel.toggleFollow() }"))
        assertTrue(source.contains("if (roomTabs.contains(LiveRoomTabType.CHAT)) {"))
        assertFalse(source.contains("val liveRoomTabSort by settingsViewModel.liveRoomTabSort.collectAsState()"))
        assertFalse(source.contains("selectedLandscapeTab"))
        assertFalse(source.contains("HorizontalPager(\n                state = landscapePagerState"))
    }
}
