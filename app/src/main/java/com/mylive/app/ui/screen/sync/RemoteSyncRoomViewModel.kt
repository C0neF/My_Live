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
import timber.log.Timber
import javax.inject.Inject

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

    private val _roomUsers = MutableStateFlow<List<RemoteRoomUser>>(emptyList())
    val roomUsers: StateFlow<List<RemoteRoomUser>> = _roomUsers.asStateFlow()

    private val _countDown = MutableStateFlow(600)
    val countDown: StateFlow<Int> = _countDown.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _roomDestroyedEvent = MutableSharedFlow<Unit>()
    val roomDestroyedEvent: SharedFlow<Unit> = _roomDestroyedEvent.asSharedFlow()

    private val _loadingState = MutableStateFlow<String?>(null)
    val loadingState: StateFlow<String?> = _loadingState.asStateFlow()

    private var timerJob: Job? = null

    init {
        setupCallbacks()
    }

    private fun setupCallbacks() {
        remoteSyncService.onFavoriteReceived = { overlay, content ->
            viewModelScope.launch {
                try {
                    followRepository.importFromJson(content, overlay)
                    val followCount = followRepository.getAllFollows().first().size
                    _toastMessage.emit("已同步关注列表（${followCount} 条）")
                } catch (e: Exception) {
                    CoreLog.e("RemoteSyncRoomViewModel: Favorite sync failed", e)
                    _toastMessage.emit("同步关注失败: ${e.message}")
                }
            }
        }

        remoteSyncService.onHistoryReceived = { overlay, content ->
            viewModelScope.launch {
                try {
                    val arr = JSONArray(content)
                    if (overlay) {
                        historyRepository.clearAllHistory()
                    }
                    val existingHistoriesById = historyRepository.getAllHistory().first().associateBy { it.id }
                    val histories = buildList {
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val siteId = item.optString("siteId", item.optString("site", ""))
                            val roomId = item.optString("roomId", item.optString("room", ""))
                            if (siteId.isBlank() || roomId.isBlank()) continue

                            val id = "${siteId}_${roomId}"
                            val userName = item.optString("userName", item.optString("name", ""))
                            val face = item.optString("face", item.optString("avatar", ""))
                            val updateTime = item.optLong("updateTime", System.currentTimeMillis())

                            val existing = existingHistoriesById[id]
                            if (existing != null && existing.updateTime > updateTime) {
                                continue
                            }

                            add(
                                HistoryEntity(
                                    id = id,
                                    roomId = roomId,
                                    siteId = siteId,
                                    userName = userName,
                                    face = face,
                                    updateTime = updateTime
                                )
                            )
                        }
                    }
                    historyRepository.addHistories(histories)
                    _toastMessage.emit("已同步历史记录")
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
                    if (overlay) {
                        shieldRepository.clearAllKeywords()
                    }
                    val shields = keywords.map { kw -> ShieldEntity(value = "keyword:$kw") }
                    shieldRepository.addShields(shields)
                    _toastMessage.emit("已同步屏蔽词")
                } catch (e: Exception) {
                    CoreLog.e("RemoteSyncRoomViewModel: Shield words sync failed", e)
                    _toastMessage.emit("同步屏蔽词失败: ${e.message}")
                }
            }
        }

        remoteSyncService.onBiliAccountReceived = { _, content ->
            viewModelScope.launch {
                try {
                    val obj = JSONObject(content)
                    val cookie = obj.optString("cookie")
                    if (cookie.isNotEmpty()) {
                        settingsRepository.setBilibiliCookie(cookie)
                        _toastMessage.emit("已同步哔哩哔哩账号")
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
                    val obj = JSONObject(content)
                    val cookie = obj.optString("cookie")
                    if (cookie.isNotEmpty()) {
                        settingsRepository.setDouyinCookie(cookie)
                        _toastMessage.emit("已同步抖音账号")
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
    }

    fun initRoom(roomParam: String) {
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
                        startTimer()
                    } else {
                        _toastMessage.emit("创建房间失败: ${resp.message}")
                        _roomDestroyedEvent.emit(Unit)
                    }
                } else {
                    val resp = remoteSyncService.joinRoom(serverUrl, proxyUrl, roomParam, clientInfo)
                    if (resp.isSuccess) {
                        _roomId.value = remoteSyncService.currentRoomId
                        startTimer()
                    } else {
                        _toastMessage.emit("加入房间失败: ${resp.message}")
                        _roomDestroyedEvent.emit(Unit)
                    }
                }
            } catch (e: Exception) {
                CoreLog.e("RemoteSyncRoomViewModel: Connection failed", e)
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

    override fun onCleared() {
        timerJob?.cancel()
        // RemoteSyncService is a @Singleton that outlives this ViewModel. Detach our callbacks
        // so it doesn't retain this cleared ViewModel (and its captured viewModelScope).
        remoteSyncService.onFavoriteReceived = null
        remoteSyncService.onHistoryReceived = null
        remoteSyncService.onShieldWordReceived = null
        remoteSyncService.onBiliAccountReceived = null
        remoteSyncService.onDouyinAccountReceived = null
        remoteSyncService.onRoomDestroyed = null
        remoteSyncService.onRoomUserUpdated = null
        remoteSyncService.disconnect()
        super.onCleared()
    }
}
