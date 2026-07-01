package com.mylive.app.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.BuildConfig
import com.mylive.app.R
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.theme.Icons
import com.mylive.app.update.AppUpdateInfo
import com.mylive.app.update.AppUpdateInstaller
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateScreen(
    navigator: Navigator,
    viewModel: AppUpdateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var launchedInstallFile by remember { mutableStateOf<File?>(null) }
    var isExiting by remember { mutableStateOf(false) }
    val updateMajorVersion = BuildConfig.VERSION_NAME.substringBefore('.')
    val updateChannelName = "v$updateMajorVersion 稳定版"

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
        viewModel.checkForUpdate()
    }

    val launchDownloadedUpdate: (File) -> Unit = { file ->
        if (launchedInstallFile != file) {
            runCatching {
                if (AppUpdateInstaller.canRequestPackageInstalls(context)) {
                    launchedInstallFile = file
                    context.startActivity(AppUpdateInstaller.createInstallIntent(context, file))
                } else {
                    Toast.makeText(
                        context,
                        "请允许 My Live 安装未知应用后再安装更新",
                        Toast.LENGTH_LONG
                    ).show()
                    context.startActivity(
                        AppUpdateInstaller.createInstallPermissionIntent(context)
                    )
                }
            }.onFailure {
                Toast.makeText(
                    context,
                    it.message ?: "无法打开安装器",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    LaunchedEffect(uiState.downloadedFile) {
        val file = uiState.downloadedFile ?: return@LaunchedEffect
        launchDownloadedUpdate(file)
    }

    DisposableEffect(lifecycleOwner, uiState.downloadedFile) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val file = uiState.downloadedFile
                    if (file != null && AppUpdateInstaller.canRequestPackageInstalls(context)) {
                        launchDownloadedUpdate(file)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_update)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "当前版本",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "My Live v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "更新通道：$updateChannelName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            val updateInfo = uiState.updateInfo
            when {
                uiState.checking -> {
                    Text(
                        text = "正在检查 GitHub $updateChannelName...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                updateInfo != null -> {
                    UpdateAvailableContent(
                        updateInfo = updateInfo,
                        downloading = uiState.downloading,
                        progress = uiState.downloadProgress,
                        onDownloadClick = viewModel::downloadUpdate
                    )
                }
                else -> {
                    Text(
                        text = uiState.message
                            ?: "点击按钮检查 GitHub 是否有新的 $updateChannelName。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = uiState.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = viewModel::checkForUpdate,
                    enabled = !uiState.checking && !uiState.downloading
                ) {
                    Text("重新检查")
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailableContent(
    updateInfo: AppUpdateInfo,
    downloading: Boolean,
    progress: Int,
    onDownloadClick: () -> Unit
) {
    Text(
        text = "发现新版本 v${updateInfo.versionName}",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = updateInfo.releaseName,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = "安装包：${updateInfo.apkName} · ${formatBytes(updateInfo.apkSizeBytes)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (updateInfo.releaseNotes.isNotBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = updateInfo.releaseNotes,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    if (downloading) {
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "正在下载 $progress%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Button(onClick = onDownloadClick) {
            Text("下载并安装")
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "未知大小"
    val mb = bytes / 1024.0 / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}
