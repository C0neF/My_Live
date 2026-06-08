package com.mylive.app.ui.screen.room

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.model.*
import com.mylive.app.core.site.LiveDanmaku
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.bilibili.BiliBiliSite
import com.mylive.app.core.site.douyin.DouyinSite
import com.mylive.app.core.site.douyu.DouyuSite
import com.mylive.app.core.site.huya.HuyaSite
import com.mylive.app.data.repository.AccountRepository
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.screen.room.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class LiveRoomUiState(
    val isLoading: Boolean = true,
    val detail: LiveRoomDetail? = null,
    val error: String? = null,
    val isFollowing: Boolean = false,
    val onlineCount: Int = 0,
    val playQualities: List<LivePlayQuality> = emptyList(),
    val currentQualityIndex: Int = 0
)

@HiltViewModel
class LiveRoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sites: Set<@JvmSuppressWildcards LiveSite>,
    private val followRepository: FollowRepository,
    private val historyRepository: HistoryRepository,
    private val accountRepository: AccountRepository,
    val settingsRepository: SettingsRepository
) : ViewModel() {

    private fun selectPreferredQuality(qualities: List<LivePlayQuality>, preferredLevel: Int): Int {
        if (qualities.isEmpty()) return 0
        if (preferredLevel == 0) return 0 // Auto

        val keywords = when (preferredLevel) {
            1 -> listOf("原画", "蓝光", "4k", "1080p", "source", "origin")
            2 -> listOf("超清", "1080p", "720p")
            3 -> listOf("高清", "720p", "480p")
            4 -> listOf("标清", "480p", "360p")
            5 -> listOf("流畅", "360p", "240p", "180p")
            else -> emptyList()
        }

        for (keyword in keywords) {
            val index = qualities.indexOfFirst { q ->
                val lowerName = q.quality.lowercase()
                lowerName.contains(keyword)
            }
            if (index != -1) return index
        }

        return if (preferredLevel == 5) qualities.lastIndex else 0
    }

    var roomId: String = savedStateHandle.get<String>("roomId") ?: ""
        private set
    var siteId: String = savedStateHandle.get<String>("siteId") ?: ""
        private set

    private val _uiState = MutableStateFlow(LiveRoomUiState())
    val uiState: StateFlow<LiveRoomUiState> = _uiState.asStateFlow()

    private val _superChats = MutableStateFlow<List<LiveSuperChatMessage>>(emptyList())
    val superChats: StateFlow<List<LiveSuperChatMessage>> = _superChats.asStateFlow()

    // Keep last N messages for the chat panel.
    private val maxMessages = 200

    fun removeExpiredSuperChats() {
        val now = System.currentTimeMillis()
        _superChats.update { list ->
            list.filter { it.endTime > now }
        }
    }

    /**
     * Separate [StateFlow] for the danmaku list. Kept off the main [uiState] so
     * that high-frequency message updates do not trigger recomposition of the
     * whole screen (player controls, room info, quality sheet, etc.).
     */
    private val liveMessageBuffer = LiveMessageBuffer(maxMessages)
    private val _danmakuMessages = MutableStateFlow<List<DisplayLiveMessage>>(emptyList())
    val danmakuMessages: StateFlow<List<DisplayLiveMessage>> = _danmakuMessages.asStateFlow()

    /**
     * Serializes danmaku messages into state updates. Producers (the danmaku
     * callback, which can fire on any thread) [tryEmit] into this flow, and a
     * single consumer in [init] applies them, eliminating the read-modify-write
     * race that would otherwise drop messages under high message rates.
     */
    private val _messages = MutableSharedFlow<LiveMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val newDanmakuMessages: SharedFlow<LiveMessage> = _messages.asSharedFlow()

    /**
     * Independent scope for cleanup work that must run after [viewModelScope] is
     * cancelled in [onCleared]. A [SupervisorJob] prevents a failure in one child
     * (e.g. danmaku stop) from cancelling siblings, and the scope is GC'd with the
     * ViewModel.
     */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentSite: LiveSite? = null
    private var currentDanmaku: LiveDanmaku? = null
    private var activeRoute: Pair<String, String>? = null
    private var loadRoomJob: Job? = null
    var playerController: PlayerController? = null // Set from Composable
        set(value) {
            field = value
            Timber.d("PlayerController set: ${value != null}")
        }

    // Track if playback was requested but player wasn't ready yet
    private var pendingPlayRequest = false

    /**
     * Called when PlayerController is assigned from the Composable.
     * If qualities were already loaded but player wasn't ready, triggers playback now.
     */
    fun onPlayerControllerReady() {
        val detail = _uiState.value.detail ?: return
        val qualities = _uiState.value.playQualities
        val route = activeRoute ?: return
        if (qualities.isNotEmpty() && playerController != null) {
            Timber.d("Player ready, starting playback with ${qualities.size} qualities")
            pendingPlayRequest = false
            viewModelScope.launch {
                playWithQuality(detail, qualities[_uiState.value.currentQualityIndex], route)
            }
        } else if (qualities.isNotEmpty()) {
            pendingPlayRequest = true
        }
    }

    init {
        startRuntimeCollectors()
        if (roomId.isNotEmpty()) {
            openRoute(roomId, siteId)
        }
    }

    fun openRoute(roomId: String, siteId: String) {
        val nextRoomId = roomId.trim()
        val nextSiteId = siteId.trim()
        if (nextRoomId.isEmpty()) {
            this.roomId = ""
            this.siteId = nextSiteId
            playerController?.stop()
            _uiState.value = LiveRoomUiState(isLoading = false, error = "直播间参数缺失")
            playerController?.showError("直播间参数缺失")
            return
        }

        val nextRoute = nextSiteId to nextRoomId
        if (activeRoute == nextRoute) return

        activeRoute = nextRoute
        this.roomId = nextRoomId
        this.siteId = nextSiteId
        pendingPlayRequest = false
        currentSite = null
        playerController?.stop()
        _danmakuMessages.value = liveMessageBuffer.clear()
        _superChats.value = emptyList()

        currentDanmaku?.let { danmaku ->
            cleanupScope.launch {
                try {
                    danmaku.stop()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to stop previous danmaku")
                }
            }
        }
        currentDanmaku = null

        loadRoomJob?.cancel()
        loadRoomJob = viewModelScope.launch {
            // Stagger API and database loading so the first route frames stay light.
            kotlinx.coroutines.delay(AppMotion.LiveRoomDataStartupDelayMillis.toLong())
            if (!isActiveRoute(nextRoute)) return@launch
            loadRoomDetail(nextRoute)
        }
    }

    private fun isActiveRoute(route: Pair<String, String>): Boolean {
        return activeRoute == route
    }

    private fun startRuntimeCollectors() {
        viewModelScope.launch {
            _messages.collect { message ->
                if (message.type == LiveMessageType.ONLINE) {
                    _uiState.update { it.copy(onlineCount = message.onlineCount ?: 0) }
                } else {
                    if (message.type == LiveMessageType.SUPER_CHAT) {
                        message.superChatMessage?.let { sc ->
                            _superChats.update { current ->
                                val exists = current.any { it.id == sc.id || (it.userName == sc.userName && it.message == sc.message && it.startTime == sc.startTime) }
                                if (exists) current else current + sc
                            }
                        }
                    }
                    _danmakuMessages.value = liveMessageBuffer.add(message)
                }
            }
        }

        viewModelScope.launch {
            accountRepository.bilibiliCookie.collect { cookie ->
                sites.filterIsInstance<BiliBiliSite>().forEach { it.cookie = cookie }
            }
        }

        viewModelScope.launch {
            accountRepository.douyinCookie.collect { cookie ->
                sites.filterIsInstance<DouyinSite>().forEach { it.cookie = cookie }
            }
        }
    }

    private suspend fun loadRoomDetail(route: Pair<String, String>) {
        if (!isActiveRoute(route)) return
        val routeSiteId = route.first
        val routeRoomId = route.second
        _uiState.value = LiveRoomUiState(isLoading = true)
        try {
            // Use siteId to directly locate the target site if available
            var detail: LiveRoomDetail? = null
            var site: LiveSite? = null
            var targetSiteError: Throwable? = null

            val targetSite = if (routeSiteId.isNotEmpty()) {
                sites.firstOrNull { it.id == routeSiteId }
            } else null

            if (targetSite != null) {
                // Direct lookup by siteId - avoids unnecessary API calls to other platforms
                try {
                    detail = targetSite.getRoomDetail(routeRoomId)
                    site = targetSite
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    targetSiteError = e
                    Timber.w(e, "Failed to get room detail from target site: $routeSiteId")
                }
            } else {
                // Fallback: try each site until one succeeds
                for (s in sites) {
                    try {
                        detail = s.getRoomDetail(routeRoomId)
                        site = s
                        break
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        continue
                    }
                }
            }

            if (!isActiveRoute(route)) return

            if (targetSite != null && targetSiteError != null) {
                throw targetSiteError
            }

            if (detail == null || site == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "未找到直播间"
                )
                playerController?.showError("未找到直播间")
                return
            }

            currentSite = site

            // Check follow status
            val isFollowing = followRepository.isFollowing(site.id, routeRoomId)

            // Add to history
            try {
                historyRepository.addHistory(
                    HistoryEntity(
                        id = "${site.id}_$routeRoomId",
                        roomId = routeRoomId,
                        siteId = site.id,
                        userName = detail.userName,
                        face = detail.userAvatar,
                        updateTime = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to add history")
            }

            if (!isActiveRoute(route)) return

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                detail = detail,
                isFollowing = isFollowing
            )

            // Load play qualities
            loadPlayQualities(detail, route)

            if (!isActiveRoute(route)) return

            // Start danmaku
            startDanmaku(detail, route)

        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (!isActiveRoute(route)) return
            Timber.e(e, "Failed to load room detail")
            val message = e.message ?: "加载失败"
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = message
            )
            playerController?.showError(message)
        }
    }

    private suspend fun loadPlayQualities(detail: LiveRoomDetail, route: Pair<String, String>) {
        if (!isActiveRoute(route)) return
        val site = currentSite ?: return
        try {
            val qualities = site.getPlayQualites(detail)
            if (!isActiveRoute(route)) return
            val preferredLevel = settingsRepository.qualityLevel.first()
            val matchIndex = selectPreferredQuality(qualities, preferredLevel)
            if (!isActiveRoute(route)) return

            _uiState.value = _uiState.value.copy(
                playQualities = qualities,
                currentQualityIndex = matchIndex
            )
            if (qualities.isEmpty()) {
                playerController?.showError("暂无可播放画质")
                return
            }
            if (playerController != null) {
                // Player is ready, play immediately
                playWithQuality(detail, qualities[matchIndex], route)
            } else {
                // Player not ready yet, mark as pending
                pendingPlayRequest = true
                Timber.d("Qualities loaded but player not ready, marking as pending")
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (!isActiveRoute(route)) return
            Timber.e(e, "Failed to load play qualities")
            playerController?.showError(e.message ?: "加载播放画质失败")
        }
    }

    private suspend fun playWithQuality(detail: LiveRoomDetail, quality: LivePlayQuality, route: Pair<String, String>) {
        if (!isActiveRoute(route)) return
        val site = currentSite ?: return
        try {
            val playUrl = site.getPlayUrls(detail, quality)
            if (!isActiveRoute(route)) return
            playerController?.play(playUrl.urls, playUrl.headers)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (!isActiveRoute(route)) return
            Timber.e(e, "Failed to get play URLs")
            playerController?.showError(e.message ?: "获取播放地址失败")
        }
    }

    fun switchQuality(index: Int) {
        val detail = _uiState.value.detail ?: return
        val qualities = _uiState.value.playQualities
        if (index !in qualities.indices) return
        val route = activeRoute ?: return
        _uiState.value = _uiState.value.copy(currentQualityIndex = index)
        viewModelScope.launch {
            playWithQuality(detail, qualities[index], route)
        }
    }

    fun refreshPlay() {
        val detail = _uiState.value.detail ?: return
        val qualities = _uiState.value.playQualities
        val route = activeRoute ?: return
        if (qualities.isNotEmpty()) {
            val quality = qualities.getOrNull(_uiState.value.currentQualityIndex) ?: qualities[0]
            viewModelScope.launch {
                playWithQuality(detail, quality, route)
            }
        }
    }

    private fun startDanmaku(detail: LiveRoomDetail, route: Pair<String, String>) {
        if (!isActiveRoute(route)) return
        val site = currentSite ?: return
        val routeRoomId = route.second
        val danmaku = try {
            site.getDanmaku()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to create danmaku")
            return
        }
        if (!isActiveRoute(route)) {
            cleanupScope.launch {
                try {
                    danmaku.stop()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to stop stale danmaku")
                }
            }
            return
        }
        currentDanmaku = danmaku

        danmaku.onMessage = onMessage@ { message ->
            if (!isActiveRoute(route)) return@onMessage
            // Hand off to the serial consumer started in init. tryEmit never fails
            // because the flow is configured with extraBufferCapacity + DROP_OLDEST.
            _messages.tryEmit(message)
        }

        danmaku.onClose = onClose@ { reason ->
            if (!isActiveRoute(route)) return@onClose
            if (reason.contains("正在尝试重连")) {
                Timber.d("Danmaku reconnecting: $reason")
            } else {
                Timber.w("Danmaku closed: $reason")
            }
        }

        danmaku.onReady = onReady@ {
            if (!isActiveRoute(route)) return@onReady
            Timber.d("Danmaku connected")
        }

        // Start danmaku with appropriate args
        viewModelScope.launch {
            try {
                if (!isActiveRoute(route)) return@launch
                val args = when (site.id) {
                    "bilibili" -> {
                        val biliSite = site as? BiliBiliSite
                        biliSite?.lastDanmakuArgs ?: DanmakuArgs.BiliBili(
                            roomId = routeRoomId.toIntOrNull() ?: 0,
                            uid = 0, token = "", buvid = "", serverHost = "broadcastlv.chat.bilibili.com", cookie = ""
                        )
                    }
                    "douyu" -> DanmakuArgs.Douyu(roomId = routeRoomId)
                    "huya" -> {
                        val huyaSite = site as? HuyaSite
                        huyaSite?.getDanmakuArgs(routeRoomId) ?: DanmakuArgs.Huya(ayyuid = 0, topSid = 0, subSid = 0)
                    }
                    "douyin" -> {
                        val douyinSite = site as? DouyinSite
                        douyinSite?.lastDanmakuArgs ?: DanmakuArgs.Douyin(
                            webRid = routeRoomId, roomId = routeRoomId, userId = "", cookie = ""
                        )
                    }
                    else -> return@launch
                }
                if (!isActiveRoute(route)) return@launch
                danmaku.start(args)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                if (!isActiveRoute(route)) return@launch
                Timber.e(e, "Failed to start danmaku")
            }
        }
    }

    fun toggleFollow() {
        val detail = _uiState.value.detail ?: return
        val site = currentSite ?: return
        viewModelScope.launch {
            try {
                if (_uiState.value.isFollowing) {
                    val follow = followRepository.getFollow(site.id, roomId)
                    if (follow != null) {
                        followRepository.removeFollow(follow.id)
                    }
                } else {
                    followRepository.addFollow(
                        FollowUserEntity(
                            id = "${site.id}_$roomId",
                            roomId = roomId,
                            siteId = site.id,
                            userName = detail.userName,
                            face = detail.userAvatar,
                            addTime = System.currentTimeMillis(),
                            tag = "",
                            isSpecialFollow = false,
                            liveStatus = if (detail.status) 1 else 2,
                            liveStartTime = if (detail.status) System.currentTimeMillis() else null,
                            showTime = if (detail.status) detail.showTime else null
                        )
                    )
                }
                _uiState.value = _uiState.value.copy(isFollowing = !_uiState.value.isFollowing)
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle follow")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is already cancelled at this point; use the cleanup scope
        // so the suspend danmaku.stop() can complete.
        cleanupScope.launch {
            try {
                currentDanmaku?.stop()
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop danmaku")
            }
        }
        // playerController is owned by LiveRoomScreen and released in its
        // DisposableEffect.onDispose, so the ViewModel must not release it here.
    }
}
