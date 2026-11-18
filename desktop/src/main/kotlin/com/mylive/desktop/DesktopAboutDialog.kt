package com.mylive.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.FileAlreadyExistsException
import javax.swing.JFileChooser
import kotlinx.coroutines.*

@Composable
fun DesktopAboutDialog(
    services: DesktopServices,
    room: DesktopRoomState,
    playback: MpvDesktopPlayback,
    owner: java.awt.Window,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<DesktopRelease?>(null) }
    val diagnostics by rememberDesktopPlaybackDiagnostics(true) { playback.diagnostics() }
    DesktopSheetModal(
        title = "关于 $DESKTOP_APP_NAME",
        onDismissRequest = { if (!busy) onDismiss() },
        preferredWidth = 520.dp,
        content = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painterResource("icons/mylive.png"),
                        DESKTOP_APP_NAME,
                        Modifier.size(52.dp).clip(RoundedCornerShape(13.dp))
                    )
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(DESKTOP_APP_NAME, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Surface(shape = RoundedCornerShape(999.dp), color = UiFieldFill) {
                                Text(
                                    "V$DESKTOP_VERSION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UiMuted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "聚合四平台直播、实时聊天与弹幕，支持与 Android 端无缝迁移同步。",
                            style = MaterialTheme.typography.bodySmall,
                            color = UiMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                SettingsSectionCard("播放状态与更新") {
                    Text(diagnostics.summary, fontSize = 12.sp, color = UiMuted)
                    SettingsFormDivider()
                    Row(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DesktopOutlinedButton(
                            enabled = !busy,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    try {
                                        release = DesktopUpdates(services.client).latest()
                                        message = if (release == null) "暂未发现已发布的 Windows 稳定版。" else null
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        message = "检查更新失败，可从项目发布页查看。"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        ) { Text("检查更新") }
                        DesktopTextButton(
                            onClick = {
                                runCatching { Desktop.getDesktop().browse(URI("$DESKTOP_PROJECT_URL/releases")) }
                                    .onFailure { message = "无法打开浏览器。" }
                            }
                        ) { Text("项目发布页") }
                    }
                    release?.let { info ->
                        SettingsFormDivider()
                        Text(
                            if (info.newer) "发现新版本 Windows V${info.version}"
                            else "当前版本 V$DESKTOP_VERSION，最新 Windows 稳定版 V${info.version}",
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (info.windowsAssets.isEmpty()) {
                            Text("此发布暂未提供 Windows 安装包。", fontSize = 12.sp, color = UiMuted)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                info.windowsAssets.forEach { (name, url) ->
                                    DesktopTextButton(
                                        onClick = {
                                            runCatching { Desktop.getDesktop().browse(URI(url)) }
                                                .onFailure { message = "无法打开下载地址。" }
                                        }
                                    ) { Text(name) }
                                }
                            }
                        }
                        info.checksumsUrl?.let { url ->
                            DesktopTextButton(
                                onClick = {
                                    runCatching { Desktop.getDesktop().browse(URI(url)) }
                                        .onFailure { message = "无法打开校验文件。" }
                                }
                            ) { Text("查看 SHA-256 校验文件") }
                        }
                    }
                }
                SettingsSectionCard("运行诊断与许可") {
                    Text("诊断包含播放指标及脱敏运行日志，便于定位与反馈问题。", color = UiMuted, fontSize = 12.sp)
                    FlowRow(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DesktopOutlinedButton(
                            enabled = !busy,
                            onClick = {
                                val chooser = JFileChooser().apply { selectedFile = File("mylive-diagnostics.json") }
                                if (chooser.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION) {
                                    val file = chooser.selectedFile.toPath()
                                    val metrics = playback.diagnostics()
                                    scope.launch {
                                        busy = true
                                        try {
                                            withContext(Dispatchers.IO) { DesktopDiagnostics.writeNew(file, room, metrics) }
                                            message = "诊断已导出。"
                                        } catch (_: FileAlreadyExistsException) {
                                            message = "文件已存在，请使用新的文件名。"
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            message = "诊断导出失败，请检查保存位置。"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            }
                        ) { Text("导出诊断") }
                        DesktopTextButton(onClick = { DesktopDiagnostics.clear(); message = "运行日志已清除。" }) {
                            Text("清除运行日志")
                        }
                        DesktopTextButton(
                            onClick = {
                                runCatching { Desktop.getDesktop().open(File(System.getProperty("compose.application.resources.dir"), "notices")) }
                                    .onFailure { message = "未找到许可目录，请查看便携包中的 notices。" }
                            }
                        ) { Text("第三方许可与源码信息") }
                    }
                }
                DesktopThinProgressBar(visible = busy)
                message?.let { Text(it, fontSize = 12.sp, color = UiAccent) }
            }
        },
        confirmButton = { DesktopOutlinedButton(enabled = !busy, onClick = onDismiss) { Text("关闭") } }
    )
}
