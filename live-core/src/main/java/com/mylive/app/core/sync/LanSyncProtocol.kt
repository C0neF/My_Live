package com.mylive.app.core.sync

import org.json.JSONObject

enum class LanSyncImportType(val path: String, val wireName: String) {
    FOLLOW("/sync/follow", "follow"),
    TAG("/sync/tag", "tag"),
    HISTORY("/sync/history", "history"),
    BLOCKED_WORD("/sync/blocked_word", "blocked_word"),
    PROFILE("/sync/profile", "profile");

    companion object {
        fun fromPath(path: String): LanSyncImportType? = entries.firstOrNull { it.path == path }
    }
}

sealed interface LanSyncDiscoveryMessage {
    val deviceId: String

    data class Hello(
        override val deviceId: String
    ) : LanSyncDiscoveryMessage

    data class Peer(
        override val deviceId: String,
        val name: String,
        val platform: String,
        val protocol: String
    ) : LanSyncDiscoveryMessage
}

data class LanSyncPeerInfo(
    val protocol: String,
    val paired: Boolean,
    val deviceId: String?,
    val name: String?,
    val platform: String,
    val app: String,
    val appVersion: String?,
    val address: String?,
    val port: Int,
    val supportsFollowBundle: Boolean = false
)

data class LanSyncAcceptedJob(val jobUrl: String)

enum class LanSyncJobState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED
}

data class LanSyncJobStatus(
    val state: LanSyncJobState,
    val message: String
)

class LanSyncProtocolException(message: String) : IllegalArgumentException(message)

/**
 * Single source of truth for LAN sync wire names, message parsing, compatibility,
 * and response decoding.
 */
object LanSyncProtocol {
    const val CURRENT_PROTOCOL = "MLSYNC2"
    const val INFO_PATH = "/info"
    const val JOB_PATH_PREFIX = "/sync/job/"

    fun encodeDiscoveryHello(deviceId: String): String {
        return JSONObject().apply {
            put("id", deviceId)
            put("type", "hello")
            put("protocol", CURRENT_PROTOCOL)
        }.toString()
    }

    fun encodeDiscoveryPeer(
        deviceId: String,
        name: String,
        platform: String = "android"
    ): String {
        return JSONObject().apply {
            put("id", deviceId)
            put("type", platform)
            put("name", name)
            put("protocol", CURRENT_PROTOCOL)
        }.toString()
    }

    fun decodeDiscoveryMessage(body: String): LanSyncDiscoveryMessage? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val payload = try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            return null
        }
        val deviceId = payload.optString("id").trim()
        val type = payload.optString("type").trim()
        if (deviceId.isEmpty() || type.isEmpty()) return null
        return if (type == "hello") {
            LanSyncDiscoveryMessage.Hello(deviceId)
        } else {
            LanSyncDiscoveryMessage.Peer(
                deviceId = deviceId,
                name = payload.optString("name"),
                platform = type,
                protocol = payload.optString("protocol")
            )
        }
    }

    /**
     * Encodes the `/info` answer.
     *
     * An unpaired caller learns only the protocol version — enough to negotiate compatibility
     * when connecting to a peer by IP, and nothing more. The app name and platform used to be in
     * this branch too, which handed every port scanner on the network a positive identification
     * of the app; that is exactly the fingerprint the paired-only fields below were moved behind.
     */
    fun encodePeerInfo(
        paired: Boolean,
        port: Int,
        deviceId: String? = null,
        name: String? = null,
        appVersion: String? = null,
        address: String? = null,
        supportsFollowBundle: Boolean = false
    ): String {
        return JSONObject().apply {
            put("protocol", CURRENT_PROTOCOL)
            put("paired", paired)
            if (paired) {
                put("type", "android")
                put("app", "My Live")
                put("port", port)
                put("id", deviceId)
                put("name", name)
                put("version", appVersion)
                put("address", address)
                put("follow_bundle", supportsFollowBundle)
            }
        }.toString()
    }

    fun decodePeerInfo(body: String): LanSyncPeerInfo {
        val payload = try {
            JSONObject(body)
        } catch (_: Exception) {
            throw LanSyncProtocolException("对方返回的同步信息格式无效")
        }
        val protocol = payload.optString("protocol").trim()
        requireCompatible(protocol)
        val paired = payload.optBoolean("paired", false)
        return LanSyncPeerInfo(
            protocol = protocol,
            paired = paired,
            deviceId = payload.optionalString("id"),
            name = payload.optionalString("name"),
            platform = payload.optString("type", "android"),
            app = payload.optString("app", "My Live"),
            appVersion = payload.optionalString("version"),
            address = payload.optionalString("address"),
            port = payload.optInt("port", 0),
            supportsFollowBundle = paired && payload.optBoolean("follow_bundle", false)
        )
    }

    fun requireCompatible(protocol: String) {
        if (protocol != CURRENT_PROTOCOL) {
            val remote = protocol.ifBlank { "legacy/unknown" }
            throw LanSyncProtocolException(
                "对方同步协议不兼容：本机 $CURRENT_PROTOCOL，对方 $remote"
            )
        }
    }

    /** Default 401 text, used when the peer did not tell us anything more specific. */
    const val PAIRING_REQUIRED_MESSAGE =
        "未配对或配对码错误，请扫描对方二维码或填写配对码"

    /**
     * Builds the error for an HTTP 401, preserving the reason the peer gave.
     *
     * The service rejects three genuinely different things with 401 (see `LanSyncAuth`): a bad
     * pairing code, a request outside the accepted clock skew, and a replayed nonce. Reporting
     * all three as "wrong pairing code" sent users to re-scan a QR code, which cannot fix a clock
     * that is a minute off — so the peer's own reason is translated instead of discarded.
     */
    fun unauthorizedException(body: String?): LanSyncProtocolException {
        val serverMessage = body
            ?.trim()
            ?.takeIf { it.startsWith("{") }
            ?.let { runCatching { JSONObject(it).optString("message").trim() }.getOrNull() }
            .orEmpty()

        val message = when {
            serverMessage.contains("stale") ->
                "对方设备与本机时间相差过大，同步请求被拒绝，请校准两台设备的系统时间后重试"
            serverMessage.contains("replay") ->
                "同步请求被判定为重复提交，请稍后重试"
            else -> PAIRING_REQUIRED_MESSAGE
        }
        return LanSyncProtocolException(message)
    }

    fun decodeImportResponse(
        statusCode: Int,
        isSuccessful: Boolean,
        body: String?
    ): LanSyncAcceptedJob? {
        if (statusCode == 401) {
            throw unauthorizedException(body)
        }

        val trimmedBody = body?.trim().orEmpty()
        val payload = if (trimmedBody.startsWith("{")) {
            try {
                JSONObject(trimmedBody)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        if (payload?.has("status") == true && !payload.optBoolean("status", true)) {
            throw LanSyncProtocolException(payload.optString("message", "sync failed"))
        }
        if (!isSuccessful) {
            throw LanSyncProtocolException("HTTP $statusCode")
        }
        if (statusCode == 202) {
            val jobUrl = payload?.optString("jobUrl")?.trim().orEmpty()
            if (jobUrl.isEmpty()) {
                throw LanSyncProtocolException("sync accepted without job url")
            }
            return LanSyncAcceptedJob(jobUrl)
        }
        return null
    }

    fun decodeJobStatus(body: String): LanSyncJobStatus {
        val payload = try {
            JSONObject(body)
        } catch (_: Exception) {
            throw LanSyncProtocolException("对方返回的同步任务状态无效")
        }
        if (payload.has("status") && !payload.optBoolean("status", true)) {
            throw LanSyncProtocolException(payload.optString("message", "sync failed"))
        }
        val state = when (payload.optString("state")) {
            "queued" -> LanSyncJobState.QUEUED
            "running" -> LanSyncJobState.RUNNING
            "succeeded" -> LanSyncJobState.SUCCEEDED
            "failed" -> LanSyncJobState.FAILED
            else -> throw LanSyncProtocolException("未知同步状态")
        }
        return LanSyncJobStatus(
            state = state,
            message = payload.optString("message", "sync failed")
        )
    }

    private fun JSONObject.optionalString(name: String): String? {
        return optString(name).trim().takeIf { it.isNotEmpty() }
    }
}
