package com.mylive.desktop

import com.mylive.app.core.common.RemoteSyncCrypto
import com.mylive.app.core.site.LiveSiteAccount
import com.mylive.app.core.sync.LanSyncImportType
import com.mylive.app.service.RemoteRoomUser
import com.mylive.app.service.RemoteSyncConnectionState
import com.mylive.app.service.RemoteSyncService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class DesktopRemoteState(
    val busy: Boolean = false,
    val roomId: String = "",
    val inviteCode: String = "",
    val qrPayload: String = "",
    val users: List<RemoteRoomUser> = emptyList(),
    val message: String? = null,
    val pendingPrompt: String? = null
)

class DesktopRemoteSync(
    private val library: DesktopLibrary,
    private val service: RemoteSyncService = RemoteSyncService(),
    private val accounts: DesktopAccounts? = null
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(DesktopRemoteState())
    val state = _state.asStateFlow()
    @Volatile private var generation = 0
    private var job: Job? = null
    private data class PendingRemoteChange(val prompt: String, val apply: suspend () -> Unit)
    private val pendingQueue = ArrayDeque<PendingRemoteChange>()

    init {
        service.onFavoriteReceived = { overlay, content -> receive(LanSyncImportType.FOLLOW, content, overlay) }
        service.onHistoryReceived = { overlay, content -> receive(LanSyncImportType.HISTORY, content, overlay) }
        service.onShieldWordReceived = { overlay, content -> receive(LanSyncImportType.BLOCKED_WORD, content, overlay) }
        service.onBiliAccountReceived = { _, content -> receiveAccount(LiveSiteAccount.BILIBILI, "哔哩哔哩", content) }
        service.onDouyinAccountReceived = { _, content -> receiveAccount(LiveSiteAccount.DOUYIN, "抖音", content) }
        service.onRoomUserUpdated = { users -> _state.update { it.copy(users = users) } }
        service.onSyncError = { msg -> _state.update { it.copy(message = msg) } }
        service.onRoomDestroyed = { _ -> scope.launch { disconnect(); _state.update { it.copy(message = "同步房间已关闭") } } }
        scope.launch {
            service.connectionState.collect { connection ->
                if (connection == RemoteSyncConnectionState.DISCONNECTED && state.value.roomId.isNotEmpty()) {
                    pendingQueue.clear()
                    _state.update {
                        it.copy(
                            roomId = "",
                            inviteCode = "",
                            qrPayload = "",
                            busy = false,
                            users = emptyList(),
                            pendingPrompt = null,
                            message = "同步连接已断开"
                        )
                    }
                }
            }
        }
    }

    fun connect(server: String, roomInvite: String?) {
        disconnect()
        val token = generation
        _state.value = DesktopRemoteState(busy = true)
        job = scope.launch {
            try {
                validateDesktopRemoteUrl(server)
                val info = JSONObject()
                    .put("app", "My Live")
                    .put("platform", "windows")
                    .put("version", DESKTOP_VERSION)
                val result = if (roomInvite == null) {
                    service.createRoom(server, "direct", info)
                } else {
                    val parsed = requireNotNull(RemoteSyncCrypto.parseInvite(roomInvite)) {
                        "请输入完整的加密房间配对码"
                    }
                    service.joinRoom(server, "direct", parsed.roomId, info, parsed.roomKey)
                }
                ensureActive()
                if (generation == token) {
                    _state.update {
                        it.copy(
                            busy = false,
                            roomId = if (result.isSuccess) service.currentRoomId else "",
                            inviteCode = if (result.isSuccess) service.currentInviteCode else "",
                            qrPayload = if (result.isSuccess) service.currentQrPayload else "",
                            message = if (result.isSuccess) "已连接加密同步房间" else result.message
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == token) {
                    _state.value = DesktopRemoteState(message = "同步服务连接失败，请检查地址、房间配对码和网络。")
                }
            }
        }
    }

    fun send(libraryState: DesktopLibraryState) {
        if (state.value.busy || state.value.roomId.isEmpty()) return
        val token = generation
        job = scope.launch {
            _state.update { it.copy(busy = true, message = "正在加密发送资料") }
            try {
                val root = withContext(Dispatchers.IO) { JSONObject(RoomProfileCodec.export(libraryState)) }
                val sections = listOf(
                    "SendFavorite" to JSONObject().put("follows", root.getJSONArray("followUsers")).put("tags", root.getJSONArray("followUserTags")).toString(),
                    "SendHistory" to root.getJSONArray("histories").toString(),
                    "SendShieldWord" to JSONArray(libraryState.danmaku.keywords).toString()
                )
                sections.forEach { (type, content) -> check(service.sendContent(type, false, content).isSuccess) }
                _state.update { it.copy(message = "关注、分组、历史和屏蔽词已加密发送") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == token) _state.update { it.copy(message = "资料发送未完成，请检查连接后重试。") }
            } finally {
                if (generation == token) _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun receive(type: LanSyncImportType, content: String, overlay: Boolean) {
        val token = generation
        scope.launch {
            if (generation != token || state.value.roomId.isEmpty()) return@launch
            if (overlay) {
                val label = when (type) {
                    LanSyncImportType.FOLLOW -> "关注列表"
                    LanSyncImportType.HISTORY -> "观看历史"
                    LanSyncImportType.BLOCKED_WORD -> "弹幕屏蔽词"
                    else -> "资料"
                }
                enqueuePrompt("收到覆盖${label}请求，是否使用对方数据替换本机现有${label}？") {
                    library.importSyncPayload(type, content, true)
                    _state.update { it.copy(message = "已收到并覆盖${label}") }
                }
                return@launch
            }
            try {
                library.importSyncPayload(type, content, false)
                _state.update { it.copy(message = "已收到并保存对方资料") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(message = "收到的资料无效或无法保存") }
            }
        }
    }

    private fun receiveAccount(account: LiveSiteAccount, label: String, content: String) {
        val token = generation
        scope.launch {
            if (generation != token || state.value.roomId.isEmpty()) return@launch
            val store = accounts ?: run {
                _state.update { it.copy(message = "当前同步窗口未挂载账号存储，已忽略${label}账号推送") }
                return@launch
            }
            val cookie = runCatching { JSONObject(content).optString("cookie", "").trim() }.getOrDefault("")
            if (cookie.isEmpty() || cookie.toByteArray(Charsets.UTF_8).size > 65_536 || cookie.any { it == '\r' || it == '\n' || it == '\u0000' }) {
                _state.update { it.copy(message = "收到的${label}账号 Cookie 无效") }
                return@launch
            }
            enqueuePrompt("收到对方发来的${label}登录凭据，是否覆盖保存到本机？") {
                val saved = store.update(account, cookie, remember = true)
                _state.update {
                    it.copy(message = if (saved) "已收到并保存${label}账号" else "保存${label}账号失败")
                }
            }
        }
    }

    fun confirmPending() {
        val current = pendingQueue.removeFirstOrNull() ?: return
        _state.update { it.copy(pendingPrompt = pendingQueue.firstOrNull()?.prompt) }
        val token = generation
        scope.launch {
            if (generation != token) return@launch
            try {
                current.apply()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == token) _state.update { it.copy(message = "收到的资料无效或无法保存") }
            }
        }
    }

    fun dismissPending() {
        pendingQueue.removeFirstOrNull()
        _state.update { it.copy(pendingPrompt = pendingQueue.firstOrNull()?.prompt, message = "已忽略该同步请求") }
    }

    private fun enqueuePrompt(prompt: String, apply: suspend () -> Unit) {
        pendingQueue.addLast(PendingRemoteChange(prompt, apply))
        if (_state.value.pendingPrompt == null) {
            _state.update { it.copy(pendingPrompt = pendingQueue.firstOrNull()?.prompt) }
        }
    }

    fun disconnect() {
        generation++
        job?.cancel()
        job = null
        pendingQueue.clear()
        service.disconnect()
        _state.value = DesktopRemoteState()
    }

    override fun close() {
        disconnect()
        scope.cancel()
        service.onFavoriteReceived = null
        service.onHistoryReceived = null
        service.onShieldWordReceived = null
        service.onBiliAccountReceived = null
        service.onDouyinAccountReceived = null
        service.onRoomUserUpdated = null
        service.onRoomDestroyed = null
        service.onSyncError = null
    }
}

internal fun validateDesktopRemoteUrl(text: String) {
    val uri = URI(text)
    require((uri.scheme == "wss" || uri.scheme == "ws" && uri.host in setOf("127.0.0.1", "localhost", "::1")) &&
        !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null)
}
