package com.mylive.app.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CoreLog {

    @Volatile
    var enableLog: Boolean = false

    @Volatile
    private var debugEnabled: Boolean = false

    var onPrintLog: ((level: LogLevel, message: String) -> Unit)? = null
    @Volatile
    var platformLogger: ((level: LogLevel, message: String) -> Unit)? = null

    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }

    data class LogEntry(val time: String, val level: LogLevel, val message: String)

    private const val MAX_ENTRIES = 500
    private val forwardingToTimber = ThreadLocal<Boolean>()
    private val timeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
        .withZone(ZoneId.systemDefault())
    private val history = mutableListOf<LogEntry>()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun configure(enabled: Boolean, debugEnabled: Boolean) {
        val shouldAnnounce = enabled && !enableLog
        enableLog = enabled
        this.debugEnabled = debugEnabled
        if (shouldAnnounce) {
            append(LogLevel.INFO, "运行日志已开启")
        }
    }

    fun clear() {
        synchronized(history) {
            history.clear()
            _entries.value = history.toList()
        }
    }

    fun d(message: String) {
        recordAndForward(LogLevel.DEBUG, message)
    }

    fun i(message: String) {
        recordAndForward(LogLevel.INFO, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        val historyMessage = formatThrowable(message, throwable)
        recordAndForward(LogLevel.ERROR, historyMessage)
    }

    fun error(e: Throwable) {
        val message = e.stackTraceToString()
        recordAndForward(LogLevel.ERROR, message)
    }

    fun w(message: String) {
        recordAndForward(LogLevel.WARNING, message)
    }

    fun recordFromTimber(
        priority: Int,
        tag: String?,
        message: String
    ) {
        if (forwardingToTimber.get() == true) return
        val level = priority.toLogLevel()
        if (!shouldRecord(level)) return

        val taggedMessage = if (tag.isNullOrBlank()) message else "[$tag] $message"
        append(level, taggedMessage)
        onPrintLog?.invoke(level, taggedMessage)
    }

    private fun recordAndForward(
        level: LogLevel,
        message: String
    ) {
        if (!shouldRecord(level)) return
        append(level, message)
        onPrintLog?.invoke(level, message)
        if (onPrintLog != null) return

        forwardingToTimber.set(true)
        try {
            platformLogger?.invoke(level, message)
        } finally {
            forwardingToTimber.remove()
        }
    }

    private fun shouldRecord(level: LogLevel): Boolean {
        return enableLog || (level == LogLevel.DEBUG && debugEnabled)
    }

    private fun append(level: LogLevel, message: String) {
        val entry = LogEntry(
            time = timeFormatter.format(Instant.now()),
            level = level,
            message = message
        )
        synchronized(history) {
            if (history.size >= MAX_ENTRIES) {
                history.removeAt(0)
            }
            history.add(entry)
            _entries.value = history.toList()
        }
    }

    private fun formatThrowable(message: String, throwable: Throwable?): String {
        if (throwable == null) return message
        return "$message\n${throwable.stackTraceToString()}"
    }

    private fun Int.toLogLevel(): LogLevel {
        return when (this) {
            2, 3 -> LogLevel.DEBUG
            4 -> LogLevel.INFO
            5 -> LogLevel.WARNING
            else -> LogLevel.ERROR
        }
    }
}
