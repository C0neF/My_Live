package com.mylive.app.service

import com.mylive.app.core.site.LiveSite
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    /** All callers share one refresh and receive the same transition result. */
    suspend fun refreshAll(): List<FollowStatusTransition> = refreshSingleFlight.run {
        _isRefreshing.value = true
        try {
            performRefresh()
        } finally {
            _isRefreshing.value = false
        }
    }

    private suspend fun performRefresh(): List<FollowStatusTransition> = coroutineScope {
        val follows = followRepository.getAllFollows().first()
        val semaphore = Semaphore(
            permits = resolveFollowUpdateConcurrency(settingsRepository.updateFollowThreadCount.first())
        )
        follows.map { follow ->
            async(Dispatchers.IO) {
                semaphore.withPermit { refreshOne(follow) }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun refreshOne(follow: FollowUserEntity): FollowStatusTransition? {
        return try {
            val site = sites.find { it.id == follow.siteId } ?: return null
            val isLive = site.getLiveStatus(follow.roomId)
            val newStatus = if (isLive) 1 else 2
            val showTime = if (isLive) {
                try {
                    site.getRoomDetail(follow.roomId).showTime
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            val changed = follow.liveStatus != newStatus || (isLive && follow.showTime != showTime)
            if (!changed) return null

            followRepository.updateLiveStatus(
                id = follow.id,
                status = newStatus,
                startTime = if (isLive) System.currentTimeMillis() else null,
                showTime = showTime
            )
            FollowStatusTransition(
                follow = follow,
                previousStatus = follow.liveStatus,
                currentStatus = newStatus
            )
        } catch (error: Exception) {
            Timber.w(error, "Failed to update follow status for ${follow.userName}")
            null
        }
    }
}
