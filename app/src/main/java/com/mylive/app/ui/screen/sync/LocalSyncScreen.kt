package com.mylive.app.ui.screen.sync

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import com.mylive.app.core.sync.LanSyncProtocol
import com.mylive.app.service.LanSyncService
import com.mylive.app.service.SyncClient
import com.mylive.app.service.shouldKeepLanSyncServerRunning
import com.mylive.app.ui.component.settings.SettingsInsetCard
import com.mylive.app.ui.component.settings.SettingsSectionHeader
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSyncScreen(
    navigator: Navigator
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clients = LanSyncService.scanClients
    var manualAddress by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var showMyInfoDialog by remember { mutableStateOf(false) }

    val handleBack = navigator.rememberRouteActions().onBack

    DisposableEffect(Unit) {
        LanSyncService.start(context)
        clients.clear()
        LanSyncService.sendHello()
        onDispose {
            // Leaving the sync flow closes the listening ports and drops the pairing code.
            // Descending into the scan/device screens does not: they are part of the same flow
            // and return here, and restarting in between would invalidate a code a peer has
            // already scanned.
            if (!shouldKeepLanSyncServerRunning(navigator.backStack)) {
                LanSyncService.stop(context)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("局域网同步") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        clients.clear()
                        LanSyncService.sendHello()
                        Toast.makeText(context, "正在刷新设备列表", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }

                    IconButton(onClick = { showMyInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "本机信息")
                    }
                }
            )
        }
    ) { padding ->
        AdaptivePageFrame(Modifier.padding(padding), LocalAppLayout.current.settingsMaxWidthDp) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    item {
                        SettingsSectionHeader("手动连接")
                        SettingsInsetCard {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = manualAddress,
                                    onValueChange = { manualAddress = it },
                                    label = { Text("输入对方设备的 IP 地址或完整同步地址") },
                                    placeholder = { Text("例如: 192.168.1.100") },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            navigator.navigate(Route.SyncScan)
                                        }) {
                                            Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码连接")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = {
                                        val cleaned = manualAddress.trim()
                                        if (cleaned.isEmpty()) {
                                            Toast.makeText(context, "请输入地址", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        connectToManualAddress(
                                            rawAddress = cleaned,
                                            coroutineScope = coroutineScope,
                                            navigator = navigator,
                                            context = context
                                        ) {
                                            isConnecting = it
                                        }
                                    },
                                    enabled = !isConnecting,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("连接设备")
                                }
                            }
                        }
                    }

                    item {
                        SettingsSectionHeader(
                            title = "发现设备",
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    if (clients.isEmpty()) {
                        item {
                            SettingsInsetCard {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "正在搜索同一 Wi-Fi/局域网内的设备...\n请确保对方设备已打开局域网同步界面",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(clients, key = { "${it.address}:${it.port}" }) { client ->
                            ClientItem(
                                client = client,
                                onClick = {
                                    navigator.navigate(
                                        Route.SyncDevice(
                                            address = client.address,
                                            port = client.port.toString(),
                                            name = client.name
                                        )
                                    )
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                if (isConnecting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("正在尝试建立连接...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMyInfoDialog) {
        val addresses = LanSyncService.ipAddress
        MyInfoDialog(
            addresses = addresses,
            onDismiss = { showMyInfoDialog = false }
        )
    }
}

@Composable
fun ClientItem(client: SyncClient, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (client.type.lowercase()) {
                "android" -> Icons.Default.Android
                "windows" -> Icons.Default.Laptop
                "macos" -> Icons.Default.Laptop
                "ios" -> Icons.Default.Phone
                "tv" -> Icons.Default.Tv
                else -> Icons.Default.Devices
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "IP: ${client.address}:${client.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun MyInfoDialog(addresses: String, onDismiss: () -> Unit) {
    val cleanAddresses = addresses.split(";").filter { it.isNotBlank() }
    val displayStr = cleanAddresses.joinToString("\n") { "$it:${LanSyncService.HTTP_PORT}" }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "本机同步信息",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (cleanAddresses.isNotEmpty()) {
                    // Encode host + port + pairing token so a scanning device is paired in one step.
                    val qrData = "mylive-sync://${cleanAddresses.first()}:${LanSyncService.HTTP_PORT}?token=${LanSyncService.syncToken}"
                    val bitmap = remember(qrData) { generateQrCode(qrData) }
                    if (bitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier
                                    .size(184.dp)
                                    .padding(10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (cleanAddresses.isEmpty()) "未获取到局域网 IP，请确认连接了 Wi-Fi/局域网" else "HTTP 服务已启动，同步地址：",
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center
                )

                if (cleanAddresses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = displayStr,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                if (LanSyncService.syncToken.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "配对码：${LanSyncService.syncToken}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "请在另一台设备的“局域网同步”界面扫描上方二维码即可自动配对并同步；若手动连接，请在对方“设备同步”页填入上方配对码。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("确定")
                }
            }
        }
    }
}

private fun connectToManualAddress(
    rawAddress: String,
    coroutineScope: CoroutineScope,
    navigator: Navigator,
    context: android.content.Context,
    setLoading: (Boolean) -> Unit
) {
    var address = rawAddress.trim()
    var parsedToken = ""
    if (address.startsWith("mylive-sync://", ignoreCase = true)) {
        parsedToken = runCatching { Uri.parse(address).getQueryParameter("token").orEmpty() }.getOrDefault("")
        address = "http://" + address.substringAfter("://")
    } else if (!address.startsWith("http://", ignoreCase = true) && !address.startsWith("https://", ignoreCase = true)) {
        address = "http://$address"
    }

    if (parsedToken.isEmpty()) {
        parsedToken = runCatching { Uri.parse(address).getQueryParameter("token").orEmpty() }.getOrDefault("")
    }

    val uri = try {
        URI(address)
    } catch (e: Exception) {
        null
    }

    if (uri == null || uri.host.isNullOrBlank()) {
        setLoading(false)
        Toast.makeText(context, "无效的地址格式", Toast.LENGTH_SHORT).show()
        return
    }

    val host = uri.host
    val port = if (uri.port != -1) uri.port else LanSyncService.HTTP_PORT
    val request = Request.Builder()
        .url("http://$host:$port${LanSyncProtocol.INFO_PATH}")
        .build()

    setLoading(true)
    coroutineScope.launch {
        try {
            val peerName = withContext(Dispatchers.IO) {
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        val body = response.body?.string().orEmpty()
                        val peerInfo = LanSyncProtocol.decodePeerInfo(body)
                        peerInfo.name ?: "Android Device"
                    }
                } finally {
                    client.dispatcher.executorService.shutdown()
                    client.connectionPool.evictAll()
                }
            }
            setLoading(false)
            navigator.navigate(
                Route.SyncDevice(
                    address = host,
                    port = port.toString(),
                    name = peerName,
                    token = parsedToken
                )
            )
        } catch (cancelled: CancellationException) {
            setLoading(false)
            throw cancelled
        } catch (e: Exception) {
            Timber.e(e, "LocalSyncScreen: Manual connect failed")
            setLoading(false)
            Toast.makeText(context, "连接设备失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
