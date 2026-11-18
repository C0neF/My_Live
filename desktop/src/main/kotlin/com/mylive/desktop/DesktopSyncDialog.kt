package com.mylive.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import javax.imageio.ImageIO
import kotlinx.coroutines.*

@Composable
fun DesktopSyncDialog(services: DesktopServices, state: DesktopLibraryState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var peer by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val addresses = remember { runCatching { desktopLanAddresses() }.getOrDefault(emptyList()) }
    var bindAddress by remember { mutableStateOf(addresses.firstOrNull().orEmpty()) }
    val lan by services.lanServer.state.collectAsState()
    var preview by remember { mutableStateOf<RoomProfileImport?>(null) }
    var confirmUpload by remember { mutableStateOf(false) }
    val remote = remember { DesktopRemoteSync(services.library, accounts = services.accounts) }
    DisposableEffect(remote) { onDispose { remote.close() } }
    fun run(messageOnSuccess: String, action: suspend () -> Unit) {
        scope.launch {
            busy = true; message = null; failure = null
            try {
                action()
                message = messageOnSuccess
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error.message?.take(160) ?: "操作失败，请检查配置与网络后重试。"
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(Unit) {
        busy = true
        try {
            val c = services.webDav.load()
            server = c.server; user = c.user; password = c.password
        } catch (_: Exception) {
            failure = "已保存的 WebDAV 配置读取失败，原文件已保留。"
        } finally {
            busy = false
        }
    }
    DesktopDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = "数据同步",
        preferredWidth = 640.dp,
        preferredHeight = 620.dp,
        content = {
            DesktopDialogTabBar(listOf("WebDAV", "局域网", "远程房间"), tab, enabled = !busy) { tab = it }
            DesktopThinProgressBar(visible = busy, modifier = Modifier.padding(top = 6.dp))
            AnimatedContent(
                targetState = tab,
                transitionSpec = { desktopDirectionalTabTransform(targetState.compareTo(initialState)) },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)
            ) { activeTab ->
                DesktopDialogScroll(Modifier.fillMaxSize()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (activeTab) {
                            0 -> {
                                SettingsSectionCard("WebDAV 服务器配置") {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        DesktopTextField(
                                            value = server,
                                            onValueChange = { server = it; preview = null },
                                            enabled = !busy,
                                            label = { Text("HTTPS 目录或 JSON 文件地址") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        DesktopTextField(
                                            value = user,
                                            onValueChange = { user = it },
                                            enabled = !busy,
                                            label = { Text("用户名") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        DesktopTextField(
                                            value = password,
                                            onValueChange = { password = it },
                                            enabled = !busy,
                                            label = { Text("应用密码") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            "与 Android 使用同一备份文件。配置由 Windows 保护，不会写入资料备份。",
                                            color = UiMuted,
                                            fontSize = 12.sp
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            DesktopOutlinedButton(
                                                enabled = !busy,
                                                onClick = { run("配置已保存") { services.webDav.save(DesktopWebDavConfig(server, user, password)) } }
                                            ) { Text("保存配置") }
                                            DesktopTextButton(
                                                enabled = !busy,
                                                onClick = { run("已清除保存的同步配置") { services.webDav.forget(); server = ""; user = ""; password = "" } }
                                            ) { Text("清除配置") }
                                        }
                                    }
                                }
                                SettingsSectionCard("云端备份与恢复") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DesktopPrimaryButton(enabled = !busy && state.writable, onClick = { confirmUpload = true }) {
                                            Text("上传备份")
                                        }
                                        DesktopOutlinedButton(
                                            enabled = !busy && state.writable,
                                            onClick = { run("备份已下载，请核对后恢复") { preview = services.webDav.preview(DesktopWebDavConfig(server, user, password)) } }
                                        ) { Text("下载并预览") }
                                    }
                                    var cachedPreview by remember { mutableStateOf(preview) }
                                    if (preview != null) cachedPreview = preview
                                    AnimatedVisibility(
                                        visible = preview != null,
                                        enter = desktopExpandEnter(),
                                        exit = desktopCollapseExit()
                                    ) {
                                        cachedPreview?.let { plan ->
                                            Column {
                                                SettingsFormDivider()
                                                Text(
                                                    "备份含 ${plan.follows.size} 个关注、${plan.history.size} 条历史${if (plan.preferences != null) "和设置" else ""}。恢复会合并房间并应用备份设置。",
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                                DesktopPrimaryButton(
                                                    enabled = !busy,
                                                    modifier = Modifier.padding(top = 8.dp),
                                                    onClick = {
                                                        run("资料已恢复") {
                                                            check(services.library.importRooms(plan)) { "保存恢复资料失败" }
                                                            preview = null
                                                        }
                                                    }
                                                ) { Text("确认恢复") }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> RemoteSyncPanel(remote, state)
                            else -> {
                                SettingsSectionCard("接收来自 Android 或其他 Windows 设备的资料") {
                                    AnimatedContent(
                                        targetState = lan.running,
                                        transitionSpec = { desktopDirectionalTabTransform(if (targetState) 1 else -1) }
                                    ) { running ->
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            if (!running) {
                                                if (addresses.isEmpty()) {
                                                    Text("暂未找到可用网络，请先连接局域网。", color = UiMuted)
                                                } else {
                                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        addresses.forEach { address ->
                                                            DesktopChoiceChip(
                                                                bindAddress == address,
                                                                onClick = { bindAddress = address },
                                                                label = { Text(address) },
                                                                enabled = !busy
                                                            )
                                                        }
                                                    }
                                                }
                                                DesktopPrimaryButton(
                                                    enabled = !busy && bindAddress.isNotBlank(),
                                                    onClick = { run("接收已开启") { withContext(Dispatchers.IO) { services.lanServer.start(bindAddress) } } }
                                                ) { Text("开启接收") }
                                            } else {
                                                Text("${lan.address}:${lan.port} · 已收到 ${lan.received} 次同步")
                                                val link = "mylive-sync://${lan.address}:${lan.port}?token=${services.lanServer.token}"
                                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                    DesktopQrCard(link, "局域网配对二维码", size = 168.dp)
                                                }
                                                DesktopTextField(
                                                    value = services.lanServer.token,
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    singleLine = true,
                                                    label = { Text("配对码") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                DesktopOutlinedButton(
                                                    enabled = !busy,
                                                    onClick = { run("接收已关闭") { withContext(Dispatchers.IO) { services.lanServer.close() } } }
                                                ) { Text("停止接收") }
                                            }
                                        }
                                    }
                                }
                                SettingsSectionCard("发送本机完整资料到对方") {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        DesktopTextField(
                                            value = peer,
                                            onValueChange = { peer = it },
                                            enabled = !busy,
                                            label = { Text("对方 IP:端口或 mylive-sync 配对链接") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        DesktopTextField(
                                            value = code,
                                            onValueChange = { code = it },
                                            enabled = !busy,
                                            label = { Text("对方配对码（链接已含配对码时可留空）") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        DesktopPrimaryButton(
                                            enabled = !busy && state.writable,
                                            onClick = {
                                                run("资料已发送并确认导入完成") {
                                                    val target = parseDesktopLanTarget(peer, code)
                                                    services.lanClient.send(
                                                        target.first.host,
                                                        target.first.port,
                                                        target.second,
                                                        RoomProfileCodec.export(state)
                                                    )
                                                }
                                            }
                                        ) { Text("发送并合并") }
                                    }
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = message != null,
                            enter = desktopExpandEnter(),
                            exit = desktopCollapseExit()
                        ) {
                            Text(message.orEmpty(), fontSize = 12.sp, color = UiAccent)
                        }
                        AnimatedVisibility(
                            visible = failure != null,
                            enter = desktopExpandEnter(),
                            exit = desktopCollapseExit()
                        ) {
                            Text(failure.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { DesktopOutlinedButton(enabled = !busy, onClick = onDismiss) { Text("关闭") } }
    )
    if (confirmUpload) {
        DesktopSheetModal(
            title = "更新服务器备份",
            onDismissRequest = { confirmUpload = false },
            preferredWidth = 420.dp,
            content = { Text("将用本机当前资料更新该地址的备份文件。", color = UiMuted) },
            confirmButton = {
                DesktopPrimaryButton(
                    onClick = {
                        confirmUpload = false
                        run("备份已上传") { services.webDav.backup(DesktopWebDavConfig(server, user, password), state) }
                    }
                ) { Text("上传") }
            },
            dismissButton = { DesktopTextButton(onClick = { confirmUpload = false }) { Text("取消") } }
        )
    }
}

internal fun parseDesktopLanTarget(text: String, code: String): Pair<URI, String> {
    val uri = URI(if (text.trim().startsWith("mylive-sync://")) text.trim() else "mylive-sync://${text.trim()}")
    require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.path.orEmpty().isEmpty()) { "对方地址格式不正确" }
    val token = uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("token=") }?.substringAfter('=')
        ?.let { URLDecoder.decode(it, Charsets.UTF_8) } ?: code.trim()
    val port = if (uri.port == -1) 23234 else uri.port
    require(port in 1..65535)
    return URI("http", null, uri.host, port, null, null, null) to token
}

@Composable
internal fun DesktopQrImage(text: String, description: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, text) {
        value = withContext(Dispatchers.Default) {
            val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300)
            val img = BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until 300) for (x in 0 until 300) img.setRGB(x, y, if (matrix[x, y]) 0 else 0xffffff)
            val bytes = ByteArrayOutputStream().use { ImageIO.write(img, "png", it); it.toByteArray() }
            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }
    }
    bitmap?.let { Image(it, description, modifier) }
}
