package com.mylive.desktop

import com.mylive.app.core.common.RemoteSyncCrypto
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
    val message: String? = null
)

class DesktopRemoteSync(
    private val library: DesktopLibrary,
    private val service: RemoteSyncService = RemoteSyncService()
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(DesktopRemoteState())
    val state = _state.asStateFlow()
    @Volatile private var generation = 0
    private var job: Job? = null

    init {
        service.onFavoriteReceived = { overlay, content -> receive(LanSyncImportType.FOLLOW, content, overlay) }
        service.onHistoryReceived = { overlay, content -> receive(LanSyncImportType.HISTORY, content, overlay) }
        service.onShieldWordReceived = { overlay, content -> receive(LanSyncImportType.BLOCKED_WORD, content, overlay) }
        service.onRoomUserUpdated = { users -> _state.update { it.copy(users = users) } }
        service.onSyncError = { msg -> _state.update { it.copy(message = msg) } }
        service.onRoomDestroyed = { _ -> scope.launch { disconnect(); _state.update { it.copy(message = "同步房间已关闭") } } }
        scope.launch {
            service.connectionState.collect { connection ->
                if (connection == RemoteSyncConnectionState.DISCONNECTED && state.value.roomId.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            roomId = "",
                            inviteCode = "",
                            qrPayload = "",
                            busy = false,
                            users = emptyList(),
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
            try {
                library.importSyncPayload(type, content, overlay)
                _state.update { it.copy(message = "已收到并保存对方资料") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(message = "收到的资料无效或无法保存") }
            }
        }
    }

    fun disconnect() {
        generation++
        job?.cancel()
        job = null
        service.disconnect()
        _state.value = DesktopRemoteState()
    }

    override fun close() {
        disconnect()
        scope.cancel()
        service.onFavoriteReceived = null
        service.onHistoryReceived = null
        service.onShieldWordReceived = null
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
