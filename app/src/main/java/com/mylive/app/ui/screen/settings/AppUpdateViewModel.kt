package com.mylive.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.update.AppUpdateInfo
import com.mylive.app.update.AppUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class AppUpdateUiState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Int = 0,
    val updateInfo: AppUpdateInfo? = null,
    val downloadedFile: File? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdate() {
        if (_uiState.value.checking) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    checking = true,
                    message = null,
                    error = null,
                    downloadedFile = null
                )
            }
            runCatching { repository.checkLatestUpdate() }
                .onSuccess { update ->
                    _uiState.update {
                        it.copy(
                            checking = false,
                            updateInfo = update,
                            message = if (update == null) "当前已是最新的 v1.x 稳定版" else null,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            checking = false,
                            error = error.message ?: "检查更新失败"
                        )
                    }
                }
        }
    }

    fun downloadUpdate() {
        val update = _uiState.value.updateInfo ?: return
        if (_uiState.value.downloading) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    downloading = true,
                    downloadProgress = 0,
                    downloadedFile = null,
                    message = null,
                    error = null
                )
            }
            runCatching {
                repository.downloadApk(update) { progress ->
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
            }.onSuccess { file ->
                _uiState.update {
                    it.copy(
                        downloading = false,
                        downloadProgress = 100,
                        downloadedFile = file,
                        message = "下载完成，准备安装"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        downloading = false,
                        error = error.message ?: "下载更新失败"
                    )
                }
            }
        }
    }
}
