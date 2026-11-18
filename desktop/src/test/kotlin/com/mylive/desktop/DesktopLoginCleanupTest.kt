package com.mylive.desktop

import org.junit.Assert.*
import org.junit.Test
import java.net.CookieHandler
import java.net.CookieManager

class DesktopLoginCleanupTest {
    @Test fun earlyCloseSuppressesQueuedPageStartupAndRestoresCookiesAfterBrowserStops() {
        val original = CookieHandler.getDefault()
        val manager = CookieManager(); val queue = mutableListOf<() -> Unit>(); val events = mutableListOf<String>()
        try {
            CookieHandler.setDefault(manager)
            val cleanup = DesktopLoginCleanup(manager, original, { queue += it }, {
                assertSame(manager, CookieHandler.getDefault()); events += "stop"
            }, { events += "finished" })
            queue += { cleanup.ifOpen { events += "load" } }
            cleanup.close(); cleanup.close()
            assertSame(manager, CookieHandler.getDefault())
            queue.forEach { it() }
            assertEquals(listOf("stop", "finished"), events)
            assertSame(original, CookieHandler.getDefault())
        } finally { CookieHandler.setDefault(original) }
    }
    @Test fun teardownDoesNotReplaceAnotherSessionCookieHandler() {
        val original = CookieHandler.getDefault(); val first = CookieManager(); val newer = CookieManager()
        try {
            val cleanup = DesktopLoginCleanup(first, original, { it() }, {}, {})
            CookieHandler.setDefault(newer); cleanup.close()
            assertSame(newer, CookieHandler.getDefault())
        } finally { CookieHandler.setDefault(original) }
    }
}
