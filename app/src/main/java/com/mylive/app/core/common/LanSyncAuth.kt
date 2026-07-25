package com.mylive.app.core.common

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/** Raised when a LAN sync request cannot be authenticated. Always answered with HTTP 401. */
class LanSyncAuthException(message: String) : IllegalArgumentException(message)

/**
 * A per-request authentication proof.
 *
 * [headerValue] goes into the `X-Sync-Auth` header, [requestContext] is the exact string that must
 * also be fed to [LanSyncCrypto.encrypt] as AEAD associated data.
 */
data class LanSyncProof(
    val headerValue: String,
    val requestContext: String
)

/**
 * Proof-of-possession authentication for LAN sync over plain HTTP.
 *
 * The previous scheme sent the pairing code itself in a header while using that same code to
 * derive the payload key, so a passive sniffer on the same Wi-Fi captured the key alongside the
 * ciphertext. Here the code never leaves the device: each request carries only
 *
 * `X-Sync-Auth: MLSYNC2-HMAC-SHA256 ts=<epoch-millis> nonce=<base64url> mac=<base64url>`
 *
 * where the MAC covers the canonical request context **and** a digest of the exact body bytes.
 * That single value gives us three properties at once:
 *
 * - **Authentication**: only a peer holding the pairing code can produce the MAC.
 * - **Context binding**: method, path and the `overlay` flag are signed, so an attacker cannot
 *   flip `?overlay=1` (which wipes follows / history / keywords) or move a body to another route.
 * - **Freshness**: requests outside [MAX_CLOCK_SKEW_MS] are refused, and each nonce is accepted
 *   only once inside that window (see [LanSyncReplayGuard]), so captured requests cannot be
 *   replayed for as long as the service happens to be running.
 */
object LanSyncAuth {

    /** Header the sender sets. */
    const val HEADER_NAME: String = "X-Sync-Auth"

    /** Lower-case header key, which is how NanoHTTPD exposes incoming headers. */
    const val HEADER_KEY: String = "x-sync-auth"

    /** Version-tagged scheme name. Bump it if the canonical string ever changes. */
    const val SCHEME: String = "MLSYNC2-HMAC-SHA256"

    /** Requests further than this from our clock are rejected as stale. */
    const val MAX_CLOCK_SKEW_MS: Long = 60_000L

    private const val MAC_ALGORITHM = "HmacSHA256"
    private const val NONCE_BYTES = 18
    private const val MAX_NONCE_CHARS = 64
    private const val MAX_MAC_CHARS = 64

    private val secureRandom = SecureRandom()
    private val base64UrlAlphabet = Regex("^[A-Za-z0-9_-]+$")

    /** A fresh 144-bit request nonce. */
    fun newNonce(): String = encode(ByteArray(NONCE_BYTES).also(secureRandom::nextBytes))

    /**
     * The canonical request context. Both peers must build this identically; it is signed by the
     * MAC and bound into the payload as AEAD associated data.
     */
    fun requestContext(
        method: String,
        path: String,
        overlay: Boolean,
        timestampMillis: Long,
        nonce: String
    ): String = listOf(
        SCHEME,
        method.uppercase(Locale.ROOT),
        path,
        if (overlay) "overlay=1" else "overlay=0",
        timestampMillis.toString(),
        nonce
    ).joinToString("\n")

    /** Builds the header value for an outgoing request. */
    fun createProof(
        token: String,
        method: String,
        path: String,
        overlay: Boolean,
        body: String,
        timestampMillis: Long = System.currentTimeMillis(),
        nonce: String = newNonce()
    ): LanSyncProof {
        if (token.isEmpty()) {
            throw LanSyncAuthException("pairing code is required to sign a LAN sync request")
        }
        val context = requestContext(method, path, overlay, timestampMillis, nonce)
        val mac = mac(token, context, body)
        return LanSyncProof(
            headerValue = "$SCHEME ts=$timestampMillis nonce=$nonce mac=$mac",
            requestContext = context
        )
    }

    /**
     * Verifies an incoming proof and returns the canonical request context to open the body with.
     *
     * The MAC is checked *before* the nonce is remembered, so an unauthenticated flood cannot fill
     * the replay guard.
     *
     * @throws LanSyncAuthException for every failure mode; the caller answers 401 without echoing
     * which check failed.
     */
    fun verifyProof(
        token: String,
        headerValue: String?,
        method: String,
        path: String,
        overlay: Boolean,
        body: String,
        nowMillis: Long = System.currentTimeMillis(),
        replayGuard: LanSyncReplayGuard? = null
    ): String {
        if (token.isEmpty()) {
            throw LanSyncAuthException("unauthorized: pairing required")
        }
        val header = headerValue?.trim().orEmpty()
        if (header.isEmpty() || !header.startsWith("$SCHEME ")) {
            throw LanSyncAuthException("unauthorized: pairing required")
        }

        val fields = header.removePrefix("$SCHEME ")
            .split(' ')
            .filter { it.isNotBlank() }
            .mapNotNull { field ->
                val separator = field.indexOf('=')
                if (separator <= 0) null else field.substring(0, separator) to field.substring(separator + 1)
            }
            .toMap()

        val timestamp = fields["ts"]?.toLongOrNull()
            ?: throw LanSyncAuthException("unauthorized: pairing required")
        val nonce = fields["nonce"].orEmpty()
        val providedMac = fields["mac"].orEmpty()
        if (!isBoundedBase64Url(nonce, MAX_NONCE_CHARS) || !isBoundedBase64Url(providedMac, MAX_MAC_CHARS)) {
            throw LanSyncAuthException("unauthorized: pairing required")
        }
        if (abs(nowMillis - timestamp) > MAX_CLOCK_SKEW_MS) {
            throw LanSyncAuthException("stale sync request")
        }

        val context = requestContext(method, path, overlay, timestamp, nonce)
        val expectedMac = mac(token, context, body)
        val matches = MessageDigest.isEqual(
            expectedMac.toByteArray(Charsets.UTF_8),
            providedMac.toByteArray(Charsets.UTF_8)
        )
        if (!matches) {
            throw LanSyncAuthException("unauthorized: pairing required")
        }

        if (replayGuard != null && !replayGuard.accept(nonce, nowMillis)) {
            throw LanSyncAuthException("replayed sync request")
        }
        return context
    }

    private fun mac(token: String, context: String, body: String): String {
        val keys = try {
            LanSyncCrypto.deriveKeys(token)
        } catch (e: LanSyncCryptoException) {
            throw LanSyncAuthException("unauthorized: pairing required")
        }
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(SecretKeySpec(keys.authenticationKey, MAC_ALGORITHM))
        mac.update(context.toByteArray(Charsets.UTF_8))
        mac.update('\n'.code.toByte())
        mac.update(MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)))
        return encode(mac.doFinal())
    }

    private fun isBoundedBase64Url(value: String, maxChars: Int): Boolean =
        value.isNotEmpty() && value.length <= maxChars && base64UrlAlphabet.matches(value)

    private fun encode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/**
 * Remembers recently accepted request nonces so a captured request cannot be replayed.
 *
 * Entries only live for [ttlMillis] (twice the accepted clock skew), and the table is bounded.
 * When it is full the guard fails **closed** rather than evicting: nonces are only ever recorded
 * after the MAC verified, so filling the table requires the pairing code, and refusing is far
 * safer than letting a flood push older nonces out and re-open the replay window.
 */
class LanSyncReplayGuard(
    private val ttlMillis: Long = 2 * LanSyncAuth.MAX_CLOCK_SKEW_MS,
    private val maxEntries: Int = 4_096
) {

    private val expiryByNonce = LinkedHashMap<String, Long>()

    /** Records [nonce]; returns false when it was already used or the table is saturated. */
    @Synchronized
    fun accept(nonce: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        prune(nowMillis)
        if (expiryByNonce.containsKey(nonce)) return false
        if (expiryByNonce.size >= maxEntries) return false
        expiryByNonce[nonce] = nowMillis + ttlMillis
        return true
    }

    @Synchronized
    fun clear() {
        expiryByNonce.clear()
    }

    @Synchronized
    fun size(): Int = expiryByNonce.size

    private fun prune(nowMillis: Long) {
        val iterator = expiryByNonce.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value <= nowMillis) iterator.remove()
        }
    }
}
