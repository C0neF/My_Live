package com.mylive.app.core.common

/**
 * Pure connection-policy helpers for [WebSocketUtils].
 * Keep timer/OkHttp side effects in the utils; decision tables live here so reconnect /
 * backup / idle behavior can be unit-tested without sockets.
 */

enum class WebSocketFailureAction {
    TRY_NEXT_URL,
    SCHEDULE_RECONNECT,
    GIVE_UP
}

/**
 * Decide what to do after a transport failure when [intentionalClose] is false.
 *
 * @param currentUrlIndex zero-based index into the ordered URL list
 * @param urlCount number of distinct connect URLs
 * @param reconnectAttemptsAlready how many reconnect timers have already fired
 * @param maxReconnectAttempts stop after this many scheduled reconnects
 */
internal fun webSocketFailureAction(
    intentionalClose: Boolean,
    currentUrlIndex: Int,
    urlCount: Int,
    reconnectAttemptsAlready: Int,
    maxReconnectAttempts: Int
): WebSocketFailureAction {
    if (intentionalClose) return WebSocketFailureAction.GIVE_UP
    if (currentUrlIndex < urlCount - 1) return WebSocketFailureAction.TRY_NEXT_URL
    if (reconnectAttemptsAlready >= maxReconnectAttempts) return WebSocketFailureAction.GIVE_UP
    return WebSocketFailureAction.SCHEDULE_RECONNECT
}

/** Idle-but-open sockets should reconnect rather than surface a terminal close. */
internal fun shouldReconnectOnIdleTimeout(
    intentionalClose: Boolean,
    idleTimeoutMillis: Long?,
    idleForMillis: Long
): Boolean {
    if (intentionalClose) return false
    val timeout = idleTimeoutMillis ?: return false
    if (timeout <= 0L) return false
    return idleForMillis >= timeout
}

/** Next reconnect counter after scheduling a reconnect attempt. */
internal fun nextReconnectAttemptCount(current: Int): Int = current + 1

/** Whether a scheduled reconnect tick should still run. */
internal fun shouldRunScheduledReconnect(intentionalClose: Boolean): Boolean = !intentionalClose
