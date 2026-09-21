package com.mylive.app.service

import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.core.site.FollowPresence
import com.mylive.app.core.site.LiveSite
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class FollowStatusRefreshCoordinatorTest {

    @Test
    fun concurrentCallersShareOneRefreshResult() = runBlocking {
        val singleFlight = SuspendSingleFlight<String>()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val executions = AtomicInteger(0)

        suspend fun refresh(): String = singleFlight.run {
            executions.incrementAndGet()
            entered.complete(Unit)
            release.await()
            "updated"
        }

        val first = async(Dispatchers.Default) { refresh() }
        entered.await()
        val second = async(Dispatchers.Default) { refresh() }
        yield()

        assertEquals(1, executions.get())
        release.complete(Unit)
        assertEquals("updated", first.await())
        assertEquals("updated", second.await())
        assertEquals(1, executions.get())
    }

    @Test
    fun transitionReportsOnlyOfflineToLiveAsBecameLive() {
        val follow = FollowUserEntity(
            id = "id",
            siteId = "huya",
            roomId = "1",
            userName = "anchor",
            face = "",
            addTime = 0L
        )

        assertTrue(FollowStatusTransition(follow, previousStatus = 2, currentStatus = 1).becameLive)
        assertFalse(FollowStatusTransition(follow, previousStatus = 1, currentStatus = 1).becameLive)
        assertFalse(FollowStatusTransition(follow, previousStatus = 0, currentStatus = 2).becameLive)
    }

    @Test
    fun refreshUsesOnePresenceQueryAndOneBatchWrite() = runBlocking {
        val follow = FollowUserEntity(
            id = "douyin_1",
            siteId = "douyin",
            roomId = "1",
            userName = "anchor",
            face = "",
            addTime = 0L,
            liveStatus = 2
        )
        val site = mockk<LiveSite>()
        every { site.id } returns "douyin"
        coEvery { site.getFollowPresence("1") } returns FollowPresence(
            isLive = true,
            showTime = "123"
        )
        val repository = mockk<FollowRepository>()
        every { repository.getAllFollows() } returns flowOf(listOf(follow))
        coEvery { repository.updateLiveStatuses(any()) } returns Unit
        val settings = mockk<SettingsRepository>()
        every { settings.updateFollowThreadCount } returns flowOf(1)

        val transitions = FollowStatusRefreshCoordinator(
            followRepository = repository,
            settingsRepository = settings,
            sites = setOf(site)
        ).refreshAll()

        assertEquals(1, transitions.size)
        assertTrue(transitions.single().becameLive)
        coVerify(exactly = 1) { site.getFollowPresence("1") }
        coVerify(exactly = 0) { site.getLiveStatus(any()) }
        coVerify(exactly = 0) { site.getRoomDetail(any()) }
        coVerify(exactly = 1) {
            repository.updateLiveStatuses(match { users ->
                users.single().liveStatus == 1 && users.single().showTime == "123"
            })
        }
    }

    @Test
    fun refreshMovesAPreviouslyLiveFollowToOfflineWhenPresenceIsFalse() = runBlocking {
        val follow = FollowUserEntity(
            id = "douyin_1",
            siteId = "douyin",
            roomId = "1",
            userName = "anchor",
            face = "",
            addTime = 0L,
            liveStatus = 1,
            showTime = "123"
        )
        val site = mockk<LiveSite>()
        every { site.id } returns "douyin"
        coEvery { site.getFollowPresence("1") } returns FollowPresence(isLive = false)
        val repository = mockk<FollowRepository>()
        every { repository.getAllFollows() } returns flowOf(listOf(follow))
        coEvery { repository.updateLiveStatuses(any()) } returns Unit
        val settings = mockk<SettingsRepository>()
        every { settings.updateFollowThreadCount } returns flowOf(1)

        val transitions = FollowStatusRefreshCoordinator(
            followRepository = repository,
            settingsRepository = settings,
            sites = setOf(site)
        ).refreshAll()

        assertEquals(1, transitions.size)
        assertEquals(1, transitions.single().previousStatus)
        assertEquals(2, transitions.single().currentStatus)
        coVerify(exactly = 1) {
            repository.updateLiveStatuses(match { users ->
                users.single().liveStatus == 2 && users.single().showTime == null
            })
        }
    }

    @Test
    fun refreshDoesNotSwallowCancellation() {
        runBlocking {
        val follow = FollowUserEntity(
            id = "douyin_1",
            siteId = "douyin",
            roomId = "1",
            userName = "anchor",
            face = "",
            addTime = 0L
        )
        val site = mockk<LiveSite>()
        every { site.id } returns "douyin"
        coEvery { site.getFollowPresence("1") } throws CancellationException("cancelled")
        val repository = mockk<FollowRepository>()
        every { repository.getAllFollows() } returns flowOf(listOf(follow))
        val settings = mockk<SettingsRepository>()
        every { settings.updateFollowThreadCount } returns flowOf(1)

            try {
                FollowStatusRefreshCoordinator(repository, settings, setOf(site)).refreshAll()
                throw AssertionError("Expected refresh cancellation")
            } catch (_: CancellationException) {
                Unit
            }
        }
    }
}
