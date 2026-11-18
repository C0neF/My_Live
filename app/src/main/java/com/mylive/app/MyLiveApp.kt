package com.mylive.app

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.RuntimeLogTree
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.ui.emoji.EmojiAtlasRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MyLiveApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        Timber.plant(RuntimeLogTree(logToLogcat = BuildConfig.DEBUG))
        CoreLog.platformLogger = { level, message ->
            when (level) {
                CoreLog.LogLevel.DEBUG -> Timber.d(message)
                CoreLog.LogLevel.INFO -> Timber.i(message)
                CoreLog.LogLevel.WARNING -> Timber.w(message)
                CoreLog.LogLevel.ERROR -> Timber.e(message)
            }
        }
        applicationScope.launch {
            combine(
                settingsRepository.logEnable,
                settingsRepository.debugMode
            ) { enabled, debugEnabled ->
                enabled to debugEnabled
            }.collect { (enabled, debugEnabled) ->
                CoreLog.configure(
                    enabled = enabled,
                    debugEnabled = debugEnabled
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            EmojiAtlasRepository.trimMemory()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        EmojiAtlasRepository.trimMemory()
    }
}
