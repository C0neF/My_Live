package com.mylive.app.service

import com.mylive.app.data.local.entity.FollowUserEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
}
