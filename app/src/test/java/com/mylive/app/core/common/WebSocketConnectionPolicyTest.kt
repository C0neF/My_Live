package com.mylive.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebSocketConnectionPolicyTest {

    @Test
    fun failurePrefersNextBackupUrlBeforeReconnect() {
        assertEquals(
            WebSocketFailureAction.TRY_NEXT_URL,
            webSocketFailureAction(
                intentionalClose = false,
                currentUrlIndex = 0,
                urlCount = 3,
                reconnectAttemptsAlready = 0,
                maxReconnectAttempts = 5
            )
        )
    }

    @Test
    fun failureSchedulesReconnectAfterLastUrl() {
        assertEquals(
            WebSocketFailureAction.SCHEDULE_RECONNECT,
            webSocketFailureAction(
                intentionalClose = false,
                currentUrlIndex = 2,
                urlCount = 3,
                reconnectAttemptsAlready = 1,
                maxReconnectAttempts = 5
            )
        )
    }

    @Test
    fun failureGivesUpAfterMaxReconnectsOrIntentionalClose() {
        assertEquals(
            WebSocketFailureAction.GIVE_UP,
            webSocketFailureAction(
                intentionalClose = false,
                currentUrlIndex = 0,
                urlCount = 1,
                reconnectAttemptsAlready = 5,
                maxReconnectAttempts = 5
            )
        )
        assertEquals(
            WebSocketFailureAction.GIVE_UP,
            webSocketFailureAction(
                intentionalClose = true,
                currentUrlIndex = 0,
                urlCount = 2,
                reconnectAttemptsAlready = 0,
                maxReconnectAttempts = 5
            )
        )
    }

    @Test
    fun idleTimeoutReconnectOnlyWhenPastThresholdAndNotClosing() {
        assertTrue(
            shouldReconnectOnIdleTimeout(
                intentionalClose = false,
                idleTimeoutMillis = 100L,
                idleForMillis = 120L
            )
        )
        assertFalse(
            shouldReconnectOnIdleTimeout(
                intentionalClose = false,
                idleTimeoutMillis = 100L,
                idleForMillis = 50L
            )
        )
        assertFalse(
            shouldReconnectOnIdleTimeout(
                intentionalClose = true,
                idleTimeoutMillis = 100L,
                idleForMillis = 200L
            )
        )
    }

    @Test
    fun webSocketUtilsUsesConnectionPolicyHelpers() {
        val source = File("src/main/java/com/mylive/app/core/common/WebSocketUtils.kt").readText()
        assertTrue(source.contains("webSocketFailureAction("))
        assertTrue(source.contains("shouldReconnectOnIdleTimeout("))
        assertTrue(source.contains("nextReconnectAttemptCount("))
        assertTrue(source.contains("shouldRunScheduledReconnect("))
    }
}
