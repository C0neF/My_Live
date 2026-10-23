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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
        // Register the waiter before releasing the owner. Yielding this runBlocking thread
        // does not guarantee that an async task on Dispatchers.Default has even started.
        val second = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { refresh() }

        assertEquals(1, executions.get())
        assertFalse(second.isCompleted)
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

    @Test
    fun fastResultIsPublishedWhileAnotherRequestIsStillWaiting() = runBlocking {
        val slowEntered = CompletableDeferred<Unit>()
        val releaseSlow = CompletableDeferred<Unit>()
        val published = CompletableDeferred<List<FollowUserEntity>>()
        val repository = mockk<FollowRepository>()
        every { repository.getAllFollows() } returns flowOf(listOf(follow("fast"), follow("slow")))
        coEvery { repository.updateLiveStatuses(any()) } coAnswers {
            val batch = firstArg<List<FollowUserEntity>>()
            if (batch.any { it.roomId == "fast" }) published.complete(batch)
        }
        val site = mockk<LiveSite>()
        every { site.id } returns "douyin"
        coEvery { site.getFollowPresence("fast") } returns FollowPresence(true)
        coEvery { site.getFollowPresence("slow") } coAnswers {
            slowEntered.complete(Unit)
            releaseSlow.await()
            FollowPresence(false)
        }
        val coordinator = coordinator(repository, site, concurrency = 2)
        val refresh = async { coordinator.refreshAll() }
        try {
            withTimeout(3000) { slowEntered.await() }
            val firstBatch = withTimeout(3000) { published.await() }
            assertEquals(listOf("fast"), firstBatch.map { it.roomId })
            withTimeout(3000) { coordinator.progress.first { it.completed == 1 } }
            assertTrue(coordinator.entries.value.getValue("douyin_slow").refreshing)
            assertFalse(coordinator.entries.value.getValue("douyin_fast").refreshing)
            assertFalse(refresh.isCompleted)
        } finally {
            releaseSlow.complete(Unit)
            refresh.await()
        }
        assertEquals(2, coordinator.progress.value.completed)
        assertFalse(coordinator.progress.value.running)
    }

    @Test
    fun unchangedSuccessIsFreshAndLaterFailureKeepsItsSuccessTimestamp() = runBlocking {
        val repository = mockk<FollowRepository>()
        every { repository.getAllFollows() } returns flowOf(listOf(follow("1")))
        coEvery { repository.updateLiveStatuses(any()) } returns Unit
        val site = mockk<LiveSite>()
        every { site.id } returns "douyin"
        coEvery { site.getFollowPresence("1") } returns FollowPresence(false)
        val coordinator = coordinator(repository, site)
        assertTrue(coordinator.refreshAll().isEmpty())
        val first = coordinator.entries.value.getValue("douyin_1")
        assertTrue(first.lastSuccessAtMillis != null)
        assertFalse(first.failed)

        coEvery { site.getFollowPresence("1") } throws IllegalStateException("offline network")
        assertTrue(coordinator.refreshAll().isEmpty())
        val failed = coordinator.entries.value.getValue("douyin_1")
        assertTrue(failed.failed)
        assertEquals(first.lastSuccessAtMillis, failed.lastSuccessAtMillis)
        assertEquals(1, coordinator.progress.value.failed)
        coVerify(exactly = 2) { repository.updateLiveStatuses(emptyList()) }
    }

    @Test
    fun databaseFailureDoesNotMarkResultsAsFresh() = runBlocking {
        val repository = mockk<FollowRepository>()
        every { repository.getAllFollows() } returns flowOf(listOf(follow("1")))
        coEvery { repository.updateLiveStatuses(any()) } throws IllegalStateException("write failed")
        val site = mockk<LiveSite>()
        every { site.id } returns "douyin"
        coEvery { site.getFollowPresence("1") } returns FollowPresence(true)
        val coordinator = coordinator(repository, site)
        try {
            coordinator.refreshAll()
            throw AssertionError("Expected database failure")
        } catch (_: IllegalStateException) {
            assertTrue(coordinator.entries.value.getValue("douyin_1").failed)
            assertEquals(null, coordinator.entries.value.getValue("douyin_1").lastSuccessAtMillis)
            assertTrue(coordinator.progress.value.error != null)
            assertFalse(coordinator.isRefreshing.value)
        }
    }

    @Test
    fun cancelledRefreshResetsIndicatorsAndRemovedFollowsArePruned() = runBlocking {
        val follows = MutableStateFlow(listOf(follow("1")))
        val repository = mockk<FollowRepository>()
        every { repository.getAllFollows() } returns follows
        coEvery { repository.updateLiveStatuses(any()) } returns Unit
        val site = mockk<LiveSite>()
        every { site.id } returns "douyin"
        coEvery { site.getFollowPresence("1") } throws CancellationException("cancelled")
        val coordinator = coordinator(repository, site)
        try {
            coordinator.refreshAll()
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(coordinator.progress.value.cancelled)
            assertFalse(coordinator.progress.value.running)
            assertFalse(coordinator.entries.value.getValue("douyin_1").refreshing)
        }
        follows.value = emptyList()
        coordinator.refreshAll()
        assertTrue(coordinator.entries.value.isEmpty())
        assertFalse(coordinator.progress.value.cancelled)
    }

    private fun follow(roomId: String) = FollowUserEntity(
        id = "douyin_$roomId", siteId = "douyin", roomId = roomId,
        userName = roomId, face = "", addTime = 0L, liveStatus = 2
    )

    private fun coordinator(repository: FollowRepository, site: LiveSite, concurrency: Int = 1) =
        FollowStatusRefreshCoordinator(repository, mockk<SettingsRepository> {
            every { updateFollowThreadCount } returns flowOf(concurrency)
        }, setOf(site))
}
