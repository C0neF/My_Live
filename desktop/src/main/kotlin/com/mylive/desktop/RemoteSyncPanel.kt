package com.mylive.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.core.common.RemoteSyncCrypto
import com.mylive.app.service.RemoteSyncService
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
internal fun RemoteSyncPanel(controller: DesktopRemoteSync, library: DesktopLibraryState) {
    val state by controller.state.collectAsState()
    var endpoint by remember { mutableStateOf(RemoteSyncService.K_DEFAULT_URL) }
    var room by remember { mutableStateOf("") }
    val validInvite = remember(room) { RemoteSyncCrypto.parseInvite(room) != null }
    Text("通过端到端加密同步房间传递关注、分组、历史和屏蔽词。关闭同步窗口会断开房间。", fontSize = 12.sp, color = UiMuted)
    OutlinedTextField(endpoint, { endpoint = it }, label = { Text("WSS 同步服务地址") }, singleLine = true,
        enabled = !state.busy && state.roomId.isEmpty(), modifier = Modifier.fillMaxWidth())
    if (state.roomId.isEmpty()) {
        OutlinedTextField(
            value = room,
            onValueChange = { room = it.take(128) },
            label = { Text("对方房间配对码") },
            placeholder = { Text("如 ABC123-K7M9P-2X4Q8") },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopPrimaryButton(enabled = !state.busy, onClick = { controller.connect(endpoint.trim(), null) }) { Text("创建房间") }
            DesktopOutlinedButton(enabled = !state.busy && validInvite, onClick = { controller.connect(endpoint.trim(), room) }) { Text("加入房间") }
        }
    } else {
        SelectionContainer {
            Text("配对码：${state.inviteCode}", style = MaterialTheme.typography.titleMedium)
        }
        DesktopQrImage(state.qrPayload, "远程同步房间配对二维码", Modifier.size(150.dp))
        Text("${state.users.size} 台设备", fontSize = 12.sp)
        state.users.forEach { Text("${it.app} · ${it.platform} ${it.version}${if (it.isSelf) "（本机）" else ""}", fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopPrimaryButton(enabled = !state.busy && library.writable, onClick = { controller.send(library) }) { Text("发送本机资料") }
            DesktopOutlinedButton(onClick = {
                runCatching {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(state.inviteCode), null)
                }
            }) { Text("复制配对码") }
            DesktopTextButton(onClick = controller::disconnect) { Text("断开房间") }
        }
    }
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.message?.let { Text(it, fontSize = 12.sp) }
}
