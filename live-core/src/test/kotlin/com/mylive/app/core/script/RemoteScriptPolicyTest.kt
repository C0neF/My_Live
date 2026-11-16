package com.mylive.app.core.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Real behaviour tests for the gate applied to site-provided JavaScript before evaluation. */
class RemoteScriptPolicyTest {

    private val douyuUrl = "https://www.douyu.com/swf_api/homeH5Enc?rids=1234"
    private val script = "function ub98484234(){return 'v=1'}"

    @Test
    fun httpsSourcesAreAccepted() {
        assertTrue(RemoteScriptPolicy.isTrustedScriptSource(douyuUrl))
        assertTrue(RemoteScriptPolicy.isTrustedScriptSource("HTTPS://www.douyu.com/a.js"))
        assertEquals(script, RemoteScriptPolicy.requireTrustedRemoteScript(douyuUrl, script))
    }

    @Test
    fun cleartextAndSchemelessSourcesAreRejected() {
        assertFalse(RemoteScriptPolicy.isTrustedScriptSource("http://www.douyu.com/a.js"))
        assertFalse(RemoteScriptPolicy.isTrustedScriptSource("//www.douyu.com/a.js"))
        assertFalse(RemoteScriptPolicy.isTrustedScriptSource("file:///sdcard/a.js"))
        assertFalse(RemoteScriptPolicy.isTrustedScriptSource("https://"))
        assertFalse(RemoteScriptPolicy.isTrustedScriptSource(""))

        assertRejected { RemoteScriptPolicy.requireTrustedRemoteScript("http://www.douyu.com/a.js", script) }
    }

    @Test
    fun emptyScriptsAreRejected() {
        assertRejected { RemoteScriptPolicy.requireTrustedRemoteScript(douyuUrl, "") }
        assertRejected { RemoteScriptPolicy.requireTrustedRemoteScript(douyuUrl, "   \n ") }
    }

    @Test
    fun oversizedScriptsAreRejected() {
        val oversized = "a".repeat(RemoteScriptPolicy.MAX_REMOTE_SCRIPT_BYTES + 1)

        assertRejected { RemoteScriptPolicy.requireTrustedRemoteScript(douyuUrl, oversized) }
    }

    @Test
    fun rejectionMessageDoesNotEchoTheQueryString() {
        try {
            RemoteScriptPolicy.requireTrustedRemoteScript("http://evil.example/a.js?token=secret", script)
            fail("expected UntrustedRemoteScriptException")
        } catch (expected: UntrustedRemoteScriptException) {
            assertFalse(expected.message!!.contains("secret"))
        }
    }

    @Test
    fun onlyTheDouyuSigningHostIsAllowlisted() {
        assertEquals(setOf("www.douyu.com"), RemoteScriptPolicy.ALLOWED_SCRIPT_HOSTS)
        assertTrue(RemoteScriptPolicy.isTrustedScriptSource("https://www.douyu.com/swf_api/homeH5Enc"))
        assertTrue(RemoteScriptPolicy.isTrustedScriptSource("https://user:token@www.douyu.com:443/a.js"))

        listOf(
            "https://evil.example/a.js",
            "https://www.douyu.com.evil.example/a.js",
            "https://notdouyu.com/a.js",
            "https://douyu.com/a.js",
            "https://m.douyu.com/a.js",
            "https://www.douyu.com.attacker/a.js",
            "https://127.0.0.1/a.js",
            "https://[::1]/a.js"
        ).forEach { url ->
            assertFalse(url, RemoteScriptPolicy.isTrustedScriptSource(url))
            assertRejected { RemoteScriptPolicy.requireTrustedRemoteScript(url, script) }
            assertEquals(null, RemoteScriptPolicy.trustedRemoteScriptOrNull(url, script))
        }
    }

    @Test
    fun optionalSha256PinAcceptsAMatchAndRejectsAMismatch() {
        val pin = RemoteScriptPolicy.sha256Hex(script)
        assertEquals(script, RemoteScriptPolicy.requireTrustedRemoteScript(douyuUrl, script, pin.uppercase()))
        assertEquals(script, RemoteScriptPolicy.trustedRemoteScriptOrNull(douyuUrl, script, "  $pin  "))

        assertRejected { RemoteScriptPolicy.requireTrustedRemoteScript(douyuUrl, script, "0".repeat(64)) }
        assertEquals(null, RemoteScriptPolicy.trustedRemoteScriptOrNull(douyuUrl, script, "0".repeat(64)))
        assertRejected { RemoteScriptPolicy.requireTrustedRemoteScript(douyuUrl, script, "not-a-hash") }
        assertRejected { RemoteScriptPolicy.requireTrustedRemoteScript(douyuUrl, script, pin.dropLast(1)) }
    }

    @Test
    fun hashMismatchMessageDoesNotIncludeTheScriptOrThePin() {
        val pin = "ab".repeat(32)
        try {
            RemoteScriptPolicy.requireTrustedRemoteScript(douyuUrl, script, pin)
            fail("expected UntrustedRemoteScriptException")
        } catch (expected: UntrustedRemoteScriptException) {
            assertFalse(expected.message!!.contains(script))
            assertFalse(expected.message!!.contains(pin))
        }
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("expected UntrustedRemoteScriptException")
        } catch (expected: UntrustedRemoteScriptException) {
            assertTrue(expected.message!!.isNotBlank())
        }
    }
}
