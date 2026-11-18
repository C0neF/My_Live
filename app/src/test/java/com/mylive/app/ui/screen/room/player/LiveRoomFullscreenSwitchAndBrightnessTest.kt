package com.mylive.app.ui.screen.room.player

import android.app.Application
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mylive.app.data.repository.LiveRoomPreferences
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.service.PlaybackForegroundService
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LiveRoomFullscreenSwitchAndBrightnessTest {
    @get:Rule
    val compose = createComposeRule()

    private val lifecycleOwner = ResumedLifecycleOwner()
    private lateinit var activity: ComponentActivity

    @After
    fun tearDown() {
        LivePlaybackHostSignals.clearPlaybackWindowState()
        PlaybackForegroundService.activePlayer = null
    }

    @Test
    fun fullscreenRoomSwitchPreservesFullscreenWindowAndBrightnessAcrossOverlappingDisposal() {
        val navigator = Navigator()
        val routeA = Route.LiveRoomDetail(roomId = "room-a", siteId = "bilibili")
        navigator.navigate(routeA)

        val preferences = LiveRoomPreferences()
        val startupPrefsA = CompletableDeferred<LiveRoomPreferences>()
        val startupPrefsB = CompletableDeferred<LiveRoomPreferences>()
        val settingsA = mockk<SettingsRepository> {
            every { liveRoomPreferences } returns flow { emit(startupPrefsA.await()) }
        }
        val settingsB = mockk<SettingsRepository> {
            every { liveRoomPreferences } returns flow { emit(startupPrefsB.await()) }
        }

        var showRoomA by mutableStateOf(true)
        var showRoomB by mutableStateOf(false)
        var routeB by mutableStateOf(
            Route.LiveRoomDetail(roomId = "room-b", siteId = "bilibili")
        )
        var hostA: LivePlaybackHost? = null
        var hostB: LivePlaybackHost? = null

        val bindingA = NoOpSessionBinding()
        val bindingB = NoOpSessionBinding()

        compose.setContent {
            activity = LocalActivity.current as ComponentActivity
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                if (showRoomA) {
                    val currentA = rememberLivePlaybackHost(
                        content = LivePlaybackHostContent(
                            routeRoomId = routeA.roomId,
                            routeSiteId = routeA.siteId,
                            roomReady = true,
                            title = "Room A",
                            anchorName = "Anchor A",
                            platform = routeA.siteId,
                            initialFullscreen = routeA.initialFullscreen
                        ),
                        preferences = preferences,
                        controlsLocked = false,
                        exiting = navigator.backStack.lastOrNull() != routeA,
                        binding = bindingA,
                        settingsRepository = settingsA,
                        onPlaybackSourceExhausted = { false },
                        keepWindowOnDispose = {
                            navigator.backStack.lastOrNull() is Route.LiveRoomDetail
                        }
                    )
                    SideEffect { hostA = currentA }
                }
                if (showRoomB) {
                    val currentB = rememberLivePlaybackHost(
                        content = LivePlaybackHostContent(
                            routeRoomId = routeB.roomId,
                            routeSiteId = routeB.siteId,
                            roomReady = true,
                            title = "Room B",
                            anchorName = "Anchor B",
                            platform = routeB.siteId,
                            initialFullscreen = routeB.initialFullscreen
                        ),
                        preferences = preferences,
                        controlsLocked = false,
                        exiting = navigator.backStack.lastOrNull() != routeB,
                        binding = bindingB,
                        settingsRepository = settingsB,
                        onPlaybackSourceExhausted = { false },
                        keepWindowOnDispose = {
                            navigator.backStack.lastOrNull() is Route.LiveRoomDetail
                        }
                    )
                    SideEffect { hostB = currentB }
                }
            }
        }

        compose.runOnIdle { startupPrefsA.complete(preferences) }
        compose.waitUntil(timeoutMillis = 5_000) { hostA?.playerController != null }

        compose.runOnIdle {
            navigator.registerRouteExitHandler(routeA) {
                if (shouldPrepareLivePlaybackExit(navigator.backStack.lastOrNull())) {
                    hostA?.prepareToLeave()
                }
            }
            val activeHostA = checkNotNull(hostA)
            activeHostA.toggleFullscreen()
            checkNotNull(activeHostA.playerController).setBrightness(activity, 0.25f)
        }
        compose.waitForIdle()

        compose.runOnIdle {
            val activeHostA = checkNotNull(hostA)
            assertTrue(activeHostA.isFullscreen)
            assertEquals(0.75f, activity.window.attributes.screenBrightness, 0.001f)

            val targetRouteB = Route.LiveRoomDetail(
                roomId = "room-b",
                siteId = "bilibili",
                initialIsFollowing = true,
                initialFullscreen = activeHostA.isFullscreen
            )
            routeB = targetRouteB
            navigator.navigate(
                route = targetRouteB,
                singleTop = true,
                popUpToRoute = Route.Index::class.java,
                inclusive = false
            )
            showRoomB = true
        }
        compose.waitForIdle()

        // Even before room B's async session finishes creating, room B stays in fullscreen.
        compose.runOnIdle {
            assertNull(hostB?.playerController)
            assertTrue("Incoming room must start in fullscreen on frame 0", checkNotNull(hostB).isFullscreen)
            assertNotEquals(
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                activity.requestedOrientation
            )
            assertEquals(0.75f, activity.window.attributes.screenBrightness, 0.001f)
            startupPrefsB.complete(preferences)
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) { hostB?.playerController != null }

        // Outgoing room A finishes its exit transition and disposes after room B is active.
        compose.runOnIdle { showRoomA = false }
        compose.waitForIdle()

        compose.runOnIdle {
            val activeHostB = checkNotNull(hostB)
            assertTrue("Outgoing room disposal must not exit fullscreen", activeHostB.isFullscreen)
            assertNotEquals(
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                activity.requestedOrientation
            )
            assertTrue(
                "Window wake flag must stay active for incoming room",
                (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
            )
            assertEquals(0.75f, activity.window.attributes.screenBrightness, 0.001f)
            assertEquals(
                0.75f,
                checkNotNull(activeHostB.playerController).state.value.brightnessOverride ?: -1f,
                0.001f
            )

            // Pressing Back / exiting fullscreen in room B leaves fullscreen cleanly.
            activeHostB.toggleFullscreen()
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertFalse(hostB!!.isFullscreen)
            showRoomB = false
            navigator.goBack(expectedTop = routeB)
        }
        compose.waitForIdle()
    }

    @Test
    fun leavingPlaybackPageResetsBrightnessToSystemAndNewRoomStartsWithSystemBrightness() {
        val navigator = Navigator()
        val routeA = Route.LiveRoomDetail(roomId = "room-a", siteId = "bilibili")
        navigator.navigate(routeA)

        val preferences = LiveRoomPreferences()
        val startupPrefsA = CompletableDeferred<LiveRoomPreferences>()
        val startupPrefsC = CompletableDeferred<LiveRoomPreferences>()
        val settingsA = mockk<SettingsRepository> {
            every { liveRoomPreferences } returns flow { emit(startupPrefsA.await()) }
        }
        val settingsC = mockk<SettingsRepository> {
            every { liveRoomPreferences } returns flow { emit(startupPrefsC.await()) }
        }

        var showRoomA by mutableStateOf(true)
        var showRoomC by mutableStateOf(false)
        val routeC = Route.LiveRoomDetail(roomId = "room-c", siteId = "douyu")
        var hostA: LivePlaybackHost? = null
        var hostC: LivePlaybackHost? = null
        val bindingA = NoOpSessionBinding()
        val bindingC = NoOpSessionBinding()

        compose.setContent {
            activity = LocalActivity.current as ComponentActivity
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                if (showRoomA) {
                    val currentA = rememberLivePlaybackHost(
                        content = LivePlaybackHostContent(
                            routeRoomId = routeA.roomId,
                            routeSiteId = routeA.siteId,
                            roomReady = true,
                            title = "Room A",
                            anchorName = "Anchor A",
                            platform = routeA.siteId
                        ),
                        preferences = preferences,
                        controlsLocked = false,
                        exiting = navigator.backStack.lastOrNull() != routeA,
                        binding = bindingA,
                        settingsRepository = settingsA,
                        onPlaybackSourceExhausted = { false },
                        keepWindowOnDispose = {
                            navigator.backStack.lastOrNull() is Route.LiveRoomDetail
                        }
                    )
                    SideEffect { hostA = currentA }
                }
                if (showRoomC) {
                    val currentC = rememberLivePlaybackHost(
                        content = LivePlaybackHostContent(
                            routeRoomId = routeC.roomId,
                            routeSiteId = routeC.siteId,
                            roomReady = true,
                            title = "Room C",
                            anchorName = "Anchor C",
                            platform = routeC.siteId
                        ),
                        preferences = preferences,
                        controlsLocked = false,
                        exiting = navigator.backStack.lastOrNull() != routeC,
                        binding = bindingC,
                        settingsRepository = settingsC,
                        onPlaybackSourceExhausted = { false },
                        keepWindowOnDispose = {
                            navigator.backStack.lastOrNull() is Route.LiveRoomDetail
                        }
                    )
                    SideEffect { hostC = currentC }
                }
            }
        }

        compose.runOnIdle { startupPrefsA.complete(preferences) }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) { hostA?.playerController != null }

        compose.runOnIdle {
            navigator.registerRouteExitHandler(routeA) {
                if (shouldPrepareLivePlaybackExit(navigator.backStack.lastOrNull())) {
                    hostA?.prepareToLeave()
                }
            }
            checkNotNull(hostA?.playerController).setBrightness(activity, 0.3f)
            assertEquals(0.8f, activity.window.attributes.screenBrightness, 0.001f)

            // Leave playback page back to Index.
            assertTrue(navigator.goBack(expectedTop = routeA))
            assertEquals(
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
                activity.window.attributes.screenBrightness,
                0.001f
            )
            showRoomA = false
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
                activity.window.attributes.screenBrightness,
                0.001f
            )
            assertNull(LivePlaybackHostSignals.playbackBrightnessOverride)

            // Enter a new room from Index: brightness starts synced with system brightness.
            navigator.navigate(routeC)
            showRoomC = true
        }
        compose.waitForIdle()
        compose.runOnIdle { startupPrefsC.complete(preferences) }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) { hostC?.playerController != null }

        compose.runOnIdle {
            assertEquals(
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
                activity.window.attributes.screenBrightness,
                0.001f
            )
            assertNull(hostC!!.playerController!!.state.value.brightnessOverride)
            showRoomC = false
        }
        compose.waitForIdle()
    }

    @Test
    fun pushingNonPlaybackRouteSyncsSystemBrightnessAndReturningRestoresPlaybackBrightness() {
        val navigator = Navigator()
        val routeA = Route.LiveRoomDetail(roomId = "room-a", siteId = "bilibili")
        navigator.navigate(routeA)

        val preferences = LiveRoomPreferences()
        val settings = mockk<SettingsRepository> {
            every { liveRoomPreferences } returns flow { emit(preferences) }
        }
        var showRoomA by mutableStateOf(true)
        var hostA: LivePlaybackHost? = null
        val bindingA = NoOpSessionBinding()

        compose.setContent {
            activity = LocalActivity.current as ComponentActivity
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                if (showRoomA) {
                    val currentA = rememberLivePlaybackHost(
                        content = LivePlaybackHostContent(
                            routeRoomId = routeA.roomId,
                            routeSiteId = routeA.siteId,
                            roomReady = true,
                            title = "Room A",
                            anchorName = "Anchor A",
                            platform = routeA.siteId
                        ),
                        preferences = preferences,
                        controlsLocked = false,
                        exiting = navigator.backStack.lastOrNull() != routeA,
                        binding = bindingA,
                        settingsRepository = settings,
                        onPlaybackSourceExhausted = { false },
                        keepWindowOnDispose = {
                            navigator.backStack.lastOrNull() is Route.LiveRoomDetail
                        }
                    )
                    SideEffect { hostA = currentA }
                }
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) { hostA?.playerController != null }

        compose.runOnIdle {
            hostA!!.playerController!!.setBrightness(activity, -0.2f)
            assertEquals(0.3f, activity.window.attributes.screenBrightness, 0.001f)

            // Push a non-playback route on top of LiveRoomDetail.
            navigator.navigate(Route.SettingsDanmuShield)
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
                activity.window.attributes.screenBrightness,
                0.001f
            )

            // Return to LiveRoomDetail.
            assertTrue(navigator.goBack(expectedTop = Route.SettingsDanmuShield))
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(0.3f, activity.window.attributes.screenBrightness, 0.001f)
            showRoomA = false
        }
        compose.waitForIdle()
    }

    private class NoOpSessionBinding : LivePlaybackSessionBinding {
        override fun bindPlaybackEngine(engine: LivePlaybackEngine) = Unit
        override fun unbindPlaybackEngine(engine: LivePlaybackEngine) = Unit
        override fun onPlaybackEngineReady() = Unit
    }

    private class ResumedLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        override val lifecycle: Lifecycle get() = registry
    }
}
