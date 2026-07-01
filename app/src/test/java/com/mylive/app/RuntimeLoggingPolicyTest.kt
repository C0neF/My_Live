package com.mylive.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RuntimeLoggingPolicyTest {

    @Test
    fun applicationOwnsRuntimeLoggingAndActivityDoesNot() {
        val app = File("src/main/java/com/mylive/app/MyLiveApp.kt").readText()
        val activity = File("src/main/java/com/mylive/app/MainActivity.kt").readText()

        assertTrue(app.contains("RuntimeLogTree(logToLogcat = BuildConfig.DEBUG)"))
        assertTrue(app.contains("settingsRepository.logEnable"))
        assertTrue(app.contains("settingsRepository.debugMode"))
        assertTrue(app.contains("CoreLog.configure("))
        assertFalse(activity.contains("settingsRepository.logEnable.collect"))
    }
}
