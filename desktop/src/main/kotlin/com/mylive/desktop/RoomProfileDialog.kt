package com.mylive.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.FileAlreadyExistsException
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun RoomProfileDialog(repository: DesktopLibrary, state: DesktopLibraryState, parentWindow: java.awt.Window, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<RoomProfileImport?>(null) }
    var filename by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var restoreSettings by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("资料备份") }, text = {
        Column(Modifier.width(480.dp)) {
            Text("可导入 Android 或 Windows 的资料备份，也支持旧版关注 JSON。", fontSize = 13.sp)
            Text("备份包含关注、历史、分组、备注、屏蔽和支持的设置。账号及同步密码单独保存；现有房间会合并去重。",
                color = UiMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopOutlinedButton(enabled = !busy && state.writable, onClick = {
                    val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("JSON 备份", "json") }
                    if (chooser.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
                        val selected = chooser.selectedFile.toPath()
                        preview = null; message = null; error = null; filename = chooser.selectedFile.name
                        scope.launch {
                            busy = true
                            try { preview = withContext(Dispatchers.IO) { RoomProfileCodec.read(selected) } }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) {
                                error = if (failure is IllegalArgumentException) failure.message else "备份读取失败，请检查文件是否为有效的 JSON。"
                            } finally { busy = false }
                        }
                    }
                }) { Text("选择备份文件") }
                DesktopOutlinedButton(enabled = !busy && state.loaded && state.writable, onClick = {
                    val chooser = JFileChooser().apply {
                        fileFilter = FileNameExtensionFilter("JSON 备份", "json")
                        selectedFile = File("mylive-profile.json")
                    }
                    if (chooser.showSaveDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
                        val selected = chooser.selectedFile.let { if (it.name.endsWith(".json", true)) it else File(it.parentFile, "${it.name}.json") }
                        val snapshot = state
                        scope.launch {
                            busy = true; error = null; message = null
                            try {
                                withContext(Dispatchers.IO) { RoomProfileCodec.writeNew(selected.toPath(), snapshot) }
                                message = "已导出 ${snapshot.follows.size} 个关注和 ${snapshot.history.size} 条历史。"
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: FileAlreadyExistsException) { error = "文件已存在，请使用新的文件名。" }
                            catch (_: Exception) { error = "导出失败，请检查保存位置和磁盘空间。" }
                            finally { busy = false }
                        }
                    }
                }) { Text("导出完整资料") }
            }
            preview?.let { plan ->
                Text(filename, fontSize = 12.sp, color = UiMuted, modifier = Modifier.padding(top = 12.dp))
                Text("关注 ${plan.follows.size} 个 · 历史 ${plan.history.size} 个", modifier = Modifier.padding(top = 8.dp))
                Text("合并后保留最近 ${DesktopLibrary.MAX_HISTORY} 条历史。", color = UiMuted, fontSize = 12.sp)
                if (plan.preferences != null) SettingsToggle("恢复备份中的设置与屏蔽", restoreSettings, !busy) { restoreSettings = it }
                DesktopPrimaryButton(enabled = !busy && state.writable, onClick = {
                    scope.launch {
                        busy = true; error = null
                        try {
                            if (repository.importRooms(if (restoreSettings) plan else plan.copy(preferences = null))) { message = "资料已合并保存。"; preview = null }
                            else error = repository.state.value.error
                        } finally { busy = false }
                    }
                }, modifier = Modifier.padding(top = 12.dp)) { Text("确认合并导入") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            message?.let { Text(it, modifier = Modifier.padding(top = 12.dp), fontSize = 13.sp) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp), fontSize = 13.sp) }
        }
    }, confirmButton = { DesktopTextButton(enabled = !busy, onClick = onDismiss) { Text("关闭") } })
}
