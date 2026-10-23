package com.mylive.app.ui.screen.room.player

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.secure.CredentialCipher
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import com.mylive.app.data.repository.LiveRoomPreferences
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.service.PlaybackForegroundService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Actual Compose Host, Session and ExoPlayer with synthetic public PiP/lifecycle events.
 * This proves the observer boundary without locking the device or opening a live room.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class PlaybackHostVisibilityAndroidTest {
    // Queue DataStore continuations for the UI-driven test clock instead of resuming the
    // Host effect immediately on the DataStore worker through the default Unconfined dispatcher.
    @get:Rule val compose = createComposeRule(effectContext = StandardTestDispatcher())

    private val owner = VisibilityLifecycleOwner()
    private var showHost by mutableStateOf(true)
    private var host: LivePlaybackHost? = null
    private lateinit var activity: ComponentActivity
    private lateinit var settings: SettingsRepository
    private val binding = object : LivePlaybackSessionBinding {
        override fun bindPlaybackEngine(engine: LivePlaybackEngine) = Unit
        override fun unbindPlaybackEngine(engine: LivePlaybackEngine) = Unit
        override fun onPlaybackEngineReady() = Unit
    }

    @Before
    fun useOnlyAuditSettings() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Build with -PisolatedDeviceTests=true before running this host test",
            "com.mylive.app.audit", target.packageName)
        val store = SettingsDataStore(target)
        val cipher = object : CredentialCipher {
            override fun encrypt(plainText: String): String = error("This test must not write credentials")
            override fun decrypt(cipherText: String): String = error("This test must not read credentials")
        }
        // Host only reads startup playback preferences in the isolated package; no settings write.
        settings = SettingsRepository(store, SensitiveCredentialStore(store, cipher))
    }

    @After
    fun releaseHost() {
        if (!::settings.isInitialized) return
        compose.runOnIdle { showHost = false }
        compose.waitForIdle()
        PlaybackForegroundService.activePlayer = null
    }

    @Test
    fun anAlreadyPausedPipHostMustPauseWhenItBecomesInvisible() {
        renderHost()
        enterPip(playbackRequested = true)

        compose.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        compose.waitForIdle()

        compose.runOnIdle {
            assertFalse("PiP ON_STOP without another ON_PAUSE must honor the background playback ban",
                host!!.playerController!!.player!!.playWhenReady)
        }
    }

    @Test
    fun visiblePipPlaybackContinuesBeforeTheHostIsStopped() {
        renderHost()
        enterPip(playbackRequested = true)

        compose.runOnIdle {
            assertTrue("PiP is still a visible playback surface while the host is STARTED",
                host!!.playerController!!.player!!.playWhenReady)
        }
    }

    @Test
    fun manuallyPausedPipRemainsPausedAfterStopAndForegroundReturn() {
        renderHost()
        enterPip(playbackRequested = false)
        compose.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        compose.waitForIdle()
        compose.runOnIdle {
            dispatchPip(false)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertFalse("A newer foreground transition must preserve manual pause",
                host!!.playerController!!.player!!.playWhenReady)
        }
    }

    private fun renderHost() {
        val preferences = LiveRoomPreferences(allowBackgroundPlayback = false, playerAutoPause = true)
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
        // Compose 1.9.5 only advances queued dispatcher work while pumping its main clock.
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(timeoutMillis = 5_000) { host?.playerController != null }
    }

    private fun enterPip(playbackRequested: Boolean) {
        compose.runOnIdle {
            if (playbackRequested) host!!.playerController!!.resume() else host!!.playerController!!.pause()
            // Match the supported ordering where ON_PAUSE precedes the PiP mode callback.
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            dispatchPip(true)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("The actual Host must receive the public PiP callback", host!!.isInPictureInPicture)
            assertEquals(playbackRequested, host!!.playerController!!.player!!.playWhenReady)
        }
    }

    @Suppress("DEPRECATION")
    private fun dispatchPip(inPip: Boolean) {
        activity.onPictureInPictureModeChanged(inPip, Configuration(activity.resources.configuration))
    }

    private class VisibilityLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
}
