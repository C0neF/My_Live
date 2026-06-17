package com.mylive.app.ui.screen.sync

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import androidx.compose.foundation.border
import com.mylive.app.R
import com.mylive.app.service.RemoteRoomUser
import com.mylive.app.service.RemoteSyncConnectionState
import com.mylive.app.ui.component.settings.SettingsMenu
import com.mylive.app.ui.util.copyPlainText
import kotlinx.coroutines.flow.collectLatest

enum class SyncType {
    FOLLOW, HISTORY, SHIELD
}

enum class AccountSyncType {
    BILIBILI, DOUYIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSyncRoomScreen(
    navigator: Navigator,
    roomIdParam: String = "",
    viewModel: RemoteSyncRoomViewModel = hiltViewModel()
) {
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

    val connectionState by viewModel.connectionState.collectAsState()
    val roomId by viewModel.roomId.collectAsState()
    val roomUsers by viewModel.roomUsers.collectAsState()
    val countDown by viewModel.countDown.collectAsState()
    val loadingState by viewModel.loadingState.collectAsState()

    var showOverlayDialog by remember { mutableStateOf<SyncType?>(null) }
    var showAccountDialog by remember { mutableStateOf<AccountSyncType?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initRoom(roomIdParam)
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.roomDestroyedEvent.collectLatest {
            handleBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据同步") },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusColor = when (connectionState) {
                            RemoteSyncConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                            RemoteSyncConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondary
                            RemoteSyncConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error
                        }
                        val statusText = when (connectionState) {
                            RemoteSyncConnectionState.CONNECTED -> "已连接"
                            RemoteSyncConnectionState.CONNECTING -> "连接中"
                            RemoteSyncConnectionState.DISCONNECTED -> "未连接"
                        }

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium
                        )
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
                if (roomIdParam.isEmpty() && roomId.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "${countDown}秒后房间将会自动关闭",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "房间号",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = roomId.ifBlank { "正在生成..." },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )

                            if (roomId.isNotEmpty()) {
                                IconButton(onClick = {
                                    copyPlainText(context, "sync room id", roomId)
                                    Toast.makeText(context, "已复制房间号", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                                }

                                IconButton(onClick = { showQrDialog = true }) {
                                    Icon(Icons.Default.QrCode, contentDescription = "显示二维码")
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "同步数据至其他设备",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            SettingsMenu(
                                title = "发送关注列表",
                                subtitle = "同步关注主播及分组标签",
                                onClick = { showOverlayDialog = SyncType.FOLLOW }
                            )
                            HorizontalDivider()
                            SettingsMenu(
                                title = "发送观看记录",
                                subtitle = "同步最近观看的直播间历史",
                                onClick = { showOverlayDialog = SyncType.HISTORY }
                            )
                            HorizontalDivider()
                            SettingsMenu(
                                title = "发送弹幕屏蔽词",
                                subtitle = "同步本地屏蔽词及设置",
                                onClick = { showOverlayDialog = SyncType.SHIELD }
                            )
                            HorizontalDivider()
                            SettingsMenu(
                                title = "发送哔哩哔哩账号",
                                subtitle = "同步哔哩哔哩登录 Cookies",
                                onClick = { showAccountDialog = AccountSyncType.BILIBILI }
                            )
                            HorizontalDivider()
                            SettingsMenu(
                                title = "发送抖音账号",
                                subtitle = "同步抖音登录 Cookies",
                                onClick = { showAccountDialog = AccountSyncType.DOUYIN }
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "已连接设备 (${roomUsers.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }

                if (roomUsers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无设备连接\n可在另一台设备上加入此房间",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(roomUsers) { user ->
                        DeviceUserItem(user = user)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            if (loadingState != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = loadingState ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOverlayDialog != null) {
        val type = showOverlayDialog!!
        AlertDialog(
            onDismissRequest = { showOverlayDialog = null },
            title = { Text("数据覆盖") },
            text = { Text("是否覆盖对方设备上的同类数据？选择“不覆盖”会合并同步。") },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayDialog = null
                    when (type) {
                        SyncType.FOLLOW -> viewModel.syncFollow(true)
                        SyncType.HISTORY -> viewModel.syncHistory(true)
                        SyncType.SHIELD -> viewModel.syncBlockedWord(true)
                    }
                }) {
                    Text("覆盖")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverlayDialog = null
                    when (type) {
                        SyncType.FOLLOW -> viewModel.syncFollow(false)
                        SyncType.HISTORY -> viewModel.syncHistory(false)
                        SyncType.SHIELD -> viewModel.syncBlockedWord(false)
                    }
                }) {
                    Text("不覆盖")
                }
            }
        )
    }

    if (showAccountDialog != null) {
        val type = showAccountDialog!!
        val accountName = when (type) {
            AccountSyncType.BILIBILI -> "哔哩哔哩账号"
            AccountSyncType.DOUYIN -> "抖音账号"
        }
        AlertDialog(
            onDismissRequest = { showAccountDialog = null },
            title = { Text("发送账号") },
            text = { Text("确定要发送${accountName}登录 Cookies 到当前房间内的设备吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showAccountDialog = null
                    when (type) {
                        AccountSyncType.BILIBILI -> viewModel.syncBiliAccount()
                        AccountSyncType.DOUYIN -> viewModel.syncDouyinAccount()
                    }
                }) {
                    Text("发送")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showQrDialog && roomId.isNotEmpty()) {
        QrCodeDialog(roomId = roomId, onDismiss = { showQrDialog = false })
    }
}

@Composable
fun DeviceUserItem(user: RemoteRoomUser) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (user.platform.lowercase()) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = user.shortId,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (user.isCreator) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "创建者",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Text(
                    text = "${user.app} - v${user.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (user.isSelf) {
                Text(
                    text = "本机",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun QrCodeDialog(roomId: String, onDismiss: () -> Unit) {
    val bitmap = remember(roomId) { generateQrCode(roomId) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "房间信息",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.White)
                            .padding(8.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = roomId,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "请使用其他My Live客户端扫描上方二维码\n建立连接后可选择需要同步的数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

private fun generateQrCode(content: String): Bitmap? {
    return try {
        val size = 512
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap
    } catch (e: Exception) {
        null
    }
}
