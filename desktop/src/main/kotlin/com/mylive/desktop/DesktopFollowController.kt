package com.mylive.desktop

import com.mylive.app.core.site.FollowPresence
import com.mylive.app.core.site.LiveSite
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class FollowRefreshState(val refreshing: Boolean = false, val completed: Int = 0, val total: Int = 0, val error: String? = null)

class DesktopFollowController(
    private val library: DesktopLibrary, private val sites: List<LiveSite>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main, private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow(FollowRefreshState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var autoJob: Job? = null
    private var generation = 0L

    fun startAutoRefresh() {
        if (autoJob != null) return
        autoJob = scope.launch {
            library.state.map { Triple(it.loaded && it.writable, it.followOptions, it.follows.map(SavedRoom::key)) }
                .distinctUntilChanged().collectLatest { (ready, preferences, keys) ->
                    if (!ready || !preferences.autoRefresh || keys.isEmpty()) return@collectLatest
                    while (isActive) {
                        if (!state.value.refreshing) refresh()
                        delay(preferences.intervalSeconds * 1000L)
                    }
                }
        }
    }

    fun refresh() {
        val current = library.state.value
        if (!current.loaded || !current.writable) return
        refreshJob?.cancel()
        val token = ++generation
        val follows = current.follows
        _state.value = FollowRefreshState(refreshing = follows.isNotEmpty(), total = follows.size)
        if (follows.isEmpty()) return
        refreshJob = scope.launch {
            val permits = Semaphore(4)
            try {
                val results = follows.map { room -> async {
                    permits.withPermit {
                        val presence = try {
                            withContext(io) { sites.first { it.id == room.siteId }.getFollowPresence(room.roomId) }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                        ensureActive()
                        if (token == generation) _state.update { it.copy(completed = it.completed + 1) }
                        room.key to presence
                    }
                } }.awaitAll()
                ensureActive()
                if (token != generation) return@launch
                val successful = results.mapNotNull { (key, presence) -> presence?.let { key to it } }.toMap()
                val saved = successful.isEmpty() || library.applyPresences(successful)
                ensureActive()
                if (token == generation) _state.value = _state.value.copy(refreshing = false, error = when {
                    !saved -> "状态已获取，但本地保存失败，请重试。"
                    successful.size < follows.size -> "${follows.size - successful.size} 个房间刷新失败，已保留上次状态。"
                    else -> null
                })
            } catch (cancelled: CancellationException) { throw cancelled }
            finally { if (token == generation) _state.update { it.copy(refreshing = false) } }
        }
    }

    fun close() { generation++; scope.cancel() }
}
