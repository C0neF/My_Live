package com.mylive.app.core.common

import org.json.JSONObject
import java.net.URI
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

class RemoteSyncCryptoException(message: String) : IllegalArgumentException(message)

data class RemoteSyncInvite(
    val roomId: String,
    val roomKey: String
) {
    val inviteCode: String
        get() = RemoteSyncCrypto.formatInviteCode(roomId, roomKey)

    val qrPayload: String
        get() = RemoteSyncCrypto.buildQrPayload(roomId, roomKey)
}

data class DecryptedRemoteSyncPayload(
    val overlay: Boolean,
    val content: String
)

/**
 * End-to-end encryption for Remote Sync Room payloads traversing an untrusted WebSocket relay.
 *
 * - Room ID (6 chars) is public routing metadata assigned by the relay.
 * - Room pairing key (10 unambiguous Base32 chars = 50 bits of entropy) is generated locally by
 *   the room creator and shared out-of-band via QR (`mylive-room://<roomId>?key=<key>`) or
 *   copyable invite code (`<roomId>-<key5>-<key5>`). It is NEVER sent to the relay server.
 * - Key derivation stretches `roomKey` with PBKDF2-HMAC-SHA256 (120,000 iterations) salted with
 *   a domain-separated hash of `roomId`, preventing precomputed rainbow tables across rooms.
 * - Payloads (`overlay` + `content`) are sealed with AES-256-GCM using a fresh 96-bit random IV
 *   and AAD binding `(roomId, channel)`, preventing cross-room replay and cross-type substitution.
 */
object RemoteSyncCrypto {

    const val ENVELOPE_PREFIX = "RMSYNC1."
    const val ROOM_ID_LENGTH = 6
    const val DEFAULT_ROOM_KEY_LENGTH = 10
    const val MIN_ROOM_KEY_LENGTH = 8
    const val MAX_ROOM_KEY_LENGTH = 32

    private const val UNAMBIGUOUS_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val QR_SCHEME = "mylive-room"
    private const val KDF_DOMAIN_PREFIX = "mylive-remote-sync-kdf-v1:"
    private const val AAD_PREFIX = "mylive-remote-sync-aad-v1"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KDF_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val MAX_CACHED_KEYS = 8
    const val MAX_CLOCK_SKEW_MS = 120_000L

    private val secureRandom = SecureRandom()
    private val keyCache = ConcurrentHashMap<String, SecretKeySpec>()

    fun generateRoomKey(length: Int = DEFAULT_ROOM_KEY_LENGTH): String {
        require(length in MIN_ROOM_KEY_LENGTH..MAX_ROOM_KEY_LENGTH) {
            "invalid room key length: $length"
        }
        return buildString(length) {
            repeat(length) {
                append(UNAMBIGUOUS_ALPHABET[secureRandom.nextInt(UNAMBIGUOUS_ALPHABET.length)])
            }
        }
    }

    fun normalizeRoomId(raw: String): String {
        val cleaned = raw.trim().uppercase()
        if (cleaned.length != ROOM_ID_LENGTH || !cleaned.all { it.isLetterOrDigit() }) {
            throw RemoteSyncCryptoException("无效的房间号格式")
        }
        return cleaned
    }

    fun normalizeRoomKey(raw: String): String {
        val cleaned = raw.trim()
            .replace("-", "")
            .replace(" ", "")
            .replace("_", "")
            .uppercase()
        if (
            cleaned.length !in MIN_ROOM_KEY_LENGTH..MAX_ROOM_KEY_LENGTH ||
            !cleaned.all { it.isLetterOrDigit() }
        ) {
            throw RemoteSyncCryptoException("无效的房间配对密钥")
        }
        return cleaned
    }

    fun formatInviteCode(roomId: String, roomKey: String): String {
        val cleanRoom = normalizeRoomId(roomId)
        val cleanKey = normalizeRoomKey(roomKey)
        val groupedKey = cleanKey.chunked(5).joinToString("-")
        return "$cleanRoom-$groupedKey"
    }

    fun buildQrPayload(roomId: String, roomKey: String): String {
        val cleanRoom = normalizeRoomId(roomId)
        val cleanKey = normalizeRoomKey(roomKey)
        return "$QR_SCHEME://$cleanRoom?key=$cleanKey"
    }

    fun parseInvite(raw: String): RemoteSyncInvite? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("$QR_SCHEME://", ignoreCase = true)) {
            return runCatching {
                val uri = URI(trimmed)
                val roomPart = (uri.host ?: uri.path?.trim('/'))?.trim().orEmpty()
                val keyPart = parseQueryParam(uri.rawQuery, "key")
                    ?: parseQueryParam(uri.rawQuery, "k")
                    ?: ""
                RemoteSyncInvite(
                    roomId = normalizeRoomId(roomPart),
                    roomKey = normalizeRoomKey(keyPart)
                )
            }.getOrNull()
        }

        if (trimmed.startsWith("/remote_sync/room", ignoreCase = true)) {
            return runCatching {
                val parts = trimmed.removePrefix("/remote_sync/room").split("?", limit = 2)
                var roomCandidate = parts.firstOrNull()?.trim('/').orEmpty()
                var keyCandidate = ""
                if (parts.size > 1) {
                    parseQueryParam(parts[1], "roomId")?.let { roomCandidate = it }
                    keyCandidate = parseQueryParam(parts[1], "key")
                        ?: parseQueryParam(parts[1], "k")
                        ?: ""
                }
                if (keyCandidate.isNotEmpty()) {
                    RemoteSyncInvite(
                        roomId = normalizeRoomId(roomCandidate),
                        roomKey = normalizeRoomKey(keyCandidate)
                    )
                } else {
                    parseInvite(roomCandidate)
                }
            }.getOrNull()
        }

        val compact = trimmed
            .replace("-", "")
            .replace(" ", "")
            .replace("_", "")
            .uppercase()
        if (
            compact.length !in (ROOM_ID_LENGTH + MIN_ROOM_KEY_LENGTH)..(ROOM_ID_LENGTH + MAX_ROOM_KEY_LENGTH) ||
            !compact.all { it.isLetterOrDigit() }
        ) {
            return null
        }
        return runCatching {
            RemoteSyncInvite(
                roomId = normalizeRoomId(compact.substring(0, ROOM_ID_LENGTH)),
                roomKey = normalizeRoomKey(compact.substring(ROOM_ID_LENGTH))
            )
        }.getOrNull()
    }

    fun canonicalChannel(actionOrEvent: String): String? = when (actionOrEvent) {
        "SendFavorite", "sendFavorite", "favoriteReceived", "favorite" -> "favorite"
        "SendHistory", "sendHistory", "historyReceived", "history" -> "history"
        "SendShieldWord", "sendShieldWord", "shieldWordReceived", "shieldWord" -> "shieldWord"
        "SendBiliAccount", "sendBiliAccount", "biliAccountReceived", "biliAccount" -> "biliAccount"
        "SendDouyinAccount", "sendDouyinAccount", "douyinAccountReceived", "douyinAccount" -> "douyinAccount"
        else -> null
    }

    fun isEncryptedEnvelope(value: String): Boolean = value.trimStart().startsWith(ENVELOPE_PREFIX)

    fun encryptPayload(
        roomId: String,
        roomKey: String,
        channel: String,
        overlay: Boolean,
        content: String,
        nowMillis: Long = System.currentTimeMillis(),
        nonce: String = LanSyncAuth.newNonce()
    ): JSONObject {
        val canonical = canonicalChannel(channel)
            ?: throw RemoteSyncCryptoException("不支持的同步数据类型: $channel")
        val cleanRoom = normalizeRoomId(roomId)
        val cleanKey = normalizeRoomKey(roomKey)
        val plaintextJson = JSONObject().apply {
            put("overlay", overlay)
            put("content", content)
            put("ts", nowMillis)
            put("nonce", nonce)
        }.toString()

        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveSecretKey(cleanRoom, cleanKey), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(buildAad(cleanRoom, canonical))
        val sealed = cipher.doFinal(plaintextJson.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val envelope = ENVELOPE_PREFIX + encoder.encodeToString(iv) + "." + encoder.encodeToString(sealed)

        return JSONObject().put("envelope", envelope)
    }

    fun decryptPayload(
        roomId: String,
        roomKey: String,
        channel: String,
        wirePayload: JSONObject?,
        maxContentBytes: Int = MAX_IMPORTED_JSON_BYTES,
        nowMillis: Long = System.currentTimeMillis(),
        replayGuard: LanSyncReplayGuard? = null
    ): DecryptedRemoteSyncPayload {
        val canonical = canonicalChannel(channel)
            ?: throw RemoteSyncCryptoException("不支持的同步数据类型: $channel")
        if (wirePayload == null) {
            throw RemoteSyncCryptoException("同步载荷为空")
        }
        val cleanRoom = normalizeRoomId(roomId)
        val cleanKey = normalizeRoomKey(roomKey)
        val envelope = wirePayload.optString("envelope", "").trim()
        if (!isEncryptedEnvelope(envelope)) {
            throw RemoteSyncCryptoException("拒绝未加密的远程同步数据")
        }
        val maxEnvelopeChars = ((maxContentBytes.toLong() * 4L) / 3L + 4_096L)
            .coerceAtMost(16L * 1024L * 1024L)
        if (envelope.length > maxEnvelopeChars) {
            throw RemoteSyncCryptoException("同步内容超过大小限制")
        }

        val parts = envelope.removePrefix(ENVELOPE_PREFIX).split(".")
        if (parts.size != 2 || parts.any { it.isEmpty() }) {
            throw RemoteSyncCryptoException("远程同步密文格式无效")
        }
        val iv = decodeBase64Url(parts[0])
        val sealed = decodeBase64Url(parts[1])
        if (iv.size != IV_BYTES) {
            throw RemoteSyncCryptoException("远程同步密文 IV 无效")
        }

        val decryptedText = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, deriveSecretKey(cleanRoom, cleanKey), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(buildAad(cleanRoom, canonical))
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw RemoteSyncCryptoException("远程同步数据解密失败，请核对房间配对码")
        }

        val inner = try {
            JSONObject(decryptedText)
        } catch (e: Exception) {
            throw RemoteSyncCryptoException("解密后的同步数据格式无效")
        }
        if (!inner.has("content")) {
            throw RemoteSyncCryptoException("解密后的同步数据缺少内容字段")
        }
        val ts = inner.optLong("ts", Long.MIN_VALUE)
        if (
            !inner.has("ts") ||
            ts == Long.MIN_VALUE ||
            ts < nowMillis - MAX_CLOCK_SKEW_MS ||
            ts > nowMillis + MAX_CLOCK_SKEW_MS
        ) {
            throw RemoteSyncCryptoException("远程同步请求已过期")
        }
        val nonce = inner.optString("nonce", "").trim()
        if (nonce.isEmpty() || nonce.length > 64) {
            throw RemoteSyncCryptoException("远程同步请求缺少有效随机凭证")
        }
        if (replayGuard != null && !replayGuard.accept("$cleanRoom:$canonical:$nonce", nowMillis)) {
            throw RemoteSyncCryptoException("拒绝重放的远程同步数据")
        }
        val overlay = inner.optBoolean("overlay", false)
        val content = inner.optString("content", "")
        if (content.toByteArray(Charsets.UTF_8).size > maxContentBytes) {
            throw RemoteSyncCryptoException("同步内容超过大小限制")
        }
        return DecryptedRemoteSyncPayload(overlay = overlay, content = content)
    }

    fun clearCachedKeys() {
        keyCache.clear()
    }

    private fun buildAad(cleanRoomId: String, canonicalChannel: String): ByteArray {
        return "$AAD_PREFIX|room=$cleanRoomId|channel=$canonicalChannel".toByteArray(Charsets.UTF_8)
    }

    private fun deriveSecretKey(cleanRoomId: String, cleanRoomKey: String): SecretKeySpec {
        val cacheId = "$cleanRoomId:$cleanRoomKey"
        keyCache[cacheId]?.let { return it }

        val salt = MessageDigest.getInstance("SHA-256")
            .digest((KDF_DOMAIN_PREFIX + cleanRoomId).toByteArray(Charsets.UTF_8))
        val spec = PBEKeySpec(cleanRoomKey.toCharArray(), salt, KDF_ITERATIONS, KEY_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        } catch (e: GeneralSecurityException) {
            throw RemoteSyncCryptoException("无法派生远程同步加密密钥")
        } finally {
            spec.clearPassword()
        }

        val secretKey = SecretKeySpec(keyBytes, "AES")
        keyBytes.fill(0)
        if (keyCache.size >= MAX_CACHED_KEYS) {
            keyCache.clear()
        }
        keyCache[cacheId] = secretKey
        return secretKey
    }

    private fun decodeBase64Url(value: String): ByteArray = try {
        Base64.getUrlDecoder().decode(value)
    } catch (e: IllegalArgumentException) {
        throw RemoteSyncCryptoException("远程同步密文编码无效")
    }

    private fun parseQueryParam(rawQuery: String?, name: String): String? {
        if (rawQuery.isNullOrBlank()) return null
        for (pair in rawQuery.split("&")) {
            val kv = pair.split("=", limit = 2)
            if (kv.size == 2 && kv[0].equals(name, ignoreCase = true)) {
                return kv[1]
            }
        }
        return null
    }
}
