package com.mylive.app.ui.screen.sync

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mylive.app.R
import com.mylive.app.service.LanSyncService
import com.mylive.app.service.RemoteSyncService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScanScreen(navigator: Navigator) {
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

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        val content = result.contents ?: return@rememberLauncherForActivityResult
        handleScanResult(content, navigator, context)
    }

    // Launch scanner immediately on screen enter
    LaunchedEffect(Unit) {
        scanLauncher.launch(createSyncScanOptions())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫码同步") },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        // Show instructions while waiting for scan result
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "正在等待扫码结果...\n\n如果相机未自动打开，请点击下方按钮重新扫描",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = {
                    scanLauncher.launch(createSyncScanOptions())
                }) {
                    Text("重新扫描")
                }
            }
        }
    }
}

private fun createSyncScanOptions(): ScanOptions {
    return ScanOptions().apply {
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        setPrompt("将二维码放入框内扫描")
        setCameraId(0)
        setBeepEnabled(false)
        setOrientationLocked(true)
        setCaptureActivity(PortraitCaptureActivity::class.java)
    }
}

private fun handleScanResult(
    content: String,
    navigator: Navigator,
    context: android.content.Context
) {
    val trimmed = content.trim()

    // LAN pairing QR: mylive-sync://<host>:<port>?token=<token> — carries the pairing token.
    if (trimmed.startsWith("mylive-sync://")) {
        val uri = android.net.Uri.parse(trimmed)
        val host = uri.host
        if (host.isNullOrBlank()) {
            Toast.makeText(context, "无效的配对二维码", Toast.LENGTH_LONG).show()
            return
        }
        val port = if (uri.port != -1) uri.port else LanSyncService.HTTP_PORT
        val token = uri.getQueryParameter("token") ?: ""
        navigator.navigate(
            Route.SyncDevice(
                address = host,
                port = port.toString(),
                name = "Scanned Device",
                token = token
            )
        )
        return
    }

    // Check if it's a remote sync room ID (fixed length). Must match the length the room
    // QR is generated with (RemoteSyncService.K_ROOM_ID_LENGTH = 6); hard-coding 8 here made
    // every scanned room code fall through and be (mis)treated as a device address.
    if (trimmed.length == RemoteSyncService.K_ROOM_ID_LENGTH && trimmed.all { it.isLetterOrDigit() }) {
        navigator.navigate(Route.RemoteSyncRoom(roomId = trimmed.uppercase()))
        return
    }

    // Check for multi-address (separated by ";")
    if (trimmed.contains(";")) {
        val addresses = trimmed.split(";").filter { it.isNotBlank() }
        if (addresses.size == 1) {
            navigateToDevice(addresses[0], navigator, context)
        } else {
            navigateToDevice(addresses[0], navigator, context)
            Toast.makeText(context, "检测到多个地址，已使用第一个", Toast.LENGTH_SHORT).show()
        }
        return
    }

    // Single address
    navigateToDevice(trimmed, navigator, context)
}

private fun navigateToDevice(
    address: String,
    navigator: Navigator,
    context: android.content.Context
) {
    var addr = address.trim()
    if (!addr.startsWith("http://") && !addr.startsWith("https://")) {
        addr = "http://$addr"
    }

    val uri = try {
        java.net.URI(addr)
    } catch (e: Exception) {
        null
    }

    if (uri == null || uri.host.isNullOrBlank()) {
        Toast.makeText(context, "无效的地址格式: $address", Toast.LENGTH_LONG).show()
        return
    }

    val host = uri.host
    val port = if (uri.port != -1) uri.port else LanSyncService.HTTP_PORT

    navigator.navigate(
        Route.SyncDevice(address = host, port = port.toString(), name = "Scanned Device")
    )
}
