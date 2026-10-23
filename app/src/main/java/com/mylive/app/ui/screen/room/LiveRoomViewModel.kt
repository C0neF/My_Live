package com.mylive.app.ui.screen.room

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.model.*
import com.mylive.app.core.site.LiveDanmakuSession
import com.mylive.app.core.site.LiveSite
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.data.repository.ShieldRepository
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.ui.screen.room.player.LivePlaybackEngine
import com.mylive.app.ui.screen.room.player.PlaybackRequestOrigin
import com.mylive.app.ui.screen.room.player.LivePlaybackSessionBinding
import com.mylive.app.ui.screen.room.player.isLiveQualitySupportedByHardware
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Shown in the chat panel when the danmaku connection could not be established. Playback is not
 * affected, so this is deliberately a panel-local notice rather than a room-level error.
 */
internal const val DANMAKU_UNAVAILABLE_NOTICE = "弹幕连接失败，不影响观看，可尝试刷新"
private const val CHAT_SNAPSHOT_PUBLICATION_INTERVAL_MILLIS = 16L

data class LiveRoomUiState(
    val isLoading: Boolean = true,
    val detail: LiveRoomDetail? = null,
    val error: String? = null,
    val isFollowing: Boolean = false,
    val isFollowStatusKnown: Boolean = false,
    val playQualities: List<LivePlayQuality> = emptyList(),
    val currentQualityIndex: Int = 0
)

internal fun loadingLiveRoomUiState(initialIsFollowing: Boolean? = null): LiveRoomUiState {
    return LiveRoomUiState(
        isLoading = true,
        isFollowing = initialIsFollowing ?: false,
        isFollowStatusKnown = initialIsFollowing != null
    )
}

private fun qualityPreferenceLevel(name: String): Int? {
    val normalizedName = name.lowercase()
    return when {
        listOf("原画", "蓝光", "4k", "2160p", "source", "origin")
            .any(normalizedName::contains) -> 1
        listOf("超清", "1080p").any(normalizedName::contains) -> 2
        listOf("高清", "720p").any(normalizedName::contains) -> 3
        listOf("标清", "480p").any(normalizedName::contains) -> 4
        listOf("流畅", "360p", "240p", "180p").any(normalizedName::contains) -> 5
        else -> null
    }
}

internal fun selectPreferredQualityIndex(
    qualities: List<LivePlayQuality>,
    preferredLevel: Int
): Int {
    if (qualities.isEmpty() || preferredLevel == 0) return 0
    if (preferredLevel !in 1..5) return 0

    val rankedQualities = qualities.mapIndexedNotNull { index, quality ->
        qualityPreferenceLevel(quality.quality)?.let { level -> Triple(index, level, quality) }
    }

    rankedQualities.firstOrNull { (_, level) -> level == preferredLevel }
        ?.let { return it.first }

    rankedQualities
        .filter { (_, level) -> level > preferredLevel }
        .minWithOrNull(compareBy<Triple<Int, Int, LivePlayQuality>> { it.second }.thenBy { it.first })
        ?.let { return it.first }

    rankedQualities
        .filter { (_, level) -> level < preferredLevel }
        .maxWithOrNull(compareBy<Triple<Int, Int, LivePlayQuality>> { it.second }.thenBy { it.first })
        ?.let { return it.first }

    return if (preferredLevel == 1) 0 else qualities.lastIndex
}

private fun LiveRoomUiState.asLoadingStatePreservingFollow(): LiveRoomUiState {
    return loadingLiveRoomUiState(
        initialIsFollowing = if (isFollowStatusKnown) isFollowing else null
    )
}

@HiltViewModel
class LiveRoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sites: Set<@JvmSuppressWildcards LiveSite>,
    private val followRepository: FollowRepository,
    private val historyRepository: HistoryRepository,
    private val shieldRepository: ShieldRepository,
    val settingsRepository: SettingsRepository,
    @param:ApplicationContext private val appContext: Context
) : ViewModel(), LivePlaybackSessionBinding {

    var roomId: String = savedStateHandle.get<String>("roomId") ?: ""
        private set
    var siteId: String = savedStateHandle.get<String>("siteId") ?: ""
        private set

    private val _uiState = MutableStateFlow(LiveRoomUiState())
    val uiState: StateFlow<LiveRoomUiState> = _uiState.asStateFlow()

    private val _superChats = MutableStateFlow<List<LiveSuperChatMessage>>(emptyList())
    val superChats: StateFlow<List<LiveSuperChatMessage>> = _superChats.asStateFlow()

    /**
     * High-frequency online counter kept off [uiState] so ONLINE ticks do not recompose
     * room chrome that only cares about detail/qualities/follow.
     */
    private val _onlineCount = MutableStateFlow(0)
    val onlineCount: StateFlow<Int> = _onlineCount.asStateFlow()

    // Keep last N messages for the chat panel.
    private val maxMessages = 200

    fun removeExpiredSuperChats() {
        val now = System.currentTimeMillis()
        _superChats.update { list ->
            activeSuperChats(list, now).sortedBy { it.endTime }
        }
    }

    private fun appendSuperChats(items: Iterable<LiveSuperChatMessage>) {
        val now = System.currentTimeMillis()
        _superChats.update { current ->
            mergeActiveSuperChats(
                current = current,
                incoming = items,
                nowMillis = now
            )
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
    private data class QueuedLiveMessage(
        val route: Pair<String, String>,
        val message: LiveMessage
    )

    private val _messages = MutableSharedFlow<QueuedLiveMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Validate in each consumer, after its queue is drained. Filtering only before emission
    // still lets a suspended overlay collector deliver the previous connection after refresh.
    @OptIn(kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class)
    val newDanmakuMessages: SharedFlow<LiveMessage> = object : SharedFlow<LiveMessage> {
        override val replayCache: List<LiveMessage>
            get() = _messages.replayCache.filter { isActiveRoute(it.route) }.map { it.message }

        override suspend fun collect(collector: FlowCollector<LiveMessage>): Nothing {
            _messages.collect { queued ->
                if (isActiveRoute(queued.route)) collector.emit(queued.message)
            }
        }
    }

    /**
     * Non-fatal danmaku notice, shown inside the chat panel.
     *
     * A danmaku failure must not enter [LiveRoomUiState.error]: the stream is still playing and
     * the room is still usable, so a room-level error would be wrong. But it must not be silent
     * either — before this existed, a failed handshake produced nothing but a log line and the
     * user just saw a chat panel that never filled.
     */
    private val _danmakuNotice = MutableStateFlow<String?>(null)
    val danmakuNotice: StateFlow<String?> = _danmakuNotice.asStateFlow()

    /**
     * Independent scope for cleanup work that must run after [viewModelScope] is
     * cancelled in [onCleared]. A [SupervisorJob] prevents a failure in one child
     * (e.g. danmaku stop) from cancelling siblings, and the scope is GC'd with the
     * ViewModel.
     */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val liveMessageShieldConfig = MutableStateFlow(LiveMessageShieldConfig())

    @Volatile private var currentSite: LiveSite? = null
    private var currentDanmaku: LiveDanmakuSession? = null
    @Volatile private var activeRoute: Pair<String, String>? = null
    private var loadRoomJob: Job? = null
    private var followStatusJob: Job? = null
    private var danmakuStartJob: Job? = null
    private var chatSnapshotPublishJob: Job? = null
    private var playbackRequestJob: Job? = null
    private val playbackRequestGate = LatestPlaybackRequestGate()
    /**
     * Playback port owned by [com.mylive.app.ui.screen.room.player.LivePlaybackSession].
     * ViewModel must not release the engine.
     */
    private var playbackEngine: LivePlaybackEngine? = null
    private var roomOpenedAtMillis: Long? = null

    private var pendingPlaybackOrigin = PlaybackRequestOrigin.UserSelection
    private var pendingPlaybackPauseGeneration: Long? = null
    private var playbackSetupError: String? = null

    private fun showPlaybackError(message: String) {
        playbackSetupError = message
        playbackEngine?.showError(message)
    }

    override fun bindPlaybackEngine(engine: LivePlaybackEngine) {
        if (playbackEngine !== engine) {
            roomOpenedAtMillis?.let(engine::beginRoomLoad)
            // A route may start before its Session exists. Bind its pause boundary now,
            // before detail/quality/URL acquisition or engine-ready callbacks can finish.
            pendingPlaybackPauseGeneration = engine.playbackPauseGeneration
        }
        playbackEngine = engine
        Timber.d("Playback engine bound")
    }

    override fun unbindPlaybackEngine(engine: LivePlaybackEngine) {
        if (playbackEngine === engine) {
            playbackEngine = null
            Timber.d("Playback engine unbound")
        }
    }

    /**
     * Called when the playback session is ready.
     * If qualities were already loaded but player wasn't ready, triggers playback now.
     */
    override fun onPlaybackEngineReady() {
        playbackSetupError?.let { message ->
            playbackEngine?.showError(message)
            return
        }
        val detail = _uiState.value.detail ?: return
        val qualities = _uiState.value.playQualities
        val route = activeRoute ?: return
        if (qualities.isNotEmpty() && playbackEngine != null) {
            Timber.d("Player ready, starting playback with ${qualities.size} qualities")
            requestPlayback(detail, qualities[_uiState.value.currentQualityIndex], route, pendingPlaybackOrigin)
        }
    }

    init {
        startRuntimeCollectors()
        if (roomId.isNotEmpty()) {
            openRoute(roomId, siteId)
        }
    }

    fun openRoute(
        roomId: String,
        siteId: String,
        initialIsFollowing: Boolean? = null,
        preferredQuality: LivePlayQuality? = null,
        origin: PlaybackRequestOrigin = PlaybackRequestOrigin.UserSelection
    ) {
        val nextRoomId = roomId.trim()
        val nextSiteId = siteId.trim()
        playbackSetupError = null
        pendingPlaybackOrigin = origin
        // Capture before any room-detail or quality suspension, including manual refresh.
        pendingPlaybackPauseGeneration = playbackEngine?.playbackPauseGeneration
        if (nextRoomId.isEmpty()) {
            activeRoute = null
            loadRoomJob?.cancel()
            followStatusJob?.cancel()
            cancelPlaybackRequest()
            stopCurrentDanmaku()
            this.roomId = ""
            this.siteId = nextSiteId
            currentSite = null
            playbackEngine?.stop()
            _uiState.value = LiveRoomUiState(isLoading = false, error = "直播间参数缺失")
            showPlaybackError("直播间参数缺失")
            return
        }

        val nextRoute = nextSiteId to nextRoomId
        if (origin == PlaybackRequestOrigin.UserSelection || roomOpenedAtMillis == null) {
            roomOpenedAtMillis = android.os.SystemClock.elapsedRealtime()
            playbackEngine?.beginRoomLoad(checkNotNull(roomOpenedAtMillis))
        }
        activeRoute = nextRoute
        this.roomId = nextRoomId
        this.siteId = nextSiteId
        _uiState.value = loadingLiveRoomUiState(initialIsFollowing)
        currentSite = null
        if (origin == PlaybackRequestOrigin.SourceRecovery) {
            playbackEngine?.stopForSourceRecovery()
        } else {
            playbackEngine?.stop()
        }
        clearChatMessages()
        _danmakuNotice.value = null
        _superChats.value = emptyList()
        _onlineCount.value = 0

        cancelPlaybackRequest()
        stopCurrentDanmaku()

        loadRoomJob?.cancel()
        followStatusJob?.cancel()
        if (nextSiteId.isNotEmpty()) {
            observeCurrentRoomFollowStatus(nextSiteId, nextRoomId, nextRoute)
        }
        loadRoomJob = viewModelScope.launch {
            loadRoomDetail(nextRoute, preferredQuality, origin)
        }
    }

    private fun isActiveRoute(route: Pair<String, String>): Boolean {
        // Each open/refresh owns a new token, including two attempts for the same room.
        return activeRoute === route
    }

    private fun stopCurrentDanmaku() {
        danmakuStartJob?.cancel()
        danmakuStartJob = null
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
    }

    private fun startRuntimeCollectors() {
        viewModelScope.launch {
            combine(
                settingsRepository.danmuShieldEnable,
                settingsRepository.danmuKeywordShieldEnable,
                settingsRepository.danmuUserShieldEnable,
                shieldRepository.getAllShields()
            ) { shieldEnabled, keywordShieldEnabled, userShieldEnabled, shields ->
                LiveMessageShieldConfig(
                    shieldValues = shields.map { it.value },
                    shieldEnabled = shieldEnabled,
                    keywordShieldEnabled = keywordShieldEnabled,
                    userShieldEnabled = userShieldEnabled
                )
            }.collect { config ->
                liveMessageShieldConfig.value = config
            }
        }

        viewModelScope.launch {
            _messages.collect { queued ->
                if (!isActiveRoute(queued.route)) return@collect
                val message = queued.message
                if (message.type == LiveMessageType.ONLINE) {
                    _onlineCount.value = message.onlineCount ?: 0
                } else {
                    if (message.type == LiveMessageType.SUPER_CHAT) {
                        message.superChatMessage?.let { sc ->
                            appendSuperChats(listOf(sc))
                        }
                    }
                    liveMessageBuffer.append(message)
                    scheduleChatSnapshotPublication()
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                removeExpiredSuperChats()
                kotlinx.coroutines.delay(1000L)
            }
        }

    }

    private fun scheduleChatSnapshotPublication() {
        if (chatSnapshotPublishJob?.isActive == true) return
        chatSnapshotPublishJob = viewModelScope.launch {
            kotlinx.coroutines.delay(CHAT_SNAPSHOT_PUBLICATION_INTERVAL_MILLIS)
            _danmakuMessages.value = liveMessageBuffer.snapshot()
        }
    }

    private fun clearChatMessages() {
        chatSnapshotPublishJob?.cancel()
        chatSnapshotPublishJob = null
        _danmakuMessages.value = liveMessageBuffer.clear()
    }

    private suspend fun loadRoomDetail(
        route: Pair<String, String>,
        preferredQuality: LivePlayQuality?,
        origin: PlaybackRequestOrigin
    ) {
        if (!isActiveRoute(route)) return
        val routeSiteId = route.first
        val routeRoomId = route.second
        if (!isActiveRoute(route)) return
        _uiState.update { it.asLoadingStatePreservingFollow() }
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
                showPlaybackError("未找到直播间")
                return
            }

            currentSite = site
            val isFollowing = followRepository.isFollowing(site.id, routeRoomId)
            if (isFollowing && site.id == "douyin") {
                try {
                    followRepository.rememberCanonicalRoomId(site.id, routeRoomId, detail.roomId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.w(error, "Failed to remember canonical follow room")
                }
            }

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
                isFollowing = isFollowing,
                isFollowStatusKnown = true
            )
            observeCurrentRoomFollowStatus(site.id, routeRoomId, route)

            refreshSuperChats(detail, route)

            // Load play qualities
            loadPlayQualities(detail, route, preferredQuality, origin)

            if (!isActiveRoute(route)) return

            // Start danmaku
            startDanmaku(detail, route)

            // Notify chat when the room is playing a recorded loop
            if (detail.isRecord) {
                _messages.tryEmit(
                    QueuedLiveMessage(route, LiveMessage(
                        type = LiveMessageType.CHAT,
                        userName = "LiveSysMessage",
                        message = "当前为轮播录像",
                        color = LiveMessageColor.WHITE
                    ))
                )
            }

        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (!isActiveRoute(route)) return
            Timber.e(e, "Failed to load room detail")
            val message = e.message ?: "加载失败"
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = message
            )
            showPlaybackError(message)
        }
    }

    private fun observeCurrentRoomFollowStatus(
        siteId: String,
        roomId: String,
        route: Pair<String, String>
    ) {
        followStatusJob?.cancel()
        followStatusJob = viewModelScope.launch {
            val initialFollowing = followRepository.isFollowing(siteId, roomId)
            if (isActiveRoute(route)) {
                _uiState.update {
                    it.copy(
                        isFollowing = initialFollowing,
                        isFollowStatusKnown = true
                    )
                }
            }
            followRepository.observeFollowing(siteId, roomId).collect { isFollowing ->
                if (!isActiveRoute(route)) return@collect
                _uiState.update {
                    it.copy(
                        isFollowing = isFollowing,
                        isFollowStatusKnown = true
                    )
                }
            }
        }
    }

    private fun refreshSuperChats(detail: LiveRoomDetail, route: Pair<String, String>) {
        val site = currentSite ?: return
        viewModelScope.launch {
            try {
                val items = site.getSuperChatMessage(route.second, detail)
                if (!isActiveRoute(route)) return@launch
                appendSuperChats(items)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.w(e, "Failed to load super chat messages")
            }
        }
    }

    private suspend fun loadPlayQualities(
        detail: LiveRoomDetail,
        route: Pair<String, String>,
        preferredQuality: LivePlayQuality?,
        origin: PlaybackRequestOrigin
    ) {
        if (!isActiveRoute(route)) return
        val site = currentSite ?: return
        try {
            val qualities = site.getPlayQualites(detail)
            if (!isActiveRoute(route)) return
            val preferredLevel = selectLiveRoomPreferredQualityLevel(
                defaultQualityLevel = settingsRepository.qualityLevel.first(),
                cellularQualityLevel = settingsRepository.qualityLevelCellular.first(),
                isCellularNetwork = isCellularNetworkActive()
            )
            val matchIndex = qualities.indexOfFirst {
                preferredQuality != null &&
                    (it.data == preferredQuality.data || it.quality == preferredQuality.quality)
            }.takeIf { it >= 0 } ?: run {
                val preferredIndex = selectPreferredQualityIndex(qualities, preferredLevel)
                val preferredQualityData = qualities.getOrNull(preferredIndex)?.data
                if (preferredQualityData is PlayQualityData.Douyu && settingsRepository.hardwareDecode.first()) {
                    withContext(Dispatchers.Default) {
                        selectHardwareSupportedQualityIndex(qualities, preferredIndex, ::isLiveQualitySupportedByHardware)
                    }
                } else {
                    preferredIndex
                }
            }
            if (!isActiveRoute(route)) return

            _uiState.value = _uiState.value.copy(
                playQualities = qualities,
                currentQualityIndex = matchIndex
            )
            if (qualities.isEmpty()) {
                showPlaybackError("暂无可播放画质")
                return
            }
            if (playbackEngine != null) {
                // Player is ready, play immediately
                requestPlayback(detail, qualities[matchIndex], route, origin)
            } else {
                // Player not ready yet, mark as pending
                Timber.d("Qualities loaded but player not ready, marking as pending")
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (!isActiveRoute(route)) return
            Timber.e(e, "Failed to load play qualities")
            showPlaybackError(e.message ?: "加载播放画质失败")
        }
    }

    private suspend fun playWithQuality(
        detail: LiveRoomDetail,
        quality: LivePlayQuality,
        route: Pair<String, String>,
        requestGeneration: Long,
        pauseGeneration: Long?,
        origin: PlaybackRequestOrigin = PlaybackRequestOrigin.UserSelection
    ) {
        if (!isCurrentPlaybackRequest(route, requestGeneration)) return
        val site = currentSite ?: return
        try {
            val playUrl = site.getPlayUrls(detail, quality)
            if (!isCurrentPlaybackRequest(route, requestGeneration)) return
            _uiState.update { state ->
                val actualIndex = updatedQualityIndexForResolvedPlayback(
                    qualities = state.playQualities,
                    currentIndex = state.currentQualityIndex,
                    requestedQuality = quality,
                    actualQuality = playUrl.actualQuality
                )
                if (actualIndex == state.currentQualityIndex) {
                    state
                } else {
                    state.copy(currentQualityIndex = actualIndex)
                }
            }
            playbackSetupError = null
            playbackEngine?.play(
                source = playUrl,
                origin = origin,
                pauseGeneration = pauseGeneration
            )
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (!isCurrentPlaybackRequest(route, requestGeneration)) return
            Timber.e(e, "Failed to get play URLs")
            showPlaybackError(e.message ?: "获取播放地址失败")
        }
    }

    private fun requestPlayback(
        detail: LiveRoomDetail,
        quality: LivePlayQuality,
        route: Pair<String, String>,
        origin: PlaybackRequestOrigin = PlaybackRequestOrigin.UserSelection
    ) {
        val requestGeneration = playbackRequestGate.begin()
        val pauseGeneration = pendingPlaybackPauseGeneration
        playbackRequestJob?.cancel()
        playbackRequestJob = viewModelScope.launch {
            playWithQuality(
                detail = detail,
                quality = quality,
                route = route,
                requestGeneration = requestGeneration,
                pauseGeneration = pauseGeneration,
                origin = origin
            )
        }
    }

    private fun cancelPlaybackRequest() {
        playbackRequestGate.begin()
        playbackRequestJob?.cancel()
        playbackRequestJob = null
    }

    private fun isCurrentPlaybackRequest(
        route: Pair<String, String>,
        requestGeneration: Long
    ): Boolean {
        return isActiveRoute(route) && playbackRequestGate.isCurrent(requestGeneration)
    }

    fun switchQuality(index: Int) {
        val detail = _uiState.value.detail ?: return
        val qualities = _uiState.value.playQualities
        if (index !in qualities.indices) return
        val route = activeRoute ?: return
        pendingPlaybackOrigin = PlaybackRequestOrigin.UserSelection
        pendingPlaybackPauseGeneration = playbackEngine?.playbackPauseGeneration
        _uiState.value = _uiState.value.copy(currentQualityIndex = index)
        requestPlayback(detail, qualities[index], route)
    }

    fun refreshPlay() {
        reloadPlaybackSource(PlaybackRequestOrigin.UserSelection)
    }

    fun recoverPlaybackAfterSourceFailure(): Boolean {
        return reloadPlaybackSource(PlaybackRequestOrigin.SourceRecovery)
    }

    /** URLs and quality objects can contain expiring platform credentials. Re-resolve both. */
    private fun reloadPlaybackSource(origin: PlaybackRequestOrigin): Boolean {
        val route = activeRoute ?: return false
        val state = _uiState.value
        openRoute(
            roomId = route.second,
            siteId = route.first,
            initialIsFollowing = state.isFollowing.takeIf { state.isFollowStatusKnown },
            preferredQuality = state.playQualities.getOrNull(state.currentQualityIndex),
            origin = origin
        )
        return true
    }

    private fun startDanmaku(detail: LiveRoomDetail, route: Pair<String, String>) {
        if (!isActiveRoute(route)) return
        val site = currentSite ?: return
        val routeRoomId = route.second
        val danmaku = try {
            site.createDanmakuSession(routeRoomId, detail)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to create danmaku")
            if (isActiveRoute(route)) {
                _danmakuNotice.value = DANMAKU_UNAVAILABLE_NOTICE
            }
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
            if (shouldDropMessageForShield(message, route)) return@onMessage
            // Hand off to the serial consumer started in init. tryEmit never fails
            // because the flow is configured with extraBufferCapacity + DROP_OLDEST.
            _messages.tryEmit(QueuedLiveMessage(route, message))
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
            _danmakuNotice.value = null
            Timber.d("Danmaku connected")
        }

        danmakuStartJob = viewModelScope.launch {
            try {
                if (!isActiveRoute(route)) return@launch
                danmaku.start()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                if (!isActiveRoute(route)) return@launch
                Timber.e(e, "Failed to start danmaku")
                _danmakuNotice.value = DANMAKU_UNAVAILABLE_NOTICE
            }
        }
    }

    private fun isCellularNetworkActive(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun shouldDropMessageForShield(message: LiveMessage, route: Pair<String, String>): Boolean {
        val resolvedSiteId = currentSite?.id ?: route.first
        return shouldShieldLiveMessage(
            message = message,
            siteId = resolvedSiteId,
            config = liveMessageShieldConfig.value
        )
    }

    fun toggleFollow() {
        val route = activeRoute ?: return
        val detail = _uiState.value.detail ?: return
        val site = currentSite ?: return
        val routeRoomId = route.second
        val wasFollowing = _uiState.value.isFollowing
        viewModelScope.launch {
            try {
                if (!isActiveRoute(route)) return@launch
                if (wasFollowing) {
                    val follow = followRepository.getFollow(site.id, routeRoomId)
                    if (!isActiveRoute(route)) return@launch
                    if (follow != null) {
                        followRepository.removeFollow(follow.id)
                    }
                } else {
                    if (!isActiveRoute(route)) return@launch
                    followRepository.addFollow(
                        FollowUserEntity(
                            id = "${site.id}_$routeRoomId",
                            roomId = routeRoomId,
                            siteId = site.id,
                            userName = detail.userName,
                            face = detail.userAvatar,
                            addTime = System.currentTimeMillis(),
                            tag = "",
                            isSpecialFollow = false,
                            liveStatus = if (detail.status) 1 else 2,
                            liveStartTime = if (detail.status) System.currentTimeMillis() else null,
                            showTime = if (detail.status) detail.showTime else null,
                            canonicalRoomId = detail.roomId.takeIf { site.id == "douyin" && it.isNotBlank() }
                        )
                    )
                }
                if (!isActiveRoute(route)) return@launch
                _uiState.value = _uiState.value.copy(
                    isFollowing = !wasFollowing,
                    isFollowStatusKnown = true
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle follow")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeRoute = null
        // viewModelScope is already cancelled at this point; use the cleanup scope
        // so the suspend danmaku.stop() can complete.
        cleanupScope.launch {
            try {
                currentDanmaku?.stop()
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop danmaku")
            }
        }
        // playbackEngine is owned by LivePlaybackSession (created from LiveRoomScreen)
        // and released in session.release(), so the ViewModel must not release it here.
    }
}
