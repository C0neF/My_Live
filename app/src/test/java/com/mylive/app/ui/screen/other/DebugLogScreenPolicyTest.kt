package com.mylive.app.ui.screen.other

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DebugLogScreenPolicyTest {

    @Test
    fun logScreenCollectsReactiveEntriesAndUsesCoreClear() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/other/DebugLogScreen.kt"
        ).readText()

        assertTrue(source.contains("CoreLog.entries.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("CoreLog.clear()"))
        assertFalse(source.contains("CoreLog.logHistory"))
        assertFalse(source.contains("LaunchedEffect(Unit)"))
    }
}
