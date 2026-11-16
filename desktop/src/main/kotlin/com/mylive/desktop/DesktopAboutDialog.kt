package com.mylive.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.FileAlreadyExistsException
import javax.swing.JFileChooser

@Composable
fun DesktopAboutDialog(services: DesktopServices, room: DesktopRoomState, playback: MpvDesktopPlayback,
    owner: java.awt.Window, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<DesktopRelease?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(DESKTOP_APP_NAME) }, text = {
        Column(Modifier.width(510.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("版本 $DESKTOP_VERSION", style = MaterialTheme.typography.bodySmall, color = UiMuted)
            Text("四个平台的直播、实时聊天与弹幕，支持 Android 资料迁移和同步。")
            Text(playback.diagnostics().summary, fontSize = 12.sp, color = UiMuted)
            Text("快捷键：F10 全区域 · F11 全屏 · P 小窗 · 空格暂停 · M 静音", color = UiMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopOutlinedButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try { release = DesktopUpdates(services.client).latest(); message = if (release == null) "暂未发现已发布的 Windows 稳定版。" else null }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { message = "检查更新失败，可从项目发布页查看。" }
                        finally { busy = false }
                    }
                }) { Text("检查更新") }
                DesktopTextButton(onClick = { runCatching { Desktop.getDesktop().browse(URI("$DESKTOP_PROJECT_URL/releases")) }.onFailure { message = "无法打开浏览器。" } }) { Text("项目发布页") }
            }
            release?.let { info ->
                Text(if (info.newer) "发现 Windows V${info.version}" else "当前版本 V$DESKTOP_VERSION，最新 Windows 稳定版 V${info.version}")
                if (info.windowsAssets.isEmpty()) Text("此发布暂未提供 Windows 安装包。", fontSize = 12.sp, color = UiMuted)
                else info.windowsAssets.forEach { (name, url) -> DesktopTextButton(onClick = { runCatching { Desktop.getDesktop().browse(URI(url)) }.onFailure { message = "无法打开下载地址。" } }) { Text(name) } }
                info.checksumsUrl?.let { url -> DesktopTextButton(onClick = { runCatching { Desktop.getDesktop().browse(URI(url)) }.onFailure { message = "无法打开校验文件。" } }) { Text("查看 SHA-256 校验文件") } }
            }
            HorizontalDivider()
            Text("诊断包含播放指标及脱敏运行日志，便于反馈问题。", color = UiMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopOutlinedButton(enabled = !busy, onClick = {
                    val chooser = JFileChooser().apply { selectedFile = File("mylive-diagnostics.json") }
                    if (chooser.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION) {
                        val file = chooser.selectedFile.toPath(); val metrics = playback.diagnostics()
                        scope.launch {
                            busy = true
                            try { withContext(Dispatchers.IO) { DesktopDiagnostics.writeNew(file, room, metrics) }; message = "诊断已导出。" }
                            catch (_: FileAlreadyExistsException) { message = "文件已存在，请使用新的文件名。" }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { message = "诊断导出失败，请检查保存位置。" }
                            finally { busy = false }
                        }
                    }
                }) { Text("导出诊断") }
                DesktopTextButton(onClick = { DesktopDiagnostics.clear(); message = "运行日志已清除。" }) { Text("清除运行日志") }
                DesktopTextButton(onClick = {
                    runCatching { Desktop.getDesktop().open(File(System.getProperty("compose.application.resources.dir"), "notices")) }.onFailure { message = "未找到许可目录，请查看便携包中的 notices。" }
                }) { Text("第三方许可与源码信息") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, fontSize = 12.sp) }
        }
    }, confirmButton = { DesktopTextButton(enabled = !busy, onClick = onDismiss) { Text("关闭") } })
}
