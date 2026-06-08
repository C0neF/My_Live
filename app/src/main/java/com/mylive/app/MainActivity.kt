package com.mylive.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mylive.app.core.common.CoreLog
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.ui.navigation.AppNavGraph
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.theme.MyLiveTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
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
        val initialRoute = intent.getStringExtra(EXTRA_INITIAL_ROUTE)

        // Sync log settings
        lifecycleScope.launch {
            settingsRepository.logEnable.collect { enabled ->
                CoreLog.enableLog = enabled
            }
        }

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
            val themeMode by settingsRepository.themeMode.collectAsState(initial = 0)
            val isDynamic by settingsRepository.isDynamic.collectAsState(initial = false)
            val styleColor by settingsRepository.styleColor.collectAsState(initial = 0xff3498db.toInt())

            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyLiveTheme(
                darkTheme = darkTheme,
                dynamicColor = isDynamic,
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPipSupportedAndActive) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val params = android.app.PictureInPictureParams.Builder().build()
                    enterPictureInPictureMode(params)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    companion object {
        const val EXTRA_INITIAL_ROUTE = "com.mylive.app.extra.INITIAL_ROUTE"
        var isPipSupportedAndActive = false
    }
}
