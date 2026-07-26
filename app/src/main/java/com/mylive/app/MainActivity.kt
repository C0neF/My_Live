package com.mylive.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.ui.navigation.AppNavGraph
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.screen.room.player.LivePlaybackHostSignals
import com.mylive.app.ui.theme.MyLiveTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var navigator: Navigator

    private var autoExitJob: Job? = null

    // Registered unconditionally during init (required by the Activity Result API).
    // Denial degrades gracefully — follow-update and playback notifications simply won't show.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        observePictureInPictureEligibility()
        val initialRoute = intent.getStringExtra(EXTRA_INITIAL_ROUTE)



        // Sync sleep timer settings
        lifecycleScope.launch {
            combine(
                settingsRepository.autoExitEnable,
                settingsRepository.autoExitDuration
            ) { enable, duration ->
                enable to duration
            }.collect { (enable, duration) ->
                autoExitJob?.cancel()
                if (enable) {
                    autoExitJob = lifecycleScope.launch {
                        delay(duration * 60 * 1000L)
                        finish()
                    }
                }
            }
        }

        // Sync follow auto-updates
        lifecycleScope.launch {
            combine(
                settingsRepository.autoUpdateFollowEnable,
                settingsRepository.autoUpdateFollowDuration
            ) { enable, duration ->
                enable to duration
            }.collect { (enable, duration) ->
                com.mylive.app.service.FollowUpdateScheduler.schedule(
                    applicationContext,
                    enable,
                    duration
                )
            }
        }

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(initialValue = 0)
            val styleColor by settingsRepository.styleColor.collectAsStateWithLifecycle(
                initialValue = 0xff3498db.toInt()
            )

            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyLiveTheme(
                darkTheme = darkTheme,
                seedColor = androidx.compose.ui.graphics.Color(styleColor)
            ) {
                AppNavGraph(navigator = navigator, initialRoute = initialRoute)
            }
        }
    }

    /**
     * On Android 13+ POST_NOTIFICATIONS is a runtime permission. Without it, the follow
     * "went live" alerts and the foreground playback notification are silently suppressed.
     * Request it once on launch (the system stops prompting after the user's choices).
     */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Keeps the platform's auto-enter flag in step with live-room PiP eligibility.
     *
     * From Android 12, `setAutoEnterEnabled` is the reliable route into picture-in-picture:
     * [onUserLeaveHint] is not delivered on every path out of the app (gesture navigation to
     * recents, in particular), so relying on it alone dropped the transition on exactly the
     * gesture most people use. The flag is armed only while the user is actually on a live room,
     * so leaving from any other screen still backgrounds the app normally.
     */
    private fun observePictureInPictureEligibility() {
        if (pipEntryMechanism(Build.VERSION.SDK_INT) != PipEntryMechanism.AutoEnter) return
        lifecycleScope.launch {
            combine(
                LivePlaybackHostSignals.autoPipOnLeaveFlow,
                snapshotFlow { navigator.backStack.lastOrNull() }
            ) { autoPipOnLeave, topRoute ->
                shouldEnterPipOnUserLeave(autoPipOnLeave = autoPipOnLeave, topRoute = topRoute)
            }
                .distinctUntilChanged()
                .collect { eligible ->
                    runCatching {
                        setPictureInPictureParams(pictureInPictureParams(autoEnter = eligible))
                    }
                }
        }
    }

    private fun pictureInPictureParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        // Animate out of the video itself rather than cross-fading the whole window.
        val sourceRectHint = LivePlaybackHostSignals.sourceRectHint
        if (sourceRectHint != null) {
            builder.setSourceRectHint(sourceRectHint)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
        }
        return builder.build()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipEntryMechanism(Build.VERSION.SDK_INT) != PipEntryMechanism.ManualOnUserLeave) return
        val enterPip = shouldEnterPipOnUserLeave(
            autoPipOnLeave = LivePlaybackHostSignals.autoPipOnLeave,
            topRoute = navigator.backStack.lastOrNull()
        )
        if (enterPip) {
            try {
                enterPictureInPictureMode(pictureInPictureParams(autoEnter = false))
            } catch (e: Exception) {
                // Some OEMs refuse PiP outright; leaving normally is the correct fallback.
            }
        }
    }

    companion object {
        const val EXTRA_INITIAL_ROUTE = "com.mylive.app.extra.INITIAL_ROUTE"

        /**
         * Test/compat accessor over [LivePlaybackHostSignals.autoPipOnLeave].
         * Production room UI registers through
         * [com.mylive.app.ui.screen.room.player.rememberLivePlaybackHost].
         */
        var isPipSupportedAndActive: Boolean
            get() = LivePlaybackHostSignals.autoPipOnLeave
            set(value) {
                LivePlaybackHostSignals.setAutoPipOnLeave(value)
            }
    }
}

/** How this device gets into picture-in-picture. */
internal enum class PipEntryMechanism {
    /**
     * Android 12+. The window manager enters on its own when the app is backgrounded, provided
     * the flag was armed beforehand. Preferred, because it covers the paths that never deliver
     * `onUserLeaveHint` — gesture navigation to recents above all.
     */
    AutoEnter,

    /** Android 8-11. `onUserLeaveHint` is the only hook available. */
    ManualOnUserLeave
}

internal fun pipEntryMechanism(sdkInt: Int): PipEntryMechanism = when {
    sdkInt >= Build.VERSION_CODES.S -> PipEntryMechanism.AutoEnter
    else -> PipEntryMechanism.ManualOnUserLeave
}

/**
 * Auto picture-in-picture is a live-room feature, not an app-wide one.
 *
 * Leaving for the launcher only shrinks the app into a floating window when the live room detail
 * screen is the destination the user is actually on. Any other screen (index, settings, sync,
 * search, ...) leaves normally, even if a room screen is still finishing its exit animation or a
 * stale host signal is left behind.
 */
internal fun shouldEnterPipOnUserLeave(
    autoPipOnLeave: Boolean,
    topRoute: NavKey?
): Boolean = autoPipOnLeave && topRoute is Route.LiveRoomDetail
