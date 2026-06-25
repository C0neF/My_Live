package com.mylive.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImportPayloadSizePolicyTest {

    @Test
    fun userAndWebDavImportsUseTheSharedBoundedReader() {
        val paths = listOf(
            "src/main/java/com/mylive/app/ui/screen/follow/FollowScreen.kt",
            "src/main/java/com/mylive/app/ui/screen/settings/ShieldSettingsScreen.kt",
            "src/main/java/com/mylive/app/ui/screen/sync/ProfileBackupScreen.kt",
            "src/main/java/com/mylive/app/ui/screen/sync/WebDavSyncScreen.kt"
        )

        paths.forEach { path ->
            val source = File(path).readText()
            assertTrue("$path must bound imported JSON", source.contains("readUtf8TextWithinLimit("))
            assertFalse("$path must not read unbounded imported JSON", source.contains(".readText()"))
            assertFalse("$path must not read an unbounded response body", source.contains("response.body?.string()"))
        }
    }
}
