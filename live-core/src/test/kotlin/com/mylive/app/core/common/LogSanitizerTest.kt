package com.mylive.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogSanitizerTest {

    @Test
    fun safeUrlKeepsEndpointButRemovesCredentialsQueryAndFragment() {
        val safeUrl = safeUrlForLog(
            "wss://user:password@example.test:8443/live/connect?token=secret#private"
        )

        assertEquals("wss://example.test:8443/live/connect", safeUrl)
        assertFalse(safeUrl.contains("user"))
        assertFalse(safeUrl.contains("password"))
        assertFalse(safeUrl.contains("token"))
        assertFalse(safeUrl.contains("secret"))
        assertFalse(safeUrl.contains("private"))
    }

    @Test
    fun safePathRemovesQueryAndFragment() {
        assertEquals("/sync/import", safePathForLog("/sync/import?token=secret#private"))
    }
}
