package com.mylive.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    SettingsSectionCard("端到端加密同步房间") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "通过端到端加密同步房间传递关注、分组、历史和屏蔽词。关闭同步窗口会断开房间。",
                fontSize = 12.sp,
                color = UiMuted
            )
            DesktopTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text("WSS 同步服务地址") },
                singleLine = true,
                enabled = !state.busy && state.roomId.isEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
            if (state.roomId.isEmpty()) {
                DesktopTextField(
                    value = room,
                    onValueChange = { room = it.take(128) },
                    label = { Text("对方房间配对码") },
                    placeholder = { Text("如 ABC123-K7M9P-2X4Q8") },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesktopPrimaryButton(enabled = !state.busy, onClick = { controller.connect(endpoint.trim(), null) }) {
                        Text("创建房间")
                    }
                    DesktopOutlinedButton(enabled = !state.busy && validInvite, onClick = { controller.connect(endpoint.trim(), room) }) {
                        Text("加入房间")
                    }
                }
            } else {
                SelectionContainer {
                    Text("配对码：${state.inviteCode}", style = MaterialTheme.typography.titleMedium)
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DesktopQrCard(state.qrPayload, "远程同步房间配对二维码", size = 148.dp)
                }
                Text("在线设备（${state.users.size} 台）", fontSize = 12.sp, color = UiMuted)
                state.users.forEach {
                    Text("${it.app} · ${it.platform} ${it.version}${if (it.isSelf) "（本机）" else ""}", fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesktopPrimaryButton(enabled = !state.busy && library.writable, onClick = { controller.send(library) }) {
                        Text("发送本机资料")
                    }
                    DesktopOutlinedButton(
                        onClick = {
                            runCatching {
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(state.inviteCode), null)
                            }
                        }
                    ) { Text("复制配对码") }
                    DesktopTextButton(onClick = controller::disconnect) { Text("断开房间") }
                }
            }
            DesktopThinProgressBar(visible = state.busy)
            state.pendingPrompt?.let { prompt ->
                Surface(
                    color = UiSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(DesktopHairlineWidth, UiAccent.copy(alpha = .4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(prompt, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DesktopPrimaryButton(onClick = controller::confirmPending) { Text("确认应用") }
                            DesktopOutlinedButton(onClick = controller::dismissPending) { Text("忽略") }
                        }
                    }
                }
            }
            state.message?.let { Text(it, fontSize = 12.sp, color = UiMuted) }
        }
    }
}
