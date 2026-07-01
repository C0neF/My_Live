package com.mylive.app.core.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class CoreLogTest {

    @Before
    fun setUp() {
        Timber.uprootAll()
        CoreLog.clear()
        CoreLog.onPrintLog = null
        CoreLog.configure(enabled = false, debugEnabled = false)
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
        CoreLog.clear()
        CoreLog.onPrintLog = null
        CoreLog.configure(enabled = false, debugEnabled = false)
    }

    @Test
    fun timberEntryIsCapturedWhenRuntimeLoggingIsEnabled() {
        CoreLog.configure(enabled = true, debugEnabled = true)
        CoreLog.clear()
        Timber.plant(RuntimeLogTree(logToLogcat = false))

        Timber.tag("Player").d("playback started")

        assertEquals(1, CoreLog.entries.value.size)
        assertTrue(CoreLog.entries.value.single().message.contains("Player"))
        assertTrue(CoreLog.entries.value.single().message.contains("playback started"))
    }

    @Test
    fun coreLogEntryIsNotCapturedTwiceThroughTimber() {
        CoreLog.configure(enabled = true, debugEnabled = true)
        CoreLog.clear()
        Timber.plant(RuntimeLogTree(logToLogcat = false))

        CoreLog.i("room loaded")

        assertEquals(1, CoreLog.entries.value.size)
        assertEquals("room loaded", CoreLog.entries.value.single().message)
    }

    @Test
    fun timberThrowableStackIsCapturedOnlyOnce() {
        CoreLog.configure(enabled = true, debugEnabled = true)
        CoreLog.clear()
        Timber.plant(RuntimeLogTree(logToLogcat = false))

        Timber.e(IllegalStateException("boom"), "playback failed")

        val message = CoreLog.entries.value.single().message
        assertEquals(1, "IllegalStateException".toRegex().findAll(message).count())
    }

    @Test
    fun loggingSwitchCapturesDebugEntriesWithoutDebugMode() {
        CoreLog.configure(enabled = true, debugEnabled = false)
        CoreLog.clear()
        Timber.plant(RuntimeLogTree(logToLogcat = false))

        Timber.d("debug captured")

        assertEquals(
            listOf(CoreLog.LogLevel.DEBUG),
            CoreLog.entries.value.map { it.level }
        )
    }

    @Test
    fun debugModeCapturesDebugEntriesWithoutGeneralLogging() {
        Timber.plant(RuntimeLogTree(logToLogcat = false))
        CoreLog.configure(enabled = false, debugEnabled = true)

        Timber.d("debug captured")
        Timber.w("warning ignored")

        assertEquals(
            listOf(CoreLog.LogLevel.DEBUG),
            CoreLog.entries.value.map { it.level }
        )
    }

    @Test
    fun enablingRuntimeLoggingPublishesConfirmationEntry() {
        CoreLog.configure(enabled = false, debugEnabled = false)

        CoreLog.configure(enabled = true, debugEnabled = false)

        assertEquals(1, CoreLog.entries.value.size)
        assertEquals(CoreLog.LogLevel.INFO, CoreLog.entries.value.single().level)
        assertTrue(CoreLog.entries.value.single().message.contains("日志"))
    }

    @Test
    fun historyIsBoundedAndClearPublishesEmptySnapshot() {
        CoreLog.configure(enabled = true, debugEnabled = true)
        CoreLog.clear()

        repeat(510) { CoreLog.i("entry-$it") }

        assertEquals(500, CoreLog.entries.value.size)
        assertEquals("entry-10", CoreLog.entries.value.first().message)

        CoreLog.clear()

        assertTrue(CoreLog.entries.value.isEmpty())
    }
}
