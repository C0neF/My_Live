package com.mylive.app.service

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpdateScheduleCoordinatorTest {

    @Test
    fun startIsIdempotentAndOnlyAppliesChangedConfiguration() = runBlocking {
        val enabled = MutableStateFlow(true)
        val intervalMinutes = MutableStateFlow(60)
        val calls = Channel<Pair<Boolean, Int>>(capacity = Channel.UNLIMITED)
        val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = FollowUpdateScheduleCoordinator(
            enabledFlow = enabled,
            intervalMinutesFlow = intervalMinutes,
            scope = coordinatorScope,
            schedule = { isEnabled, interval ->
                calls.trySend(isEnabled to interval).getOrThrow()
            }
        )

        coordinator.start()
        coordinator.start()

        assertEquals(true to 60, withTimeout(1_000) { calls.receive() })
        yield()
        assertTrue(calls.tryReceive().isFailure)

        intervalMinutes.value = 120
        assertEquals(true to 120, withTimeout(1_000) { calls.receive() })

        coordinator.start()
        yield()
        assertTrue(calls.tryReceive().isFailure)
        coordinatorScope.cancel()
    }
}
