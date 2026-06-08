package com.mylive.app.ui.screen.sync

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import com.mylive.app.data.repository.ProfileBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class ProfileMessage {
    object ExportOk : ProfileMessage()
    data class ExportFailed(val reason: String) : ProfileMessage()
    object ImportOk : ProfileMessage()
    data class ImportFailed(val reason: String) : ProfileMessage()
    object InvalidJson : ProfileMessage()
}

data class ProfileBackupUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val message: ProfileMessage? = null
)

@HiltViewModel
class ProfileBackupViewModel @Inject constructor(
    private val profileBackupManager: ProfileBackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileBackupUiState())
    val uiState: StateFlow<ProfileBackupUiState> = _uiState.asStateFlow()

    fun exportProfile(onResult: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            try {
                onResult(profileBackupManager.exportProfileJson())
                _uiState.value = _uiState.value.copy(isExporting = false, message = ProfileMessage.ExportOk)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isExporting = false, message = ProfileMessage.ExportFailed(e.message ?: ""))
            }
        }
    }

    fun importProfile(jsonString: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            try {
                profileBackupManager.importProfileJson(jsonString)
                _uiState.value = _uiState.value.copy(isImporting = false, message = ProfileMessage.ImportOk)
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(isImporting = false, message = ProfileMessage.InvalidJson)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isImporting = false, message = ProfileMessage.ImportFailed(e.message ?: ""))
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBackupScreen(
    navigator: Navigator,
    viewModel: ProfileBackupViewModel = hiltViewModel()
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

    LaunchedEffect(uiState.message) {
        val msg = uiState.message ?: return@LaunchedEffect
        val text = when (msg) {
            is ProfileMessage.ExportOk -> context.getString(R.string.profile_export_ok)
            is ProfileMessage.ExportFailed -> context.getString(R.string.profile_export_failed, msg.reason)
            is ProfileMessage.ImportOk -> context.getString(R.string.profile_import_ok)
            is ProfileMessage.ImportFailed -> context.getString(R.string.profile_import_failed, msg.reason)
            is ProfileMessage.InvalidJson -> context.getString(R.string.profile_invalid_json)
        }
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }

    // Export launcher - save to file
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportProfile { jsonString ->
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
            }
        }
    }

    // Import launcher - read from file
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val jsonString = context.contentResolver.openInputStream(it)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            }
            jsonString?.let { json -> viewModel.importProfile(json) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.profile_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.profile_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Export button
            Button(
                onClick = {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    exportLauncher.launch("mylive_config_$timestamp.json")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isExporting && !uiState.isImporting
            ) {
                if (uiState.isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.profile_export))
            }

            // Import button
            OutlinedButton(
                onClick = { importLauncher.launch("application/json") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isExporting && !uiState.isImporting
            ) {
                if (uiState.isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.profile_import))
            }
        }
    }
}
