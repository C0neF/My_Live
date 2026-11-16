package com.mylive.desktop

import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.LivePlayUrl
import com.mylive.app.core.model.LiveRoomDetail
import com.mylive.app.core.model.LiveSuperChatMessage
import com.mylive.app.core.site.LiveDanmakuSession
import com.mylive.app.core.site.LiveSite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DesktopRoomState(
    val address: RoomAddress? = null,
    val detail: LiveRoomDetail? = null,
    val opening: Boolean = false,
    val error: String? = null,
    val qualities: List<LivePlayQuality> = emptyList(),
    val quality: LivePlayQuality? = null,
    val line: Int = 0,
    val lineCount: Int = 0,
    val chatStatus: String = "未连接"
)

data class ChatEntry(val id: Long, val message: LiveMessage)

/** All room ownership and UI state are confined to [dispatcher] (the Swing EDT in production). */
class DesktopRoomController(
    private val sites: List<LiveSite>,
    private val playback: DesktopPlayback,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val library: DesktopLibrary? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow(DesktopRoomState())
    val state = _state.asStateFlow()
    private val _messages = MutableStateFlow<List<ChatEntry>>(emptyList())
    val messages = _messages.asStateFlow()
    private val _superChats = MutableStateFlow<List<LiveSuperChatMessage>>(emptyList())
    val superChats = _superChats.asStateFlow()
    private var roomJob: Job? = null
    private var playbackJob: Job? = null
    private var generation = 0L
    private var nextMessageId = 0L
    private var source: LivePlayUrl? = null
    private var closed = false

    fun open(address: RoomAddress) {
        check(!closed)
        val site = sites.first { it.id == address.siteId }
        reset()
        playback.beginRoom()
        val token = generation
        _state.value = DesktopRoomState(address = address, opening = true)
        roomJob = scope.launch {
            var chat: LiveDanmakuSession? = null
            val incoming = Channel<LiveMessage>(capacity = 256, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
            try {
                val detail = withContext(ioDispatcher) { site.getRoomDetail(address.roomId) }
                ensureActive()
                if (token != generation) return@launch
                _state.update { it.copy(detail = detail) }
                launch { library?.recordVisit(address, detail) }
                if (!detail.status) {
                    _state.update { it.copy(opening = false, error = "主播暂未开播") }
                    return@launch
                }
                val deduplicator = DesktopMessageDeduplicator()
                coroutineScope {
                // Slow chat handshakes must not delay video URL resolution.
                launch {
                    try {
                        chat = site.createDanmakuSession(address.roomId, detail).also { session ->
                            session.onMessage = { message ->
                                if (message.type == LiveMessageType.CHAT) incoming.trySend(message)
                                else if (message.type == LiveMessageType.SUPER_CHAT) message.superChatMessage?.let { paid ->
                                    scope.launch { if (token == generation) publishSuperChats(listOf(paid)) }
                                }
                            }
                            session.onReady = { scope.launch { if (token == generation) _state.update { it.copy(chatStatus = "已连接") } } }
                            session.onClose = { scope.launch { if (token == generation) _state.update { it.copy(chatStatus = "连接断开，可重新进入") } } }
                        }
                        _state.update { it.copy(chatStatus = "连接中") }
                        withContext(ioDispatcher) { chat!!.start() }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (token == generation) _state.update { it.copy(chatStatus = "弹幕连接失败，可重新进入") }
                    }
                }
                launch {
                    while (true) {
                        val first = incoming.receive()
                        delay(50) // Batch busy rooms without driving the entire UI on each WebSocket message.
                        val batch = mutableListOf(first)
                        while (batch.size < 128) batch += incoming.tryReceive().getOrNull() ?: break
                        if (token == generation) _messages.update { old ->
                            val settings = library?.state?.value?.danmaku ?: DesktopDanmakuPreferences()
                            (old + batch.filter { deduplicator.accepts(it, settings, System.currentTimeMillis()) }
                                .map { ChatEntry(++nextMessageId, it) }).takeLast(200)
                        }
                    }
                }
                launch {
                    while (true) {
                        try {
                            val paid = withContext(ioDispatcher) { site.getSuperChatMessage(address.roomId, detail) }
                            ensureActive(); if (token == generation) publishSuperChats(paid)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Keep current paid messages through transient failures. */ }
                        delay(20_000)
                    }
                }
                val qualities = withContext(ioDispatcher) { site.getPlayQualites(detail) }
                ensureActive()
                if (token != generation) return@coroutineScope
                require(qualities.isNotEmpty()) { "没有可用清晰度" }
                _state.update { it.copy(qualities = qualities) }
                selectQuality(preferredDesktopQuality(qualities, address.siteId, library?.state?.value?.player ?: DesktopPlayerPreferences()), remember = false)
                awaitCancellation()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (token == generation) _state.update { it.copy(opening = false, error = "房间加载失败，请稍后重试或更新账号 Cookie") }
            } finally {
                incoming.close()
                withContext(NonCancellable + ioDispatcher) { runCatching { chat?.stop() } }
            }
        }
    }

    fun selectQuality(quality: LivePlayQuality, remember: Boolean = true) {
        val current = _state.value
        val detail = current.detail ?: return
        val site = sites.first { it.id == current.address?.siteId }
        val token = generation
        playbackJob?.cancel()
        source = null
        _state.update { it.copy(opening = true, error = null, quality = quality, line = 0, lineCount = 0) }
        playbackJob = scope.launch {
            try {
                if (remember && library?.state?.value?.player?.rememberQuality == true) library.updatePlayer {
                    it.copy(qualityBySite = it.qualityBySite + (site.id to quality.quality))
                }
                val resolved = withContext(ioDispatcher) { site.getPlayUrls(detail, quality) }
                ensureActive()
                if (token != generation) return@launch
                require(resolved.urls.isNotEmpty()) { "直播源为空" }
                source = resolved
                val line = openPlaybackLine(playback, resolved)
                ensureActive()
                if (token == generation) _state.update {
                    it.copy(opening = false, quality = resolved.actualQuality ?: quality, line = line, lineCount = resolved.urls.size)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (token == generation) _state.update { it.copy(opening = false, error = "播放失败，请切换清晰度或重试") }
            }
        }
    }

    fun nextLine() {
        val resolved = source ?: return
        if (resolved.urls.size < 2) return
        val token = generation
        playbackJob?.cancel()
        _state.update { it.copy(opening = true, error = null) }
        val preferred = (_state.value.line + 1) % resolved.urls.size
        playbackJob = scope.launch {
            try {
                val line = openPlaybackLine(playback, resolved, preferred)
                if (token == generation) _state.update { it.copy(opening = false, line = line) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (token == generation) _state.update { it.copy(opening = false, error = "切换线路失败，请重试") }
            }
        }
    }

    fun openVideo(uri: String) {
        check(!closed)
        reset()
        playback.beginRoom()
        val token = generation
        _state.value = DesktopRoomState(
            detail = LiveRoomDetail("", "视频播放", "", "本地文件 / 视频链接", status = true), opening = true
        )
        playbackJob = scope.launch {
            try {
                playback.open(uri, emptyMap())
                if (token == generation) _state.update { it.copy(opening = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (token == generation) _state.update { it.copy(opening = false, error = "视频打开失败，请检查链接或文件格式") }
            }
        }
    }

    fun leave() { reset(); _state.value = DesktopRoomState() }

    private fun publishSuperChats(incoming: List<LiveSuperChatMessage>) {
        val now = System.currentTimeMillis() / 1000
        _superChats.value = (_superChats.value + incoming).filter { it.endTime == 0L || it.endTime > now }
            .distinctBy { it.id ?: "${it.userName}\u0000${it.message}\u0000${it.startTime}" }
            .sortedByDescending { it.startTime }.take(50)
    }

    private fun reset() {
        generation++
        roomJob?.cancel()
        playbackJob?.cancel()
        playback.stop()
        source = null
        _messages.value = emptyList()
        _superChats.value = emptyList()
    }

    suspend fun shutdown() {
        if (closed) return
        closed = true
        generation++
        playbackJob?.cancelAndJoin()
        roomJob?.cancelAndJoin()
        scope.cancel()
        playback.close()
    }
}
