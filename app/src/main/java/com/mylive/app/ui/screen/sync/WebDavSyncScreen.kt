package com.mylive.app.ui.screen.sync

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import com.mylive.app.core.common.readUtf8TextWithinLimit
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import com.mylive.app.data.repository.ProfileBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class WebDavUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val lastUploadTime: String = "",
    val lastRecoverTime: String = "",
    val isLoading: Boolean = false,
    val showPassword: Boolean = false
)

sealed class WebDavMessage {
    object Saved : WebDavMessage()
    object BackupOk : WebDavMessage()
    data class BackupFailed(val reason: String) : WebDavMessage()
    object RestoreOk : WebDavMessage()
    data class RestoreFailed(val reason: String) : WebDavMessage()
    object ConfigRequired : WebDavMessage()
    data class ConfigInvalid(val reason: String) : WebDavMessage()
}

@HiltViewModel
class WebDavViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val sensitiveCredentialStore: SensitiveCredentialStore,
    private val profileBackupManager: ProfileBackupManager,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(WebDavUiState())
    val uiState: StateFlow<WebDavUiState> = _uiState.asStateFlow()

    private val _message = MutableSharedFlow<WebDavMessage?>(replay = 0, extraBufferCapacity = 1)
    val message: SharedFlow<WebDavMessage?> = _message.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.getFlow(SettingsDataStore.WebDAVUri, "").collect { url ->
                _uiState.value = _uiState.value.copy(serverUrl = url)
            }
        }
        viewModelScope.launch {
            settingsDataStore.getFlow(SettingsDataStore.WebDAVUser, "").collect { user ->
                _uiState.value = _uiState.value.copy(username = user)
            }
        }
        viewModelScope.launch {
            sensitiveCredentialStore.webDavPassword.collect { pass ->
                _uiState.value = _uiState.value.copy(password = pass)
            }
        }
        viewModelScope.launch {
            settingsDataStore.getFlow(SettingsDataStore.kWebDAVLastUploadTime, "").collect { time ->
                _uiState.value = _uiState.value.copy(lastUploadTime = time)
            }
        }
        viewModelScope.launch {
            settingsDataStore.getFlow(SettingsDataStore.kWebDAVLastRecoverTime, "").collect { time ->
                _uiState.value = _uiState.value.copy(lastRecoverTime = time)
            }
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url)
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(showPassword = !_uiState.value.showPassword)
    }

    fun saveConfig() {
        viewModelScope.launch {
            try {
                buildWebDavBackupUrl(_uiState.value.serverUrl)
                persistConfig(_uiState.value)
                _message.emit(WebDavMessage.Saved)
            } catch (e: IllegalArgumentException) {
                _message.emit(WebDavMessage.ConfigInvalid(e.message ?: "Invalid WebDAV URL"))
            }
        }
    }

    fun backup() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.serverUrl.isBlank()) {
                _message.emit(WebDavMessage.ConfigRequired)
                return@launch
            }
            _uiState.value = state.copy(isLoading = true)
            try {
                val backupUrl = buildWebDavBackupUrl(state.serverUrl)
                persistConfig(state)
                val uploadTime = withContext(Dispatchers.IO) {
                    val body = profileBackupManager.exportProfileJson()
                        .toRequestBody(JSON_MEDIA_TYPE)
                    val request = Request.Builder()
                        .url(backupUrl)
                        .put(body)
                        .applyAuth(state.username, state.password)
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code}: ${response.message}")
                        }
                    }
                    timestamp()
                }
                settingsDataStore.setValue(SettingsDataStore.kWebDAVLastUploadTime, uploadTime)
                _uiState.value = _uiState.value.copy(isLoading = false)
                _message.emit(WebDavMessage.BackupOk)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _message.emit(WebDavMessage.BackupFailed(e.message ?: ""))
            }
        }
    }

    fun restore() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.serverUrl.isBlank()) {
                _message.emit(WebDavMessage.ConfigRequired)
                return@launch
            }
            _uiState.value = state.copy(isLoading = true)
            try {
                val backupUrl = buildWebDavBackupUrl(state.serverUrl)
                val recoverTime = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(backupUrl)
                        .get()
                        .applyAuth(state.username, state.password)
                        .build()
                    val body = okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code}: ${response.message}")
                        }
                        response.body?.byteStream()?.use { input ->
                            input.readUtf8TextWithinLimit()
                        } ?: throw IOException("empty response")
                    }
                    profileBackupManager.importProfileJson(body)
                    timestamp()
                }
                settingsDataStore.setValue(SettingsDataStore.kWebDAVLastRecoverTime, recoverTime)
                _uiState.value = _uiState.value.copy(isLoading = false)
                _message.emit(WebDavMessage.RestoreOk)
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _message.emit(WebDavMessage.RestoreFailed("无效的备份文件"))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _message.emit(WebDavMessage.RestoreFailed(e.message ?: ""))
            }
        }
    }

    private suspend fun persistConfig(state: WebDavUiState) {
        settingsDataStore.setValue(SettingsDataStore.WebDAVUri, state.serverUrl.trim())
        settingsDataStore.setValue(SettingsDataStore.WebDAVUser, state.username)
        sensitiveCredentialStore.setWebDavPassword(state.password)
    }

    private fun Request.Builder.applyAuth(username: String, password: String): Request.Builder {
        if (username.isNotBlank()) {
            header("Authorization", Credentials.basic(username, password))
        }
        return this
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavSyncScreen(
    navigator: Navigator,
    viewModel: WebDavViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var isExiting by remember { mutableStateOf(false) }
    val handleBack: () -> Unit = {
        if (!isExiting) {
            isExiting = true
            navigator.goBack()
        }
    }
    BackHandler(enabled = !isExiting) {
        handleBack()
    }

    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            msg ?: return@collect
            val text = when (msg) {
                WebDavMessage.Saved -> context.getString(R.string.webdav_saved)
                WebDavMessage.BackupOk -> context.getString(R.string.webdav_backup_ok)
                is WebDavMessage.BackupFailed -> context.getString(R.string.webdav_backup_failed, msg.reason)
                WebDavMessage.RestoreOk -> context.getString(R.string.webdav_restore_ok)
                is WebDavMessage.RestoreFailed -> context.getString(R.string.webdav_restore_failed, msg.reason)
                WebDavMessage.ConfigRequired -> context.getString(R.string.webdav_config_required)
                is WebDavMessage.ConfigInvalid -> context.getString(R.string.webdav_config_invalid, msg.reason)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.webdav_title)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Server URL
            OutlinedTextField(
                value = uiState.serverUrl,
                onValueChange = { viewModel.updateServerUrl(it) },
                label = { Text(stringResource(R.string.webdav_server_url)) },
                placeholder = { Text(stringResource(R.string.webdav_server_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isLoading
            )

            // Username
            OutlinedTextField(
                value = uiState.username,
                onValueChange = { viewModel.updateUsername(it) },
                label = { Text(stringResource(R.string.webdav_username)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isLoading
            )

            // Password
            OutlinedTextField(
                value = uiState.password,
                onValueChange = { viewModel.updatePassword(it) },
                label = { Text(stringResource(R.string.webdav_password)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isLoading,
                visualTransformation = if (uiState.showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { viewModel.togglePasswordVisibility() }) {
                        Text(
                            text = if (uiState.showPassword) stringResource(R.string.webdav_hide)
                            else stringResource(R.string.webdav_show),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            )

            // Save config button
            Button(
                onClick = { viewModel.saveConfig() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                Text(stringResource(R.string.webdav_save))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Last times
            if (uiState.lastUploadTime.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.webdav_last_backup, uiState.lastUploadTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (uiState.lastRecoverTime.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.webdav_last_recover, uiState.lastRecoverTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Backup button
            OutlinedButton(
                onClick = { viewModel.backup() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && uiState.serverUrl.isNotBlank()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.webdav_backup))
            }

            // Restore button
            OutlinedButton(
                onClick = { viewModel.restore() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && uiState.serverUrl.isNotBlank()
            ) {
                Text(stringResource(R.string.webdav_restore))
            }
        }
    }
}
