package com.mylive.app.ui.screen.sync

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import com.mylive.app.core.common.LanSyncAuth
import com.mylive.app.core.common.LanSyncCrypto
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.ProfileBackupManager
import com.mylive.app.data.repository.ShieldRepository
import com.mylive.app.ui.component.settings.SettingsMenu
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

// ─── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class SyncDeviceViewModel @Inject constructor(
    private val followRepository: FollowRepository,
    private val historyRepository: HistoryRepository,
    private val shieldRepository: ShieldRepository,
    private val profileBackupManager: ProfileBackupManager
) : ViewModel() {

    private val _syncResults = MutableStateFlow<Map<String, String>>(emptyMap())
    val syncResults: StateFlow<Map<String, String>> = _syncResults.asStateFlow()

    private val _syncingKey = MutableStateFlow<String?>(null)
    val syncingKey: StateFlow<String?> = _syncingKey.asStateFlow()

    private val client = OkHttpClient()

    private companion object {
        const val LAN_SYNC_JOB_TIMEOUT_MS = 30_000L
        const val LAN_SYNC_JOB_POLL_INTERVAL_MS = 300L
    }

    private fun buildUrl(address: String, port: Int, endpoint: String, overlay: Boolean = false): String {
        val overlayParam = if (overlay) "?overlay=1" else ""
        return "http://$address:$port$endpoint$overlayParam"
    }

    fun syncProfile(address: String, port: Int, token: String, overlay: Boolean = false) {
        viewModelScope.launch {
            _syncingKey.value = "profile"
            try {
                val json = profileBackupManager.exportProfileJson()
                postJson(address, port, "/sync/profile", json, token, overlay)
                _syncResults.value = _syncResults.value + ("profile" to "✅ 配置包同步成功")
            } catch (e: Exception) {
                _syncResults.value = _syncResults.value + ("profile" to "❌ 配置包同步失败: ${e.message}")
            } finally {
                _syncingKey.value = null
            }
        }
    }

    fun syncFollow(address: String, port: Int, token: String, overlay: Boolean = false) {
        viewModelScope.launch {
            _syncingKey.value = "follow"
            try {
                val follows = followRepository.getAllFollows().first()
                val jsonArray = org.json.JSONArray()
                follows.forEach { f ->
                    jsonArray.put(JSONObject().apply {
                        put("siteId", f.siteId)
                        put("id", f.id)
                        put("roomId", f.roomId)
                        put("userName", f.userName)
                        put("face", f.face)
                        put("addTime", f.addTime.toString())
                        put("tag", f.tag)
                        put("isSpecialFollow", f.isSpecialFollow)
                    })
                }
                postJson(address, port, "/sync/follow", jsonArray.toString(), token, overlay)

                // Also sync tags
                val tags = followRepository.getAllTags().first()
                postJson(address, port, "/sync/tag", encodeFollowTagsForLanSync(tags), token, overlay)

                _syncResults.value = _syncResults.value + ("follow" to "✅ 关注列表同步成功")
            } catch (e: Exception) {
                _syncResults.value = _syncResults.value + ("follow" to "❌ 关注列表同步失败: ${e.message}")
            } finally {
                _syncingKey.value = null
            }
        }
    }

    fun syncHistory(address: String, port: Int, token: String, overlay: Boolean = false) {
        viewModelScope.launch {
            _syncingKey.value = "history"
            try {
                val history = historyRepository.getAllHistory().first()
                val jsonArray = org.json.JSONArray()
                history.forEach { h ->
                    jsonArray.put(JSONObject().apply {
                        put("id", h.id)
                        put("roomId", h.roomId)
                        put("siteId", h.siteId)
                        put("userName", h.userName)
                        put("face", h.face)
                        put("updateTime", h.updateTime.toString())
                    })
                }
                postJson(address, port, "/sync/history", jsonArray.toString(), token, overlay)
                _syncResults.value = _syncResults.value + ("history" to "✅ 观看历史同步成功")
            } catch (e: Exception) {
                _syncResults.value = _syncResults.value + ("history" to "❌ 观看历史同步失败: ${e.message}")
            } finally {
                _syncingKey.value = null
            }
        }
    }

    fun syncShield(address: String, port: Int, token: String, overlay: Boolean = false) {
        viewModelScope.launch {
            _syncingKey.value = "shield"
            try {
                val shields = shieldRepository.getAllShields().first()
                val payload = encodeShieldKeywordsForLanSync(shields)
                postJson(address, port, "/sync/blocked_word", payload, token, overlay)
                _syncResults.value = _syncResults.value + ("shield" to "✅ 屏蔽词同步成功")
            } catch (e: Exception) {
                _syncResults.value = _syncResults.value + ("shield" to "❌ 屏蔽词同步失败: ${e.message}")
            } finally {
                _syncingKey.value = null
            }
        }
    }

    /**
     * Sends one sealed, signed sync request.
     *
     * The pairing code stays on this device: it is only used locally to derive the payload key and
     * to sign the request. Putting the code itself in a header (as this used to do) handed anyone
     * sniffing the plain-HTTP LAN traffic the key to the very ciphertext travelling beside it.
     */
    private suspend fun postJson(
        address: String,
        port: Int,
        endpoint: String,
        json: String,
        token: String,
        overlay: Boolean
    ) {
        if (token.isEmpty()) {
            throw Exception("未配对，请先扫描对方二维码或填写配对码")
        }
        withContext(Dispatchers.IO) {
            // One timestamp + nonce describes this exact request. The peer rebuilds the same
            // context from the request line, so the endpoint and the destructive `overlay` flag
            // are covered by both the signature and the AEAD tag, and the nonce makes the request
            // single-use.
            val timestamp = System.currentTimeMillis()
            val nonce = LanSyncAuth.newNonce()
            val requestContext = LanSyncAuth.requestContext(
                method = "POST",
                path = endpoint,
                overlay = overlay,
                timestampMillis = timestamp,
                nonce = nonce
            )

            // LAN sync runs over plain HTTP, so seal the payload before it leaves the device.
            // Follows / history / settings must not be readable (or forgeable) by anyone else on
            // the same Wi-Fi.
            val sealed = LanSyncCrypto.encrypt(token, json, requestContext)
            val proof = LanSyncAuth.createProof(
                token = token,
                method = "POST",
                path = endpoint,
                overlay = overlay,
                body = sealed,
                timestampMillis = timestamp,
                nonce = nonce
            )

            val body = sealed.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(buildUrl(address, port, endpoint, overlay))
                .post(body)
                .addHeader(LanSyncAuth.HEADER_NAME, proof.headerValue)
                .build()
            client.newCall(request).execute().use { response ->
                val acceptedJob = validateLanSyncResponse(
                    response.code,
                    response.isSuccessful,
                    response.body?.string()
                )
                if (acceptedJob != null) {
                    val jobUrl = response.request.url.resolve(acceptedJob.jobUrl)
                        ?: throw Exception("invalid sync job url")
                    pollLanSyncJob(jobUrl, token)
                }
            }
        }
    }

    private suspend fun pollLanSyncJob(jobUrl: HttpUrl, token: String) {
        val deadline = System.currentTimeMillis() + LAN_SYNC_JOB_TIMEOUT_MS
        while (true) {
            // Every poll is signed afresh: a GET carries no body, so the proof covers the job path
            // plus a new timestamp and nonce.
            val proof = LanSyncAuth.createProof(
                token = token,
                method = "GET",
                path = jobUrl.encodedPath,
                overlay = false,
                body = ""
            )
            val request = Request.Builder()
                .url(jobUrl)
                .get()
                .addHeader(LanSyncAuth.HEADER_NAME, proof.headerValue)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    throw Exception("未配对或配对码错误，请扫描对方二维码或填写配对码")
                }
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }

                val payload = JSONObject(response.body?.string().orEmpty())
                if (payload.has("status") && !payload.optBoolean("status", true)) {
                    throw Exception(payload.optString("message", "sync failed"))
                }
                when (payload.optString("state")) {
                    "succeeded" -> return
                    "queued", "running" -> Unit
                    "failed" -> throw Exception(payload.optString("message", "sync failed"))
                    else -> throw Exception("未知同步状态")
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                throw Exception("等待对方导入完成超时")
            }
            delay(LAN_SYNC_JOB_POLL_INTERVAL_MS)
        }
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDeviceScreen(
    navigator: Navigator,
    key: Route.SyncDevice,
    viewModel: SyncDeviceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val syncingKey by viewModel.syncingKey.collectAsStateWithLifecycle()
    val syncResults by viewModel.syncResults.collectAsStateWithLifecycle()

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

    // Extract parameters from Route key
    val address = key.address
    val port = key.port.toIntOrNull() ?: 23234
    val deviceName = key.name

    var token by remember { mutableStateOf(key.token) }
    var showConfirmDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备同步") },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Device info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$address:$port",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = token,
                onValueChange = { token = it.trim() },
                label = { Text("配对码") },
                placeholder = { Text("扫码会自动填入；手动连接请填对方“本机信息”中的配对码") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Text(
                text = "同步操作",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Sync operations
            val operations = listOf(
                "profile" to "完整配置包" to { showConfirmDialog = "profile" },
                "follow" to "关注列表 + 标签" to { showConfirmDialog = "follow" },
                "history" to "观看历史" to { showConfirmDialog = "history" },
                "shield" to "弹幕屏蔽词" to { showConfirmDialog = "shield" }
            )

            operations.forEachIndexed { index, (pair, action) ->
                val (key, title) = pair
                val result = syncResults[key]
                val isSyncing = syncingKey == key

                SettingsMenu(
                    title = if (isSyncing) "$title (同步中...)" else title,
                    subtitle = result ?: "点击开始同步",
                    onClick = {
                        if (syncingKey == null) action()
                    },
                    enabled = syncingKey == null
                )
                if (index < operations.lastIndex) {
                    HorizontalDivider()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "提示：同步操作会将数据推送到目标设备。账号 Cookie 不通过局域网 HTTP 同步。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Confirmation dialog
    if (showConfirmDialog != null) {
        val key = showConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            title = { Text("确认同步") },
            text = { Text("确定要将数据同步到 $deviceName 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = null
                    when (key) {
                        "profile" -> viewModel.syncProfile(address, port, token)
                        "follow" -> viewModel.syncFollow(address, port, token)
                        "history" -> viewModel.syncHistory(address, port, token)
                        "shield" -> viewModel.syncShield(address, port, token)
                    }
                }) { Text("同步") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = null }) { Text("取消") }
            }
        )
    }
}
