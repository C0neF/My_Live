package com.mylive.app.service

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class LanSyncSecurityPolicyTest {

    @Test
    fun lanPairingTokenIsNotInterpolatedIntoLogs() {
        val source = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()

        assertFalse(
            "LAN pairing token authorizes write endpoints and must not be written to debug logs",
            source.contains("${'$'}syncToken")
        )
    }
}
