package com.mylive.app.ui.screen.room

import android.content.pm.ActivityInfo
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun lockedControlsFreezeOrientationAndFullscreenStaysLandscape() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LOCKED,
            liveRoomRequestedOrientation(fullscreen = true, controlsLocked = true)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            liveRoomRequestedOrientation(fullscreen = true, controlsLocked = false)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            liveRoomRequestedOrientation(fullscreen = false, controlsLocked = false)
        )
    }

    @Test
    fun leavingFullscreenRequestsPortraitUntilPortraitIsReached() {
        // Back key / fullscreen button leaves fullscreen and pulls the window back to portrait,
        // even when the device is still held sideways.
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            liveRoomRequestedOrientation(
                fullscreen = false,
                controlsLocked = false,
                restorePortrait = true
            )
        )
        // Once portrait is reached the request is dropped and the system owns rotation again.
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            liveRoomRequestedOrientation(
                fullscreen = false,
                controlsLocked = false,
                restorePortrait = false
            )
        )
        // Fullscreen and locked controls keep priority over the portrait restore request.
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            liveRoomRequestedOrientation(
                fullscreen = true,
                controlsLocked = false,
                restorePortrait = true
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LOCKED,
            liveRoomRequestedOrientation(
                fullscreen = true,
                controlsLocked = true,
                restorePortrait = true
            )
        )
    }

    @Test
    fun systemBackLeavesFullscreenAndTheRoomRestoresPortrait() {
        val player = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val screen = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        // The system back key exits fullscreen first instead of leaving the room.
        assertTrue(player.contains("BackHandler(enabled = isFullscreen) {"))
        // The room then requests portrait once, and releases it as soon as portrait is reached.
        assertTrue(screen.contains("var restorePortraitAfterFullscreen by remember { mutableStateOf(false) }"))
        assertTrue(screen.contains("restorePortraitAfterFullscreen = true"))
        assertTrue(screen.contains("if (restorePortraitAfterFullscreen && !isLandscape) {"))
        assertTrue(screen.contains("restorePortrait = restorePortraitAfterFullscreen"))
        // Rotation itself still never exits fullscreen and never renders a portrait fullscreen.
        assertFalse(screen.contains("OrientationEventListener"))
    }

    @Test
    fun fullscreenNeverRendersPortraitAndNeverExitsOnRotation() {
        // Fullscreen is landscape-only: SENSOR_LANDSCAPE allows the two landscape directions and
        // nothing else, so holding the device upright cannot letterbox the player into a portrait
        // window (the bug reported on the device with auto-rotate turned off).
        val fullscreenOrientation =
            liveRoomRequestedOrientation(fullscreen = true, controlsLocked = false)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, fullscreenOrientation)
        assertNotEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, fullscreenOrientation)
        assertNotEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR, fullscreenOrientation)
        assertNotEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, fullscreenOrientation)

        // Locked controls still freeze the orientation instead of leaving fullscreen.
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LOCKED,
            liveRoomRequestedOrientation(fullscreen = true, controlsLocked = true)
        )
    }

    @Test
    fun fullscreenOrientationPolicyIsDocumentedInSource() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertTrue(source.contains("SCREEN_ORIENTATION_SENSOR_LANDSCAPE"))
        // Free-rotation modes must not come back: any of them lets a physically upright device
        // turn fullscreen into a portrait window.
        assertFalse(source.contains("SCREEN_ORIENTATION_FULL_SENSOR"))
        assertFalse(source.contains("SCREEN_ORIENTATION_SENSOR,"))
        // The exit-on-portrait-rotation helper is gone entirely, not merely disabled, so no call
        // site can reintroduce the old behaviour by accident.
        assertFalse(source.contains("shouldExitFullscreenOnPortraitRotation"))
        // The landscape-latch state is gone too; fullscreen no longer has a "free rotation later"
        // phase that could drop into portrait.
        assertFalse(source.contains("hasReachedLandscapeInFullscreen"))
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
