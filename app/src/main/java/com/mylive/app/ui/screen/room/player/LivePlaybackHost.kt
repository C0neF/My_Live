package com.mylive.app.ui.screen.room.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.core.model.TabletOrientationMode
import com.mylive.app.data.repository.LiveRoomPreferences
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.ui.orientation.resolveTabletOrientationMode
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.orientation.tabletRequestedOrientation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal data class LivePlaybackHostContent(
    val routeRoomId: String,
    val routeSiteId: String,
    val roomReady: Boolean,
    val title: String,
    val anchorName: String,
    val platform: String
)

/**
 * Observable host state plus the few host actions the room UI can request.
 *
 * Lifecycle, PiP, fullscreen, orientation, wake-lock, foreground playback, and release ordering
 * stay inside [rememberLivePlaybackHost].
 */
@Immutable
internal class LivePlaybackHost internal constructor(
    val playerController: PlayerController?,
    val isInPictureInPicture: Boolean,
    val isFullscreen: Boolean,
    private val playbackSession: LivePlaybackSession?,
    private val activity: Activity?,
    private val tabletOrientationMode: TabletOrientationMode?
) {
    fun toggleFullscreen() {
        playbackSession?.toggleFullscreen()
    }

    fun prepareToLeave() {
        activity?.let {
            prepareLivePlaybackHostExit(
                activity = it,
                tabletOrientationMode = tabletOrientationMode
            )
        }
    }

}

/**
 * Owns the Android host side of one live playback session.
 *
 * The room UI supplies current room facts and preferences. This module owns all host effects and
 * returns only render state plus explicit user actions.
 */
@Composable
internal fun rememberLivePlaybackHost(
    content: LivePlaybackHostContent,
    preferences: LiveRoomPreferences,
    controlsLocked: Boolean,
    exiting: Boolean,
    binding: LivePlaybackSessionBinding,
    settingsRepository: SettingsRepository,
    onPlaybackSourceExhausted: () -> Boolean
): LivePlaybackHost {
    val context = LocalContext.current
    val activity = context as? Activity
    val componentActivity = context as? ComponentActivity
    val liveRoomView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val tabletOrientationMode = if (
        LocalAppLayout.current.tablet
    ) {
        resolveTabletOrientationMode(
            storedMode = preferences.tabletOrientationMode,
            currentConfigurationOrientation = configuration.orientation
        )
    } else {
        null
    }
    val currentContent = rememberUpdatedState(content)
    val currentPreferences = rememberUpdatedState(preferences)
    val currentSourceExhausted = rememberUpdatedState(onPlaybackSourceExhausted)

    var isInPictureInPicture by remember(componentActivity) {
        mutableStateOf(componentActivity?.isInPictureInPictureMode == true)
    }
    DisposableEffect(componentActivity) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> {
            isInPictureInPicture = it.isInPictureInPictureMode
        }
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    val autoPipOwner = remember { Any() }
    DisposableEffect(preferences.autoPipOnExit, autoPipOwner) {
        LivePlaybackHostSignals.setAutoPipOnLeave(autoPipOwner, preferences.autoPipOnExit)
        onDispose {
            LivePlaybackHostSignals.setAutoPipOnLeave(autoPipOwner, false)
        }
    }

    var currentSession by remember(binding) { mutableStateOf<LivePlaybackSession?>(null) }
    // Each effect must keep the Session from its own composition through callback and disposal.
    val playbackSession = currentSession
    val playerController = playbackSession?.playerController

    LaunchedEffect(binding, settingsRepository) {
        val startupPreferences = settingsRepository.liveRoomPreferences.first()
        val session = LivePlaybackSession.create(
            context = context,
            hardwareDecodeEnabled = startupPreferences.hardwareDecode,
            forceHttps = startupPreferences.playerForceHttps,
            onPlaybackSourceExhausted = {
                currentSourceExhausted.value()
            }
        )
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            session.pauseForHost(
                content = currentContent.value,
                preferences = startupPreferences,
                inPictureInPicture = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                    componentActivity?.isInPictureInPictureMode == true
            )
        }
        currentSession = session
        session.attach(binding)
    }

    DisposableEffect(playbackSession) {
        val session = playbackSession
        onDispose {
            session?.release()
        }
    }

    LaunchedEffect(exiting, playbackSession) {
        if (exiting) {
            playbackSession?.stop()
        }
    }

    LaunchedEffect(playbackSession, preferences.playerForceHttps) {
        playbackSession?.setForceHttps(preferences.playerForceHttps)
    }

    DisposableEffect(
        lifecycleOwner,
        playbackSession,
        componentActivity,
        activity,
        liveRoomView
    ) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // Unlocking a still-visible PiP window need not resume its Activity.
                    if (componentActivity?.isInPictureInPictureMode == true) {
                        playbackSession?.onHostResume()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    playbackSession?.pauseForHost(
                        content = currentContent.value,
                        preferences = currentPreferences.value,
                        inPictureInPicture =
                            componentActivity?.isInPictureInPictureMode == true
                    )
                }
                Lifecycle.Event.ON_STOP -> {
                    // PiP entry already delivered ON_PAUSE. Once stopped, its video is no
                    // longer visible even if Android still reports PiP mode (e.g. screen off).
                    playbackSession?.pauseForHost(
                        content = currentContent.value,
                        preferences = currentPreferences.value,
                        inPictureInPicture = false
                    )
                }
                Lifecycle.Event.ON_RESUME -> {
                    liveRoomView.keepScreenOn = true
                    keepLivePlaybackHostAwake(activity, true)
                    playbackSession?.onHostResume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            playbackSession?.onHostResume()
        } else {
            playbackSession?.pauseForHost(
                content = currentContent.value,
                preferences = currentPreferences.value,
                inPictureInPicture = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                    componentActivity?.isInPictureInPictureMode == true
            )
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playbackSession?.stopForegroundService()
        }
    }

    // Some OEMs deliver ON_PAUSE before the PiP mode flag changes.
    LaunchedEffect(isInPictureInPicture, playbackSession, lifecycleOwner) {
        if (isInPictureInPicture && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            playbackSession?.onHostResume()
        }
    }

    val isFullscreen by remember(playerController) {
        playerController?.state?.map { it.isFullscreen } ?: flowOf(false)
    }.collectAsStateWithLifecycle(initialValue = false)

    var autoFullscreenAppliedRoute by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(
        preferences.autoFullScreen,
        playerController,
        content.routeRoomId,
        content.routeSiteId,
        content.roomReady,
        isFullscreen,
        tabletOrientationMode
    ) {
        val route = content.routeSiteId to content.routeRoomId
        if (
            shouldApplyLivePlaybackAutoFullscreen(
                autoFullScreen = preferences.autoFullScreen,
                tabletOrientationMode = tabletOrientationMode
            ) &&
            playerController != null &&
            content.roomReady &&
            autoFullscreenAppliedRoute != route &&
            !isFullscreen
        ) {
            autoFullscreenAppliedRoute = route
            playbackSession?.toggleFullscreen()
        }
    }

    var wasFullscreen by remember { mutableStateOf(false) }
    var restorePortraitAfterFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(isFullscreen, tabletOrientationMode) {
        if (isFullscreen) {
            restorePortraitAfterFullscreen = false
        } else if (wasFullscreen && tabletOrientationMode == null) {
            restorePortraitAfterFullscreen = true
        } else if (tabletOrientationMode != null) {
            restorePortraitAfterFullscreen = false
        }
        wasFullscreen = isFullscreen
    }
    LaunchedEffect(restorePortraitAfterFullscreen, isLandscape) {
        if (restorePortraitAfterFullscreen && !isLandscape) {
            restorePortraitAfterFullscreen = false
        }
    }

    LaunchedEffect(
        isFullscreen,
        controlsLocked,
        restorePortraitAfterFullscreen,
        tabletOrientationMode
    ) {
        activity?.let {
            applyLivePlaybackHostWindow(
                activity = it,
                fullscreen = isFullscreen,
                controlsLocked = controlsLocked,
                restorePortrait = restorePortraitAfterFullscreen,
                tabletOrientationMode = tabletOrientationMode
            )
        }
    }

    DisposableEffect(activity, liveRoomView, tabletOrientationMode) {
        liveRoomView.keepScreenOn = true
        keepLivePlaybackHostAwake(activity, true)
        onDispose {
            liveRoomView.keepScreenOn = false
            keepLivePlaybackHostAwake(activity, false)
            activity?.let {
                restoreLivePlaybackHostWindow(
                    activity = it,
                    tabletOrientationMode = tabletOrientationMode
                )
            }
        }
    }

    // Remembered on its identity-defining inputs: the room UI holds on to this object (the back
    // handler captures it), so returning a fresh instance on every recomposition would defeat
    // lambda memoisation for everything downstream of it.
    return remember(
        playerController,
        isInPictureInPicture,
        isFullscreen,
        playbackSession,
        activity,
        tabletOrientationMode
    ) {
        LivePlaybackHost(
            playerController = playerController,
            isInPictureInPicture = isInPictureInPicture,
            isFullscreen = isFullscreen,
            playbackSession = playbackSession,
            activity = activity,
            tabletOrientationMode = tabletOrientationMode
        )
    }
}

private fun LivePlaybackSession.pauseForHost(
    content: LivePlaybackHostContent,
    preferences: LiveRoomPreferences,
    inPictureInPicture: Boolean
) {
    onHostPause(
        allowBackgroundPlayback = preferences.allowBackgroundPlayback,
        playerAutoPause = preferences.playerAutoPause,
        roomTitle = content.title,
        anchorName = content.anchorName,
        platform = content.platform,
        inPictureInPicture = inPictureInPicture
    )
}

internal fun shouldResumeLivePlaybackOnForeground(
    lifecyclePausedPlayback: Boolean,
    wasPlayingBeforePause: Boolean
): Boolean = lifecyclePausedPlayback && wasPlayingBeforePause

internal fun livePlaybackHostRequestedOrientation(
    fullscreen: Boolean,
    controlsLocked: Boolean,
    restorePortrait: Boolean = false,
    tabletOrientationMode: TabletOrientationMode? = null
): Int {
    return when {
        controlsLocked -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
        fullscreen -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        tabletOrientationMode != null -> tabletRequestedOrientation(tabletOrientationMode)
        restorePortrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

internal fun shouldApplyLivePlaybackAutoFullscreen(
    autoFullScreen: Boolean,
    tabletOrientationMode: TabletOrientationMode?
): Boolean {
    return autoFullScreen && tabletOrientationMode != TabletOrientationMode.Portrait
}

private fun applyLivePlaybackHostWindow(
    activity: Activity,
    fullscreen: Boolean,
    controlsLocked: Boolean,
    restorePortrait: Boolean,
    tabletOrientationMode: TabletOrientationMode?
) {
    activity.requestedOrientation = livePlaybackHostRequestedOrientation(
        fullscreen = fullscreen,
        controlsLocked = controlsLocked,
        restorePortrait = restorePortrait,
        tabletOrientationMode = tabletOrientationMode
    )
    if (fullscreen) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }
}

private fun prepareLivePlaybackHostExit(
    activity: Activity,
    tabletOrientationMode: TabletOrientationMode?
) {
    if (tabletOrientationMode != null) {
        activity.requestedOrientation = tabletRequestedOrientation(tabletOrientationMode)
    } else if (
        activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    ) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    WindowCompat.setDecorFitsSystemWindows(activity.window, true)
    WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        .show(WindowInsetsCompat.Type.systemBars())
}

private fun restoreLivePlaybackHostWindow(
    activity: Activity,
    tabletOrientationMode: TabletOrientationMode?
) {
    activity.requestedOrientation = tabletOrientationMode?.let(::tabletRequestedOrientation)
        ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    WindowCompat.setDecorFitsSystemWindows(activity.window, true)
    WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        .show(WindowInsetsCompat.Type.systemBars())
}

private fun keepLivePlaybackHostAwake(activity: Activity?, keepAwake: Boolean) {
    val window = activity?.window ?: return
    if (keepAwake) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
