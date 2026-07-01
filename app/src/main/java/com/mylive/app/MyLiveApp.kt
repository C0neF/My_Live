package com.mylive.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.RuntimeLogTree
import com.mylive.app.data.repository.SettingsRepository
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
}
