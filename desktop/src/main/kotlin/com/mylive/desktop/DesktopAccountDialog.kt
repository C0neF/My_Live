package com.mylive.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.mylive.app.core.site.LiveSiteAccount
import com.mylive.app.core.site.douyu.inspectDouyuCookie
import kotlinx.coroutines.*
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Composable
fun DesktopAccountDialog(services: DesktopServices, owner: Window, onApplied: (LiveSiteAccount) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val accounts = services.accounts
    val statuses by accounts.state.collectAsState()
    var account by remember { mutableStateOf(LiveSiteAccount.DOUYU) }
    var cookie by remember(account) { mutableStateOf("") }
    var rememberLogin by remember(account) { mutableStateOf(statuses.getValue(account).remembered || !statuses.getValue(account).present) }
    var manual by remember(account) { mutableStateOf(account != LiveSiteAccount.BILIBILI) }
    var busy by remember { mutableStateOf(false) }
    var webOpen by remember { mutableStateOf(false) }
    var error by remember(account) { mutableStateOf<String?>(null) }
    var information by remember(account) { mutableStateOf("") }
    var checkSequence by remember { mutableIntStateOf(0) }
    val names = mapOf(LiveSiteAccount.BILIBILI to "哔哩哔哩", LiveSiteAccount.DOUYU to "斗鱼", LiveSiteAccount.DOUYIN to "抖音")
    LaunchedEffect(account, statuses.getValue(account), checkSequence) {
        information = try { services.login.describe(account) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { "暂时无法检查登录信息，可稍后重试。" }
    }
    fun save(selected: LiveSiteAccount, input: String) {
        val persist = rememberLogin
        scope.launch {
            busy = true; error = null
            try {
                val normalized = normalizeDesktopCookie(selected, input)
                require(normalized.isNotBlank())
                if (selected == LiveSiteAccount.DOUYU) require(inspectDouyuCookie(normalized).let { it.hasLoginToken || it.canRenew })
                if (accounts.update(selected, normalized, persist)) {
                    ensureActive(); onApplied(selected); cookie = ""; checkSequence++
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "未识别到有效登录信息，请检查 Cookie 或重新登录。" }
            finally { busy = false }
        }
    }
    DesktopDialog(onDismissRequest = { if (!busy && !webOpen) onDismiss() }, title = "平台账号", preferredWidth = 600.dp,
        modifier = Modifier.testTag("account-dialog"), content = {
            val platforms = names.keys.toList()
            DesktopDialogTabBar(names.values.toList(), platforms.indexOf(account), enabled = !busy && !webOpen) {
                account = platforms[it]
            }
            key(account) {
                DesktopDialogScroll(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                    Text(information, fontSize = 12.sp, color = UiMuted, modifier = Modifier.padding(vertical = 10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(rememberLogin, { rememberLogin = it }, enabled = !busy && !webOpen)
                        Text("在当前 Windows 账号中记住登录", fontSize = 13.sp)
                    }
                    Text(if (statuses.getValue(account).remembered) "当前登录信息已加密保存。" else "未勾选时，登录信息仅在本次运行期间有效。",
                        color = UiMuted, fontSize = 11.sp)
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (account == LiveSiteAccount.BILIBILI) DesktopOutlinedButton(enabled = !busy, onClick = { manual = !manual }) {
                            Text(if (manual) "二维码登录" else "手动 Cookie")
                        }
                        DesktopOutlinedButton(enabled = !busy && !webOpen, onClick = {
                            val selected = account
                            webOpen = true
                            try { DesktopWebLogin.open(owner, selected, { value -> save(selected, value) }, { error = it }) }
                            finally { webOpen = false }
                        }) { Text("打开网页登录") }
                        DesktopTextButton(enabled = !busy, onClick = { checkSequence++ }) { Text("检查登录") }
                    }
                    if (account == LiveSiteAccount.BILIBILI && !manual) {
                        BilibiliQrPanel(services.login, onCookie = { value ->
                            val saved = accounts.update(LiveSiteAccount.BILIBILI, value, rememberLogin)
                            currentCoroutineContext().ensureActive()
                            if (saved) onApplied(LiveSiteAccount.BILIBILI)
                            saved
                        })
                    } else {
                        OutlinedTextField(cookie, { cookie = it }, label = { Text("Cookie") }, visualTransformation = PasswordVisualTransformation(),
                            enabled = !busy, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        DesktopPrimaryButton(enabled = !busy && cookie.isNotBlank(), onClick = { save(account, cookie) }, modifier = Modifier.padding(top = 10.dp)) { Text("保存登录信息") }
                    }
                    if (account == LiveSiteAccount.DOUYU) DesktopTextButton(enabled = !busy, onClick = {
                        val selected = account
                        scope.launch {
                            busy = true
                            try {
                                val renewed = services.douyuSession.renew()
                                information = if (renewed) "斗鱼登录已续期" else "续期未成功，请检查网络或重新登录"
                                if (renewed) onApplied(selected)
                            } finally { busy = false }
                        }
                    }) { Text("续期斗鱼登录") }
                    (error ?: statuses.getValue(account).error)?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
    }, confirmButton = { DesktopOutlinedButton(enabled = !busy && !webOpen, onClick = onDismiss) { Text("完成") } },
        dismissButton = { DesktopTextButton(enabled = !busy && !webOpen, onClick = {
            val selected = account
            manual = true // Dispose any polling QR before applying sign-out.
            scope.launch {
                busy = true
                try { if (accounts.update(selected, "", false)) { onApplied(selected); cookie = ""; checkSequence++ } }
                finally { busy = false }
            }
        }) { Text("退出该平台账号") } })
}

@Composable
private fun BilibiliQrPanel(api: BilibiliQrApi, onCookie: suspend (String) -> Boolean) {
    val save by rememberUpdatedState(onCookie)
    val controller = remember(api) { BilibiliQrController(api, { save(it) }) }
    val state by controller.state.collectAsState()
    LaunchedEffect(controller) { controller.load() }
    DisposableEffect(controller) { onDispose { controller.close() } }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, state.url) {
        value = if (state.url.isBlank()) null else withContext(Dispatchers.Default) {
            val matrix = MultiFormatWriter().encode(state.url, BarcodeFormat.QR_CODE, 280, 280)
            val image = BufferedImage(280, 280, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until 280) for (x in 0 until 280) image.setRGB(x, y, if (matrix[x, y]) 0 else 0xFFFFFF)
            val bytes = ByteArrayOutputStream().use { ImageIO.write(image, "png", it); it.toByteArray() }
            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.status == DesktopQrStatus.Loading) CircularProgressIndicator(Modifier.padding(24.dp))
        else if (state.status !in listOf(DesktopQrStatus.Success, DesktopQrStatus.Failed, DesktopQrStatus.Expired)) bitmap?.let { Image(it, "哔哩哔哩登录二维码", Modifier.size(230.dp)) }
        Text(state.message ?: "使用哔哩哔哩手机客户端扫码登录", fontSize = 12.sp, color = UiMuted)
        if (state.status in listOf(DesktopQrStatus.Failed, DesktopQrStatus.Expired)) DesktopOutlinedButton(onClick = controller::load, modifier = Modifier.padding(top = 6.dp)) { Text("刷新二维码") }
    }
}
