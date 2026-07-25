package com.mylive.app.core.script

/** Raised when a remote script is refused before it ever reaches the JS engine. */
class UntrustedRemoteScriptException(message: String) : IllegalArgumentException(message)

/**
 * Gate for remote (site-provided) JavaScript that is evaluated by [JsEngine].
 *
 * Bundled asset scripts ship with the APK and are trusted. Site-provided scripts (for example the
 * Douyu `homeH5Enc` signing snippet) are attacker-controlled if the transport can be tampered with,
 * so they must at minimum come from an HTTPS origin and stay within a sane size budget.
 */
object RemoteScriptPolicy {

    /** Upper bound for a remote snippet. The real Douyu payload is a few kilobytes. */
    const val MAX_REMOTE_SCRIPT_BYTES: Int = 256 * 1024

    /** True when [url] is an absolute HTTPS URL with a non-empty host. */
    fun isTrustedScriptSource(url: String): Boolean {
        val normalized = url.trim()
        if (!normalized.startsWith("https://", ignoreCase = true)) return false
        val host = normalized
            .substring("https://".length)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
            .substringBefore(':')
        return host.isNotEmpty()
    }

    /**
     * Validates a remote script before evaluation.
     *
     * @return the same [script] so call sites can use this inline.
     * @throws UntrustedRemoteScriptException when the origin is not HTTPS, or the script is empty
     * or larger than [MAX_REMOTE_SCRIPT_BYTES].
     */
    fun requireTrustedRemoteScript(sourceUrl: String, script: String): String {
        if (!isTrustedScriptSource(sourceUrl)) {
            throw UntrustedRemoteScriptException(
                "refusing to evaluate a remote script from a non-HTTPS source: ${redact(sourceUrl)}"
            )
        }
        if (script.isBlank()) {
            throw UntrustedRemoteScriptException(
                "refusing to evaluate an empty remote script from ${redact(sourceUrl)}"
            )
        }
        val size = script.toByteArray(Charsets.UTF_8).size
        if (size > MAX_REMOTE_SCRIPT_BYTES) {
            throw UntrustedRemoteScriptException(
                "remote script from ${redact(sourceUrl)} is too large: $size > $MAX_REMOTE_SCRIPT_BYTES bytes"
            )
        }
        return script
    }

    private fun redact(url: String): String = url.trim().substringBefore('?').take(120)
}
