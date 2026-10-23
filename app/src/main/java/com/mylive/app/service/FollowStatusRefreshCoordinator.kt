package com.mylive.app.service

import com.mylive.app.core.site.LiveSite
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.playbackRoomId
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

internal class SuspendSingleFlight<T> {
    private data class Lease<T>(
        val result: CompletableDeferred<T>,
        val owner: Boolean
    )

    private val lock = Any()
    private var inFlight: CompletableDeferred<T>? = null

    suspend fun run(block: suspend () -> T): T {
        val lease = synchronized(lock) {
            val existing = inFlight
            if (existing != null) {
                Lease(existing, owner = false)
            } else {
                val result = CompletableDeferred<T>()
                inFlight = result
                Lease(result, owner = true)
            }
        }
        if (!lease.owner) return lease.result.await()

        try {
            val value = block()
            lease.result.complete(value)
            return value
        } catch (error: Throwable) {
            lease.result.completeExceptionally(error)
            throw error
        } finally {
            synchronized(lock) {
                if (inFlight === lease.result) inFlight = null
            }
        }
    }
}

data class FollowStatusTransition(
    val follow: FollowUserEntity,
    val previousStatus: Int,
    val currentStatus: Int
) {
    val becameLive: Boolean
        get() = previousStatus != 1 && currentStatus == 1
}

@Singleton
class FollowStatusRefreshCoordinator @Inject constructor(
    private val followRepository: FollowRepository,
    private val settingsRepository: SettingsRepository,
    private val sites: Set<@JvmSuppressWildcards LiveSite>
) {
    private val refreshSingleFlight = SuspendSingleFlight<List<FollowStatusTransition>>()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _entries = MutableStateFlow<Map<String, FollowRefreshEntry>>(emptyMap())
    val entries: StateFlow<Map<String, FollowRefreshEntry>> = _entries.asStateFlow()
    private val _progress = MutableStateFlow(FollowRefreshProgress())
    val progress: StateFlow<FollowRefreshProgress> = _progress.asStateFlow()

    /** All callers share one refresh and receive the same transition result. */
    suspend fun refreshAll(): List<FollowStatusTransition> = refreshSingleFlight.run {
        _isRefreshing.value = true
        _progress.value = FollowRefreshProgress(running = true)
        try {
            performRefresh()
        } catch (cancelled: CancellationException) {
            _progress.update { it.copy(cancelled = true) }
            throw cancelled
        } catch (error: Exception) {
            _progress.update { it.copy(error = "关注状态刷新失败，请重试") }
            throw error
        } finally {
            _entries.update { entries -> entries.mapValues { (_, entry) -> entry.copy(refreshing = false) } }
            _progress.update { it.copy(running = false) }
            _isRefreshing.value = false
        }
    }

    private suspend fun performRefresh(): List<FollowStatusTransition> = coroutineScope {
        val follows = followRepository.getAllFollows().first()
        val previous = _entries.value
        _entries.value = follows.associate { follow ->
            follow.id to (previous[follow.id] ?: FollowRefreshEntry()).copy(refreshing = true)
        }
        _progress.value = FollowRefreshProgress(total = follows.size, running = true)
        val semaphore = Semaphore(
            permits = resolveFollowUpdateConcurrency(settingsRepository.updateFollowThreadCount.first())
        )
        val results = Channel<FollowRefreshOutcome>(capacity = 8)
        val producer = async {
            try {
                follows.map { follow ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit { results.send(refreshOne(follow)) }
                    }
                }.awaitAll()
            } finally {
                results.close()
            }
        }

        val pending = mutableListOf<FollowRefreshOutcome>()
        val transitions = mutableListOf<FollowStatusTransition>()
        suspend fun flush() {
            if (pending.isEmpty()) return
            val batch = pending.toList()
            pending.clear()
            try {
                followRepository.updateLiveStatuses(batch.mapNotNull { it.change?.updatedFollow })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                recordResults(batch.map { it.copy(failed = true, change = null) })
                throw error
            }
            recordResults(batch)
            transitions += batch.mapNotNull { it.change?.transition }
        }

        while (true) {
            // Flush a sparse batch promptly even when the next platform request is slow.
            val received = if (pending.isEmpty()) results.receiveCatching()
                else withTimeoutOrNull(150L) { results.receiveCatching() }
            if (received == null) {
                flush()
            } else if (received.isClosed) {
                producer.await()
                flush()
                break
            } else {
                pending += received.getOrThrow()
                if (pending.size >= 8) flush()
            }
        }
        transitions
    }

    private fun recordResults(results: List<FollowRefreshOutcome>) {
        _entries.update { previous ->
            previous.toMutableMap().apply {
                results.forEach { result ->
                    val old = get(result.id) ?: FollowRefreshEntry()
                    put(result.id, old.copy(
                        refreshing = false,
                        lastAttemptAtMillis = result.checkedAtMillis,
                        lastSuccessAtMillis = if (result.failed) old.lastSuccessAtMillis else result.checkedAtMillis,
                        failed = result.failed
                    ))
                }
            }
        }
        _progress.update { it.copy(
            completed = it.completed + results.size,
            failed = it.failed + results.count { result -> result.failed }
        ) }
    }

    private suspend fun refreshOne(follow: FollowUserEntity): FollowRefreshOutcome {
        return try {
            val site = sites.find { it.id == follow.siteId }
                ?: return FollowRefreshOutcome(follow.id, System.currentTimeMillis(), failed = true)
            val presence = site.getFollowPresence(follow.playbackRoomId)
            val isLive = presence.isLive
            val newStatus = if (isLive) 1 else 2
            val showTime = presence.showTime.takeIf { isLive }
            val statusChanged = follow.liveStatus != newStatus || (isLive && follow.showTime != showTime)
            val canonicalRoomId = if (follow.siteId == "douyin")
                presence.canonicalRoomId?.trim()?.takeIf { it.isNotEmpty() } ?: follow.canonicalRoomId
                else follow.canonicalRoomId
            if (!statusChanged && canonicalRoomId == follow.canonicalRoomId)
                return FollowRefreshOutcome(follow.id, System.currentTimeMillis())

            FollowRefreshOutcome(follow.id, System.currentTimeMillis(), change = ResolvedFollowStatus(
                updatedFollow = follow.copy(
                    liveStatus = newStatus,
                    liveStartTime = if (statusChanged) { if (isLive) System.currentTimeMillis() else null } else follow.liveStartTime,
                    showTime = if (statusChanged) showTime else follow.showTime,
                    canonicalRoomId = canonicalRoomId
                ),
                transition = if (statusChanged) FollowStatusTransition(
                    follow = follow.copy(canonicalRoomId = canonicalRoomId),
                    previousStatus = follow.liveStatus,
                    currentStatus = newStatus
                ) else null
            ))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Failed to update follow status for ${follow.userName}")
            FollowRefreshOutcome(follow.id, System.currentTimeMillis(), failed = true)
        }
    }
}

private data class FollowRefreshOutcome(
    val id: String,
    val checkedAtMillis: Long,
    val failed: Boolean = false,
    val change: ResolvedFollowStatus? = null
)

private data class ResolvedFollowStatus(
    val updatedFollow: FollowUserEntity,
    val transition: FollowStatusTransition?
)
