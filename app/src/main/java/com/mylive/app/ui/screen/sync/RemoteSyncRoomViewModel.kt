package com.mylive.app.ui.screen.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.BuildConfig
import com.mylive.app.core.common.CoreLog
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.data.repository.ShieldRepository
import com.mylive.app.service.RemoteRoomUser
import com.mylive.app.service.RemoteSyncConnectionState
import com.mylive.app.service.RemoteSyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import timber.log.Timber
import javax.inject.Inject

data class PendingRemoteSyncAction(
    val title: String,
    val message: String,
    internal val apply: suspend () -> Unit
)

@HiltViewModel
class RemoteSyncRoomViewModel @Inject constructor(
    private val remoteSyncService: RemoteSyncService,
    private val followRepository: FollowRepository,
    private val historyRepository: HistoryRepository,
    private val shieldRepository: ShieldRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val connectionState: StateFlow<RemoteSyncConnectionState> = remoteSyncService.connectionState

    private val _roomId = MutableStateFlow("")
    val roomId: StateFlow<String> = _roomId.asStateFlow()

    private val _inviteCode = MutableStateFlow("")
    val inviteCode: StateFlow<String> = _inviteCode.asStateFlow()

    private val _qrPayload = MutableStateFlow("")
    val qrPayload: StateFlow<String> = _qrPayload.asStateFlow()

    private val _roomUsers = MutableStateFlow<List<RemoteRoomUser>>(emptyList())
    val roomUsers: StateFlow<List<RemoteRoomUser>> = _roomUsers.asStateFlow()

    private val _countDown = MutableStateFlow(600)
    val countDown: StateFlow<Int> = _countDown.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _roomDestroyedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val roomDestroyedEvent: SharedFlow<Unit> = _roomDestroyedEvent.asSharedFlow()

    private val _loadingState = MutableStateFlow<String?>(null)
    val loadingState: StateFlow<String?> = _loadingState.asStateFlow()

    private val pendingActionQueue = ArrayDeque<PendingRemoteSyncAction>()
    private val _pendingIncomingAction = MutableStateFlow<PendingRemoteSyncAction?>(null)
    val pendingIncomingAction: StateFlow<PendingRemoteSyncAction?> = _pendingIncomingAction.asStateFlow()

    private var timerJob: Job? = null
    private var initializedRoomKey: Pair<String, String>? = null

    init {
        setupCallbacks()
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state == RemoteSyncConnectionState.DISCONNECTED) {
                    timerJob?.cancel()
                    timerJob = null
                }
            }
        }
    }

    private fun setupCallbacks() {
        remoteSyncService.claimCallbacks(this)
        remoteSyncService.onFavoriteReceived = { overlay, content ->
            viewModelScope.launch {
                try {
                    validateRemoteSyncFavoriteBounds(content)
                    val applyImport: suspend () -> Unit = {
                        followRepository.importFromJson(content, overlay)
                        val followCount = followRepository.getAllFollows().first().size
                        _toastMessage.emit("已同步关注列表（${followCount} 条）")
                    }
                    if (overlay) {
                        enqueueIncomingConfirmation(
                            title = "覆盖关注列表",
                            message = "对方请求使用收到的关注列表覆盖本机现有关注与分组，是否确认？",
                            apply = applyImport
                        )
                    } else {
                        applyImport()
                    }
                } catch (e: Exception) {
                    CoreLog.e("RemoteSyncRoomViewModel: Favorite sync failed", e)
                    _toastMessage.emit("同步关注失败: ${e.message}")
                }
            }
        }

        remoteSyncService.onHistoryReceived = { overlay, content ->
            viewModelScope.launch {
                try {
                    validateRemoteSyncHistoryBounds(content)
                    val applyImport: suspend () -> Unit = {
                        historyRepository.importFromJson(content, overlay)
                        _toastMessage.emit("已同步历史记录")
                    }
                    if (overlay) {
                        enqueueIncomingConfirmation(
                            title = "覆盖观看历史",
                            message = "对方请求使用收到的观看记录覆盖本机历史记录，是否确认？",
                            apply = applyImport
                        )
                    } else {
                        applyImport()
                    }
                } catch (e: Exception) {
                    CoreLog.e("RemoteSyncRoomViewModel: History sync failed", e)
                    _toastMessage.emit("同步历史失败: ${e.message}")
                }
            }
        }

        remoteSyncService.onShieldWordReceived = { overlay, content ->
            viewModelScope.launch {
                try {
                    val keywords = decodeLanSyncShieldKeywords(content)
                    validateRemoteSyncShieldBounds(keywords.size)
                    val applyImport: suspend () -> Unit = {
                        shieldRepository.importKeywords(keywords, overlay)
                        _toastMessage.emit("已同步屏蔽词")
                    }
                    if (overlay) {
                        enqueueIncomingConfirmation(
                            title = "覆盖弹幕屏蔽词",
                            message = "对方请求使用收到的屏蔽词覆盖本机现有屏蔽词，是否确认？",
                            apply = applyImport
                        )
                    } else {
                        applyImport()
                    }
                } catch (e: Exception) {
                    CoreLog.e("RemoteSyncRoomViewModel: Shield words sync failed", e)
                    _toastMessage.emit("同步屏蔽词失败: ${e.message}")
                }
            }
        }

        remoteSyncService.onBiliAccountReceived = { _, content ->
            viewModelScope.launch {
                try {
                    val cookie = parseValidatedSyncCookie(content)
                    if (cookie.isNotEmpty()) {
                        enqueueIncomingConfirmation(
                            title = "覆盖哔哩哔哩账号",
                            message = "收到对方发送的哔哩哔哩登录凭据，是否覆盖保存到本机？"
                        ) {
                            settingsRepository.setBilibiliCookie(cookie)
                            _toastMessage.emit("已同步哔哩哔哩账号")
                        }
                    } else {
                        _toastMessage.emit("同步哔哩哔哩账号 Cookie 为空")
                    }
                } catch (e: Exception) {
                    CoreLog.e("RemoteSyncRoomViewModel: Bilibili account sync failed", e)
                    _toastMessage.emit("同步哔哩哔哩账号失败: ${e.message}")
                }
            }
        }

        remoteSyncService.onDouyinAccountReceived = { _, content ->
            viewModelScope.launch {
                try {
                    val cookie = parseValidatedSyncCookie(content)
                    if (cookie.isNotEmpty()) {
                        enqueueIncomingConfirmation(
                            title = "覆盖抖音账号",
                            message = "收到对方发送的抖音登录凭据，是否覆盖保存到本机？"
                        ) {
                            settingsRepository.setDouyinCookie(cookie)
                            _toastMessage.emit("已同步抖音账号")
                        }
                    } else {
                        _toastMessage.emit("同步抖音账号 Cookie 为空")
                    }
                } catch (e: Exception) {
                    CoreLog.e("RemoteSyncRoomViewModel: Douyin account sync failed", e)
                    _toastMessage.emit("同步抖音账号失败: ${e.message}")
                }
            }
        }

        remoteSyncService.onRoomDestroyed = { reason ->
            viewModelScope.launch {
                _toastMessage.emit("房间已被销毁: $reason")
                _roomDestroyedEvent.emit(Unit)
            }
        }

        remoteSyncService.onRoomUserUpdated = { users ->
            _roomUsers.value = users
        }

        remoteSyncService.onSyncError = { msg ->
            viewModelScope.launch {
                _toastMessage.emit(msg)
            }
        }
    }

    fun initRoom(roomParam: String, roomKeyParam: String = "") {
        val target = roomParam.trim() to roomKeyParam.trim()
        if (initializedRoomKey == target && (_loadingState.value != null || _roomId.value.isNotEmpty())) {
            return
        }
        initializedRoomKey = target
        viewModelScope.launch {
            _loadingState.value = "连接中..."
            try {
                val serverUrl = settingsRepository.syncServerUrl.first()
                val proxyUrl = settingsRepository.syncProxyUrl.first()

                val clientInfo = JSONObject().apply {
                    put("app", "My Live")
                    put("platform", "android")
                    put("version", BuildConfig.VERSION_NAME)
                }

                if (roomParam.isBlank()) {
                    val resp = remoteSyncService.createRoom(serverUrl, proxyUrl, clientInfo)
                    if (resp.isSuccess) {
                        _roomId.value = remoteSyncService.currentRoomId
                        _inviteCode.value = remoteSyncService.currentInviteCode
                        _qrPayload.value = remoteSyncService.currentQrPayload
                        startTimer()
                    } else {
                        initializedRoomKey = null
                        _toastMessage.emit("创建房间失败: ${resp.message}")
                        _roomDestroyedEvent.emit(Unit)
                    }
                } else {
                    val resp = remoteSyncService.joinRoom(
                        configuredUrl = serverUrl,
                        configuredProxyUrl = proxyUrl,
                        roomId = roomParam,
                        clientInfo = clientInfo,
                        roomKey = roomKeyParam
                    )
                    if (resp.isSuccess) {
                        _roomId.value = remoteSyncService.currentRoomId
                        _inviteCode.value = remoteSyncService.currentInviteCode
                        _qrPayload.value = remoteSyncService.currentQrPayload
                        startTimer()
                    } else {
                        initializedRoomKey = null
                        _toastMessage.emit("加入房间失败: ${resp.message}")
                        _roomDestroyedEvent.emit(Unit)
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                initializedRoomKey = null
                Timber.e(e, "RemoteSyncRoomViewModel: Connection failed")
                _toastMessage.emit("连接同步服务失败: ${e.message}")
                _roomDestroyedEvent.emit(Unit)
            } finally {
                _loadingState.value = null
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        _countDown.value = 600
        timerJob = viewModelScope.launch {
            while (_countDown.value > 0) {
                delay(1000)
                _countDown.value -= 1
            }
            _toastMessage.emit("连接超时自动退出")
            _roomDestroyedEvent.emit(Unit)
        }
    }

    fun syncFollow(overlay: Boolean) {
        viewModelScope.launch {
            if (_roomUsers.value.size <= 1) {
                _toastMessage.emit("无设备连接")
                return@launch
            }
            _loadingState.value = "发送中..."
            try {
                val json = followRepository.exportToJson()
                val resp = remoteSyncService.sendContent(
                    action = "SendFavorite",
                    overlay = overlay,
                    content = json
                )
                if (resp.isSuccess) {
                    _toastMessage.emit("已发送关注列表")
                } else {
                    _toastMessage.emit("发送失败: ${resp.message}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("发送失败: ${e.message}")
            } finally {
                _loadingState.value = null
            }
        }
    }

    fun syncHistory(overlay: Boolean) {
        viewModelScope.launch {
            if (_roomUsers.value.size <= 1) {
                _toastMessage.emit("无设备连接")
                return@launch
            }
            _loadingState.value = "发送中..."
            try {
                val histories = historyRepository.getAllHistory().first()
                val arr = JSONArray().apply {
                    histories.forEach { hist ->
                        put(JSONObject().apply {
                            put("id", hist.id)
                            put("roomId", hist.roomId)
                            put("siteId", hist.siteId)
                            put("userName", hist.userName)
                            put("face", hist.face)
                            put("updateTime", hist.updateTime)
                        })
                    }
                }
                val resp = remoteSyncService.sendContent(
                    action = "SendHistory",
                    overlay = overlay,
                    content = arr.toString()
                )
                if (resp.isSuccess) {
                    _toastMessage.emit("已发送历史记录")
                } else {
                    _toastMessage.emit("发送失败: ${resp.message}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("发送失败: ${e.message}")
            } finally {
                _loadingState.value = null
            }
        }
    }

    fun syncBlockedWord(overlay: Boolean) {
        viewModelScope.launch {
            if (_roomUsers.value.size <= 1) {
                _toastMessage.emit("无设备连接")
                return@launch
            }
            _loadingState.value = "发送中..."
            try {
                val shields = shieldRepository.getAllShields().first()
                val payload = encodeShieldKeywordsForLanSync(shields)
                val resp = remoteSyncService.sendContent(
                    action = "SendShieldWord",
                    overlay = overlay,
                    content = payload
                )
                if (resp.isSuccess) {
                    _toastMessage.emit("已发送屏蔽词")
                } else {
                    _toastMessage.emit("发送失败: ${resp.message}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("发送失败: ${e.message}")
            } finally {
                _loadingState.value = null
            }
        }
    }

    fun syncBiliAccount() {
        viewModelScope.launch {
            if (_roomUsers.value.size <= 1) {
                _toastMessage.emit("无设备连接")
                return@launch
            }
            val cookie = settingsRepository.bilibiliCookie.first()
            if (cookie.isBlank()) {
                _toastMessage.emit("未登录哔哩哔哩")
                return@launch
            }
            _loadingState.value = "发送中..."
            try {
                val payload = JSONObject().apply {
                    put("cookie", cookie)
                }
                val resp = remoteSyncService.sendContent(
                    action = "SendBiliAccount",
                    overlay = true,
                    content = payload.toString()
                )
                if (resp.isSuccess) {
                    _toastMessage.emit("已发送哔哩哔哩账号")
                } else {
                    _toastMessage.emit("发送失败: ${resp.message}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("发送失败: ${e.message}")
            } finally {
                _loadingState.value = null
            }
        }
    }

    fun syncDouyinAccount() {
        viewModelScope.launch {
            if (_roomUsers.value.size <= 1) {
                _toastMessage.emit("无设备连接")
                return@launch
            }
            val cookie = settingsRepository.douyinCookie.first()
            if (cookie.isBlank()) {
                _toastMessage.emit("未配置抖音 Cookie")
                return@launch
            }
            _loadingState.value = "发送中..."
            try {
                val payload = JSONObject().apply {
                    put("cookie", cookie)
                }
                val resp = remoteSyncService.sendContent(
                    action = "SendDouyinAccount",
                    overlay = true,
                    content = payload.toString()
                )
                if (resp.isSuccess) {
                    _toastMessage.emit("已发送抖音账号")
                } else {
                    _toastMessage.emit("发送失败: ${resp.message}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("发送失败: ${e.message}")
            } finally {
                _loadingState.value = null
            }
        }
    }

    fun confirmIncomingAction() {
        val current = pendingActionQueue.removeFirstOrNull() ?: return
        _pendingIncomingAction.value = pendingActionQueue.firstOrNull()
        viewModelScope.launch {
            try {
                current.apply()
            } catch (e: Exception) {
                CoreLog.e("RemoteSyncRoomViewModel: Confirmed sync action failed", e)
                _toastMessage.emit("同步应用失败: ${e.message}")
            }
        }
    }

    fun dismissIncomingAction() {
        pendingActionQueue.removeFirstOrNull()
        _pendingIncomingAction.value = pendingActionQueue.firstOrNull()
    }

    private fun enqueueIncomingConfirmation(
        title: String,
        message: String,
        apply: suspend () -> Unit
    ) {
        pendingActionQueue.addLast(PendingRemoteSyncAction(title, message, apply))
        if (_pendingIncomingAction.value == null) {
            _pendingIncomingAction.value = pendingActionQueue.firstOrNull()
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        pendingActionQueue.clear()
        _pendingIncomingAction.value = null
        // An incoming page may already own the singleton during the outgoing transition.
        remoteSyncService.releaseCallbacks(this)
        super.onCleared()
    }

    private companion object {
        const val MAX_FOLLOW_ITEMS = 2_000
        const val MAX_FOLLOW_TAG_ITEMS = 1_000
        const val MAX_HISTORY_ITEMS = 5_000
        const val MAX_SHIELD_ITEMS = 5_000
        const val MAX_COOKIE_CHARS = 65_536

        fun validateRemoteSyncFavoriteBounds(content: String) {
            val parsed = JSONTokener(content).nextValue()
            when (parsed) {
                is JSONArray -> require(parsed.length() <= MAX_FOLLOW_ITEMS) { "关注数量超出同步上限" }
                is JSONObject -> {
                    val follows = parsed.optJSONArray("follows")
                    val tags = parsed.optJSONArray("tags")
                    require((follows?.length() ?: 0) <= MAX_FOLLOW_ITEMS) { "关注数量超出同步上限" }
                    require((tags?.length() ?: 0) <= MAX_FOLLOW_TAG_ITEMS) { "分组数量超出同步上限" }
                }
                else -> throw IllegalArgumentException("无效的关注同步格式")
            }
        }

        fun validateRemoteSyncHistoryBounds(content: String) {
            val parsed = JSONTokener(content).nextValue()
            require(parsed is JSONArray && parsed.length() <= MAX_HISTORY_ITEMS) { "历史数量超出同步上限" }
        }

        fun validateRemoteSyncShieldBounds(count: Int) {
            require(count <= MAX_SHIELD_ITEMS) { "屏蔽词数量超出同步上限" }
        }

        fun parseValidatedSyncCookie(content: String): String {
            val obj = JSONObject(content)
            val cookie = obj.optString("cookie").trim()
            if (cookie.isEmpty()) return ""
            require(cookie.length <= MAX_COOKIE_CHARS && cookie.none { it == '\r' || it == '\n' || it == '\u0000' }) {
                "账号 Cookie 格式无效"
            }
            return cookie
        }
    }
}
