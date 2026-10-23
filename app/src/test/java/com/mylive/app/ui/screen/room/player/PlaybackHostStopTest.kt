package com.mylive.app.ui.screen.room.player

import android.app.Application
import android.content.res.Configuration
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the real Host observer, Session and ExoPlayer without opening a media source. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaybackHostStopTest {
    @get:Rule val compose = createComposeRule()

    private val owner = VisibilityLifecycleOwner()
    private var showHost by mutableStateOf(true)
    private var host: LivePlaybackHost? = null
    private lateinit var activity: ComponentActivity
    private val binding = object : LivePlaybackSessionBinding {
        override fun bindPlaybackEngine(engine: LivePlaybackEngine) = Unit
        override fun unbindPlaybackEngine(engine: LivePlaybackEngine) = Unit
        override fun onPlaybackEngineReady() = Unit
    }

    @After
    fun releaseHost() {
        compose.runOnIdle { showHost = false }
        compose.waitForIdle()
        PlaybackForegroundService.activePlayer = null
    }

    @Test
    fun pictureInPicturePausesWhenTheAlreadyPausedHostBecomesStopped() {
        renderHost()
        enterPictureInPicture(playbackRequested = true)

        // Entering PiP already dispatched ON_PAUSE. Locking the screen dispatches ON_STOP,
        // without a second ON_PAUSE, and the composition/session remain alive.
        compose.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        compose.waitForIdle()

        compose.runOnIdle {
            assertFalse("An invisible PiP host must honor disabled background playback",
                host!!.playerController!!.player!!.playWhenReady)
        }
    }

    @Test
    fun anExistingManualPauseSurvivesPictureInPictureStopAndForegroundReturn() {
        renderHost()
        enterPictureInPicture(playbackRequested = false)
        compose.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        compose.waitForIdle()
        compose.runOnIdle {
            dispatchPipMode(false)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertFalse("A stopped/started host must preserve the user's manual pause",
                host!!.playerController!!.player!!.playWhenReady)
        }
    }

    private fun renderHost() {
        val preferences = LiveRoomPreferences(allowBackgroundPlayback = false, playerAutoPause = true)
        val startupPreferences = CompletableDeferred<LiveRoomPreferences>()
        val settings = mockk<SettingsRepository> {
            every { liveRoomPreferences } returns flow { emit(startupPreferences.await()) }
        }
        compose.setContent {
            activity = LocalActivity.current as ComponentActivity
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (showHost) {
                    val current = rememberLivePlaybackHost(
                        content = LivePlaybackHostContent("visibility-room", "test", true, "room", "anchor", "test"),
                        preferences = preferences,
                        controlsLocked = false,
                        exiting = false,
                        binding = binding,
                        settingsRepository = settings,
                        onPlaybackSourceExhausted = { false }
                    )
                    SideEffect { host = current }
                }
            }
        }
        // Commit the initial composition before delivering asynchronous DataStore preferences.
        // A synchronous flowOf completed inside LaunchedEffect let the initial null-keyed
        // disposal effect capture and then release the just-created Session before this fixture
        // reached its first lifecycle event.
        compose.runOnIdle {
            assertNull("The startup preferences must still be pending", host?.playerController)
            startupPreferences.complete(preferences)
        }
        compose.waitUntil(timeoutMillis = 5_000) { host?.playerController != null }
        compose.runOnIdle { assertNotNull(host!!.playerController!!.player) }
    }

    private fun enterPictureInPicture(playbackRequested: Boolean) {
        compose.runOnIdle {
            val controller = host!!.playerController!!
            if (playbackRequested) controller.resume() else controller.pause()
            // Model the supported OEM ordering where ON_PAUSE arrives before the PiP flag.
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            dispatchPipMode(true)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("The real Host must receive the public PiP callback", host!!.isInPictureInPicture)
            assertTrue("PiP entry must preserve the initial playback intent",
                host!!.playerController!!.player!!.playWhenReady == playbackRequested)
        }
    }

    @Suppress("DEPRECATION")
    private fun dispatchPipMode(inPip: Boolean) {
        activity.onPictureInPictureModeChanged(inPip, Configuration(activity.resources.configuration))
    }

    private class VisibilityLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
}
