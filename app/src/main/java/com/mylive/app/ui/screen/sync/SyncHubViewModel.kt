package com.mylive.app.ui.screen.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.service.RemoteSyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncServerHealthUiState(
    val endpointName: String = "默认同步服务",
    val statusText: String = "等待检测",
    val isProbing: Boolean = false,
    val isReachable: Boolean? = null
)

@HiltViewModel
class SyncHubViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val remoteSyncService: RemoteSyncService
) : ViewModel() {
    private val _serverHealth = MutableStateFlow(SyncServerHealthUiState())
    val serverHealth: StateFlow<SyncServerHealthUiState> = _serverHealth.asStateFlow()
    private var probeJob: Job? = null

    init {
        refreshServerHealth()
    }

    fun refreshServerHealth() {
        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            val configuredUrl = settingsRepository.syncServerUrl.first()
            val configuredProxy = settingsRepository.syncProxyUrl.first()
            val endpointName = syncEndpointDisplayName(configuredUrl)
            _serverHealth.value = SyncServerHealthUiState(
                endpointName = endpointName,
                statusText = "检测中…",
                isProbing = true
            )
            try {
                val result = remoteSyncService.probeServer(configuredUrl, configuredProxy)
                _serverHealth.value = SyncServerHealthUiState(
                    endpointName = endpointName,
                    statusText = syncProbeStatusText(
                        isReachable = result.isReachable,
                        latencyMillis = result.latencyMillis,
                        error = result.message
                    ),
                    isReachable = result.isReachable
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _serverHealth.value = SyncServerHealthUiState(
                    endpointName = endpointName,
                    statusText = syncProbeStatusText(false, null, error.message.orEmpty()),
                    isReachable = false
                )
            }
        }
    }
}
