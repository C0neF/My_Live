package com.mylive.app.ui.screen.sync

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import com.mylive.app.service.LanSyncService
import com.mylive.app.service.SyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSyncScreen(
    navigator: Navigator
) {
    val context = LocalContext.current
    val clients = LanSyncService.scanClients
    var manualAddress by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var showMyInfoDialog by remember { mutableStateOf(false) }

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
        LanSyncService.start(context)
        clients.clear()
        LanSyncService.sendHello()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("局域网同步") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "手动连接",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = manualAddress,
                                onValueChange = { manualAddress = it },
                                label = { Text("输入对方设备的 IP 地址或完整同步地址") },
                                placeholder = { Text("例如: 192.168.1.100") },
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
                                    connectToManualAddress(cleaned, navigator, context) {
                                        isConnecting = it
                                    }
                                },
                                enabled = !isConnecting,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("连接设备")
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "发现设备",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }

                if (clients.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(16.dp))
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
                    items(clients) { client ->
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
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            if (isConnecting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在尝试建立连接...")
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "IP: ${client.address}:${client.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun MyInfoDialog(addresses: String, onDismiss: () -> Unit) {
    val cleanAddresses = addresses.split(";").filter { it.isNotBlank() }
    val displayStr = cleanAddresses.joinToString("\n") { "$it:${LanSyncService.HTTP_PORT}" }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "本机同步信息",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (cleanAddresses.isNotEmpty()) {
                    // Encode host + port + pairing token so a scanning device is paired in one step.
                    val qrData = "mylive-sync://${cleanAddresses.first()}:${LanSyncService.HTTP_PORT}?token=${LanSyncService.syncToken}"
                    val bitmap = remember(qrData) { generateQrCode(qrData) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .size(180.dp)
                                .background(androidx.compose.ui.graphics.Color.White)
                                .padding(8.dp)
                        )
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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                if (LanSyncService.syncToken.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "配对码：${LanSyncService.syncToken}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "请在另一台设备的“局域网同步”界面扫描上方二维码即可自动配对并同步；若手动连接，请在对方“设备同步”页填入上方配对码。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
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
    navigator: Navigator,
    context: android.content.Context,
    setLoading: (Boolean) -> Unit
) {
    val okHttpClient = OkHttpClient()
    setLoading(true)

    // Parse address
    var address = rawAddress.trim()
    if (!address.startsWith("http://") && !address.startsWith("https://")) {
        address = "http://$address"
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

    // Make network request in background
    val request = Request.Builder()
        .url("http://$host:$port/info")
        .build()

    // Run async task
    val thread = Thread {
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val id = json.optString("id", "unknown")
                val name = json.optString("name", "Android Device")
                val platform = json.optString("type", "android")

                // Navigate back on main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    setLoading(false)
                    navigator.navigate(
                        Route.SyncDevice(address = host, port = port.toString(), name = name)
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "LocalSyncScreen: Manual connect failed")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                setLoading(false)
                Toast.makeText(context, "连接设备失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
    thread.start()
}
