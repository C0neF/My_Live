package com.mylive.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import com.mylive.app.core.model.TabletOrientationMode
import com.mylive.app.core.model.AppLayoutMode
import com.mylive.app.ui.layout.ProvideAppLayout
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.ui.motion.ProvideAppMotionPolicy
import com.mylive.app.ui.navigation.AppNavGraph
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.orientation.resolveTabletOrientationMode
import com.mylive.app.ui.orientation.tabletRequestedOrientation
import com.mylive.app.ui.screen.room.player.LivePlaybackHostSignals
import com.mylive.app.ui.theme.MyLiveTheme
import com.mylive.app.service.FollowUpdateScheduleCoordinator
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

    @Inject
    lateinit var followUpdateScheduleCoordinator: FollowUpdateScheduleCoordinator

    private var autoExitJob: Job? = null

    // Registered unconditionally during init (required by the Activity Result API).
    // Denial degrades gracefully — follow-update and playback notifications simply won't show.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigator.restoreState(savedInstanceState?.getString(NAVIGATION_STATE_KEY))
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        observePictureInPictureEligibility()
        val initialRoute = if (savedInstanceState == null) intent.getStringExtra(EXTRA_INITIAL_ROUTE) else null



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

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(initialValue = 0)
            val styleColor by settingsRepository.styleColor.collectAsStateWithLifecycle(
                initialValue = 0xff3498db.toInt()
            )
            val storedTabletOrientationMode by
                settingsRepository.tabletOrientationMode.collectAsStateWithLifecycle(
                    initialValue = null
                )
            val configuration = LocalConfiguration.current
            val appLayoutMode by settingsRepository.appLayoutMode.collectAsStateWithLifecycle(
                initialValue = AppLayoutMode.Auto
            )

            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ProvideAppLayout(appLayoutMode) {
                val tabletLayout = LocalAppLayout.current.tablet
                val liveRoomOwnsOrientation = navigator.backStack.lastOrNull() is Route.LiveRoomDetail
                LaunchedEffect(tabletLayout, storedTabletOrientationMode, liveRoomOwnsOrientation) {
                    val storedMode = storedTabletOrientationMode ?: return@LaunchedEffect
                    if (liveRoomOwnsOrientation) return@LaunchedEffect
                    val resolvedMode = resolveTabletOrientationMode(storedMode, configuration.orientation)
                    if (tabletLayout && storedMode == TabletOrientationMode.Uninitialized) {
                        settingsRepository.setTabletOrientationMode(resolvedMode)
                    }
                    val orientation = if (tabletLayout) tabletRequestedOrientation(resolvedMode)
                        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    if (requestedOrientation != orientation) requestedOrientation = orientation
                }
                ProvideAppMotionPolicy {
                    MyLiveTheme(
                        darkTheme = darkTheme,
                        seedColor = androidx.compose.ui.graphics.Color(styleColor)
                    ) {
                        Box(Modifier.semantics { testTagsAsResourceId = true }) {
                            AppNavGraph(navigator = navigator, initialRoute = initialRoute)
                        }
                    }
                }
            }
        }
        window.decorView.doOnPreDraw { view ->
            view.post { followUpdateScheduleCoordinator.start() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(NAVIGATION_STATE_KEY, navigator.saveState())
        super.onSaveInstanceState(outState)
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
        private const val NAVIGATION_STATE_KEY = "mylive.navigation.backStack"
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
