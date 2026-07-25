package com.mylive.app.core.common

import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Raised when a LAN sync payload cannot be sealed or opened: missing pairing token,
 * malformed envelope, wrong key, wrong request context, or a tampered body.
 */
class LanSyncCryptoException(message: String) : IllegalArgumentException(message)

/**
 * The two independent 256-bit keys derived from a pairing code.
 *
 * [encryptionKey] seals payloads with AES-256-GCM, [authenticationKey] signs the request context
 * with HMAC-SHA256. They are separated so that a weakness in one use never leaks the other.
 */
class LanSyncKeys(
    val encryptionKey: ByteArray,
    val authenticationKey: ByteArray
)

/**
 * End-to-end encryption for LAN sync payloads.
 *
 * The LAN transport is plain HTTP (a NanoHTTPD server on a link-local address cannot present a
 * trusted TLS certificate), so confidentiality and integrity are provided at the payload level:
 *
 * - The pairing code is only ever shared out of band (QR code / on-screen pairing code). It is
 *   **never** put on the wire, not even as an authorization header: possession is proven with a
 *   per-request HMAC (see [LanSyncAuth]), so a passive sniffer never observes key material.
 * - Both keys are stretched from the pairing code with PBKDF2-HMAC-SHA256, so a short, human
 *   typed pairing code is not trivially brute-forceable from a captured request.
 * - Every payload is sealed with AES-256-GCM using a fresh random 96-bit IV, and the request
 *   context (method, path, `overlay` flag, timestamp, nonce) is authenticated as GCM AAD. A body
 *   therefore cannot be replayed against another endpoint, nor can query parameters such as
 *   `overlay=1` (which wipes local collections) be flipped in flight.
 *
 * Envelope format (ASCII, safe to send as an HTTP body):
 * `MLSYNC2.<base64url(iv)>.<base64url(ciphertext||tag)>`
 */
object LanSyncCrypto {

    /** Version-tagged envelope prefix. Bump the suffix if the wire format ever changes. */
    const val ENVELOPE_PREFIX: String = "MLSYNC2."

    private const val KDF_DOMAIN = "mylive-lan-sync-kdf-v2"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KDF_ITERATIONS = 120_000
    private const val KDF_DERIVED_BITS = 512
    private const val KEY_BYTES = 32
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /**
     * Key stretching is deliberately expensive, so derived keys are memoised per pairing code.
     * Only codes this device already trusts ever reach the KDF, and the cache is tiny and cleared
     * wholesale when it fills, so it cannot grow into a leak of old pairing material.
     */
    private const val MAX_CACHED_KEYS = 8

    private val secureRandom = SecureRandom()
    private val keyCache = ConcurrentHashMap<String, LanSyncKeys>()

    /** True when [body] looks like a sealed envelope produced by [encrypt]. */
    fun isEncryptedEnvelope(body: String): Boolean = body.trimStart().startsWith(ENVELOPE_PREFIX)

    /**
     * Stretches [token] into the encryption and authentication keys.
     *
     * Exposed for [LanSyncAuth] and tests; payload callers should prefer [encrypt] / [decrypt].
     */
    fun deriveKeys(token: String): LanSyncKeys {
        if (token.isEmpty()) {
            throw LanSyncCryptoException("pairing token is required for LAN sync encryption")
        }
        keyCache[token]?.let { return it }

        val salt = MessageDigest.getInstance("SHA-256")
            .digest(KDF_DOMAIN.toByteArray(Charsets.UTF_8))
        val spec = PBEKeySpec(token.toCharArray(), salt, KDF_ITERATIONS, KDF_DERIVED_BITS)
        val material = try {
            SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        } catch (e: GeneralSecurityException) {
            throw LanSyncCryptoException("unable to derive LAN sync keys")
        } finally {
            spec.clearPassword()
        }

        val keys = LanSyncKeys(
            encryptionKey = material.copyOfRange(0, KEY_BYTES),
            authenticationKey = material.copyOfRange(KEY_BYTES, KEY_BYTES * 2)
        )
        material.fill(0)
        if (keyCache.size >= MAX_CACHED_KEYS) keyCache.clear()
        keyCache[token] = keys
        return keys
    }

    /** Forgets every memoised key. Called when the sync service stops and rotates its code. */
    fun clearCachedKeys() {
        keyCache.clear()
    }

    /**
     * Seals [plaintext] with the key derived from [token], binding it to [requestContext].
     *
     * [requestContext] must be the exact canonical request context the peer will rebuild from the
     * HTTP request line and headers (see [LanSyncAuth.requestContext]); a mismatch fails
     * authentication, which is what stops replay onto a different endpoint or with a flipped
     * `overlay` flag.
     */
    fun encrypt(token: String, plaintext: String, requestContext: String): String {
        requireContext(requestContext)
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(token), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(requestContext.toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return ENVELOPE_PREFIX + encoder.encodeToString(iv) + "." + encoder.encodeToString(sealed)
    }

    /**
     * Opens an envelope produced by [encrypt].
     *
     * @throws LanSyncCryptoException when the body is not an envelope, is malformed, was sealed
     * with a different pairing code, was sealed for a different request context, or was modified
     * in flight.
     */
    fun decrypt(token: String, envelope: String, requestContext: String): String {
        requireContext(requestContext)
        val trimmed = envelope.trim()
        if (!isEncryptedEnvelope(trimmed)) {
            throw LanSyncCryptoException("payload is not an encrypted LAN sync envelope")
        }

        val parts = trimmed.removePrefix(ENVELOPE_PREFIX).split(".")
        if (parts.size != 2 || parts.any { it.isEmpty() }) {
            throw LanSyncCryptoException("malformed LAN sync envelope")
        }

        val iv = decodeBase64Url(parts[0])
        val sealed = decodeBase64Url(parts[1])
        if (iv.size != IV_BYTES) {
            throw LanSyncCryptoException("malformed LAN sync envelope")
        }

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(token), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(requestContext.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            // Wrong pairing code, wrong request context, or a tampered body; never surface cipher
            // internals to the peer.
            throw LanSyncCryptoException("LAN sync payload failed authentication")
        }
    }

    private fun requireContext(requestContext: String) {
        if (requestContext.isBlank()) {
            throw LanSyncCryptoException("LAN sync payloads must be bound to a request context")
        }
    }

    private fun secretKey(token: String) =
        SecretKeySpec(deriveKeys(token).encryptionKey, "AES")

    private fun decodeBase64Url(value: String): ByteArray = try {
        Base64.getUrlDecoder().decode(value)
    } catch (e: IllegalArgumentException) {
        throw LanSyncCryptoException("malformed LAN sync envelope")
    }
}
