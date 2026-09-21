package com.mylive.app.service

import android.content.Context
import com.mylive.app.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-level owner of the periodic follow-update schedule.
 *
 * The activity only supplies the first-frame signal. This object owns the long-lived settings
 * observation, filters duplicate configurations, and prevents activity recreation from touching
 * WorkManager again.
 */
@Singleton
class FollowUpdateScheduleCoordinator internal constructor(
    private val enabledFlow: Flow<Boolean>,
    private val intervalMinutesFlow: Flow<Int>,
    private val scope: CoroutineScope,
    private val schedule: (enabled: Boolean, intervalMinutes: Int) -> Unit
) {
    private val started = AtomicBoolean(false)

    @Inject
    constructor(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ) : this(
        enabledFlow = settingsRepository.autoUpdateFollowEnable,
        intervalMinutesFlow = settingsRepository.autoUpdateFollowDuration,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        schedule = { enabled, intervalMinutes ->
            FollowUpdateScheduler.schedule(context, enabled, intervalMinutes)
        }
    )

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(enabledFlow, intervalMinutesFlow) { enabled, intervalMinutes ->
                enabled to intervalMinutes
            }
                .distinctUntilChanged()
                .collect { (enabled, intervalMinutes) ->
                    schedule(enabled, intervalMinutes)
                }
        }
    }
}
