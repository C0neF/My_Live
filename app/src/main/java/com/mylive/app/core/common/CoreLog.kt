package com.mylive.app.core.common

import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 * Request log level type, mirroring the Dart RequestLogType enum.
 */
enum class RequestLogType {
    /** Output all request information including URL, parameters, headers, body, response headers, content, and time */
    ALL,
    /** Short output: only URL and response status code */
    SHORT,
    /** No request logging */
    NONE
}

/**
 * Core logging utility wrapping Timber.
 *
 * Provides static-style logging methods with a global enable/disable switch
 * and an optional callback for external log handling.
 */
object CoreLog {

    /** Whether logging is enabled */
    var enableLog: Boolean = true

    /** Request log mode */
    var requestLogType: RequestLogType = RequestLogType.ALL

    /** Optional external log callback */
    var onPrintLog: ((level: LogLevel, message: String) -> Unit)? = null

    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }

    data class LogEntry(val time: String, val level: LogLevel, val message: String)
    val logHistory: MutableList<LogEntry> = Collections.synchronizedList(mutableListOf<LogEntry>())
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private fun addEntry(level: LogLevel, message: String) {
        val time = timeFormat.format(Date())
        synchronized(logHistory) {
            if (logHistory.size >= 500) {
                logHistory.removeAt(0)
            }
            logHistory.add(LogEntry(time, level, message))
        }
    }

    fun d(message: String) {
        if (!enableLog) return
        addEntry(LogLevel.DEBUG, message)
        onPrintLog?.invoke(LogLevel.DEBUG, message)
        if (onPrintLog == null) {
            Timber.d(message)
        }
    }

    fun i(message: String) {
        if (!enableLog) return
        addEntry(LogLevel.INFO, message)
        onPrintLog?.invoke(LogLevel.INFO, message)
        if (onPrintLog == null) {
            Timber.i(message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (!enableLog) return
        addEntry(LogLevel.ERROR, message)
        onPrintLog?.invoke(LogLevel.ERROR, message)
        if (onPrintLog == null) {
            if (throwable != null) {
                Timber.e(throwable, message)
            } else {
                Timber.e(message)
            }
        }
    }

    fun error(e: Throwable) {
        if (!enableLog) return
        val msg = e.stackTraceToString()
        addEntry(LogLevel.ERROR, msg)
        onPrintLog?.invoke(LogLevel.ERROR, msg)
        if (onPrintLog == null) {
            Timber.e(e, e.message ?: "Unknown error")
        }
    }

    fun w(message: String) {
        if (!enableLog) return
        addEntry(LogLevel.WARNING, message)
        onPrintLog?.invoke(LogLevel.WARNING, message)
        if (onPrintLog == null) {
            Timber.w(message)
        }
    }
}

