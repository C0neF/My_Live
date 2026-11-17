package com.mylive.app.core.script

import java.security.MessageDigest

/** Raised when a remote script is refused before it ever reaches the JS engine. */
class UntrustedRemoteScriptException(message: String) : IllegalArgumentException(message)

/**
 * Gate for remote (site-provided) JavaScript evaluated by a platform's [JsExecutor].
 *
 * Bundled asset scripts ship with the app and are trusted. Site-provided scripts (for example the
 * Douyu `homeH5Enc` signing snippet) are attacker-controlled if the transport can be tampered with,
 * so they must come from an allowlisted HTTPS host, stay within a sane size budget, and match a
 * SHA-256 pin when the caller has one.
 *
 * [trustedRemoteScriptOrNull] is the graceful form: a rejected script is not evaluated and the
 * caller keeps its previous behaviour (bundled signer, unsigned request, or a user-visible error).
 */
object RemoteScriptPolicy {

    /** Upper bound for a remote snippet. The real Douyu payload is a few kilobytes. */
    const val MAX_REMOTE_SCRIPT_BYTES: Int = 256 * 1024

    /**
     * Hosts this codebase actually downloads signing JavaScript from.
     *
     * Today that is only Douyu's `homeH5Enc` endpoint (`https://www.douyu.com/swf_api/homeH5Enc`).
     * Douyin signing scripts are bundled assets and never pass through this gate.
     */
    val ALLOWED_SCRIPT_HOSTS: Set<String> = setOf("www.douyu.com")

    /** True when [url] is an absolute HTTPS URL whose host is on [ALLOWED_SCRIPT_HOSTS]. */
    fun isTrustedScriptSource(url: String): Boolean = scriptHost(url) in ALLOWED_SCRIPT_HOSTS

    /**
     * Validates a remote script before evaluation.
     *
     * @param expectedSha256 optional lowercase or uppercase hex SHA-256 of the script's UTF-8 bytes.
     *   When null, no pin is required. When present, a mismatch or a malformed pin is a rejection.
     * @return the same [script] so call sites can use this inline.
     * @throws UntrustedRemoteScriptException when the origin is not an allowlisted HTTPS host, the
     * script is empty or oversized, or a supplied hash pin does not match.
     */
    fun requireTrustedRemoteScript(
        sourceUrl: String,
        script: String,
        expectedSha256: String? = null
    ): String {
        if (!isTrustedScriptSource(sourceUrl)) {
            throw UntrustedRemoteScriptException(
                "refusing to evaluate a remote script from an untrusted source: ${redact(sourceUrl)}"
            )
        }
        if (script.isBlank()) {
            throw UntrustedRemoteScriptException(
                "refusing to evaluate an empty remote script from ${redact(sourceUrl)}"
            )
        }
        val bytes = script.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_REMOTE_SCRIPT_BYTES) {
            throw UntrustedRemoteScriptException(
                "remote script from ${redact(sourceUrl)} is too large: ${bytes.size} > $MAX_REMOTE_SCRIPT_BYTES bytes"
            )
        }
        if (expectedSha256 != null && !sha256Matches(bytes, expectedSha256)) {
            throw UntrustedRemoteScriptException(
                "remote script from ${redact(sourceUrl)} does not match the pinned SHA-256"
            )
        }
        return script
    }

    /**
     * Same checks as [requireTrustedRemoteScript], but a rejection returns null instead of throwing
     * so playback can fall back without evaluating the script.
     */
    fun trustedRemoteScriptOrNull(
        sourceUrl: String,
        script: String,
        expectedSha256: String? = null
    ): String? = try {
        requireTrustedRemoteScript(sourceUrl, script, expectedSha256)
    } catch (_: UntrustedRemoteScriptException) {
        null
    }

    /** SHA-256 hex of [script] as UTF-8, for callers that want to pin a known payload. */
    fun sha256Hex(script: String): String = sha256Hex(script.toByteArray(Charsets.UTF_8))

    internal fun scriptHost(url: String): String? {
        val normalized = url.trim()
        if (!normalized.startsWith("https://", ignoreCase = true)) return null
        val rest = normalized.substring("https://".length)
        if (rest.isEmpty()) return null
        val authority = rest
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        if (authority.isEmpty() || authority.any { it.isWhitespace() || it == '\\' }) return null
        val hostPort = authority.substringAfterLast('@')
        // IPv6 literals are not a Douyu signing host, and ':' parsing would mis-read them.
        if (hostPort.isEmpty() || hostPort.startsWith('[')) return null
        val host = hostPort.substringBefore(':')
        if (host.isEmpty() || host.any { it !in HOST_CHARS }) return null
        return host.lowercase()
    }

    private fun sha256Matches(bytes: ByteArray, expectedSha256: String): Boolean {
        val expected = expectedSha256.trim().lowercase()
        if (expected.length != 64 || expected.any { it !in HEX }) return false
        return sha256Hex(bytes) == expected
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun redact(url: String): String = url.trim().substringBefore('?').take(120)

    private const val HOST_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-"
    private const val HEX = "0123456789abcdef"
}
