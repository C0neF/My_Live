package com.mylive.app.core.site.douyin

import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.script.JsEngine
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Wrapper around [JsEngine] for Douyin-specific URL signing and signature generation.
 *
 * Two JS scripts are loaded from assets:
 * - `douyin_abogus.js` (~641 KB) -- generates A-Bogus URL parameters
 * - `douyin_webmssdk.js` -- generates WebSocket signatures for the danmaku connection
 *
 * Both are executed via QuickJS through [JsEngine].
 */
class DouyinSign(private val jsEngine: JsEngine) {

    companion object {
        private const val ABOGUS_SCRIPT = "douyin_abogus.js"
        private const val WEBMSSDK_SCRIPT = "douyin_webmssdk.js"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.97 Safari/537.36 Core/1.116.567.400 QQBrowser/19.7.6764.400"
    }

    private val random = SecureRandom()

    /**
     * Generate an A-Bogus signed URL.
     *
     * Appends `msToken` and `a_bogus` query parameters to the given URL
     * by executing the A-Bogus JavaScript signing algorithm.
     *
     * @param url The original request URL
     * @param userAgent The User-Agent string to use in the signing algorithm
     * @return The URL with msToken and a_bogus parameters appended
     */
    suspend fun getAbogusUrl(url: String, userAgent: String): String {
        return try {
            val msToken = generateMsToken(107)
            val params = "$url&msToken=$msToken"
            val query = if (params.contains("?")) {
                params.substringAfter("?")
            } else {
                params
            }
            val aBogus = jsEngine.execute(ABOGUS_SCRIPT, "getABogus", query, userAgent)
            "$url&msToken=${java.net.URLEncoder.encode(msToken, "UTF-8")}&a_bogus=${java.net.URLEncoder.encode(aBogus, "UTF-8")}"
        } catch (e: Throwable) {
            CoreLog.e("[DouyinSign] getAbogusUrl failed", e)
            url
        }
    }

    /**
     * Generate a WebSocket signature for the danmaku connection.
     *
     * Uses the MS-SDK signing algorithm to produce a signature string that
     * does NOT contain '-' or '=' characters (retries if it does).
     *
     * @param roomId The room ID
     * @param uniqueId The user unique ID
     * @return A valid signature string
     */
    suspend fun getSignature(roomId: String, uniqueId: String): String {
        return try {
            val msStub = getMsStub(roomId, uniqueId)
            // Evaluate the (large) webmssdk script once and retry the call within one context.
            // A valid signature must not contain '-' or '=' (URL-safe requirement).
            jsEngine.executeWithRetry(
                scriptName = WEBMSSDK_SCRIPT,
                functionName = "getMSSDKSignature",
                args = arrayOf(msStub, DEFAULT_USER_AGENT),
                maxAttempts = 11,
                isValid = { sig -> !sig.contains('-') && !sig.contains('=') }
            )
        } catch (e: Throwable) {
            CoreLog.e("[DouyinSign] getSignature failed", e)
            ""
        }
    }

    /**
     * Compute the MS-Stub value (MD5 hash of serialized parameters).
     *
     * The parameters are joined as "key=value" pairs separated by commas,
     * then hashed with MD5.
     *
     * @param roomId The room ID
     * @param uniqueId The user unique ID
     * @return Lowercase hex MD5 hash string
     */
    fun getMsStub(roomId: String, uniqueId: String): String {
        val params = linkedMapOf(
            "live_id" to "1",
            "aid" to "6383",
            "version_code" to "180800",
            "webcast_sdk_version" to "1.3.0",
            "room_id" to roomId,
            "sub_room_id" to "",
            "sub_channel_id" to "",
            "did_rule" to "3",
            "user_unique_id" to uniqueId,
            "device_platform" to "web",
            "device_type" to "",
            "ac" to "",
            "identity" to "audience"
        )
        val sigParams = params.entries.joinToString(",") { "${it.key}=${it.value}" }
        return md5(sigParams)
    }

    /**
     * Generate a random alphanumeric token of the given length.
     *
     * @param length The desired token length
     * @return A random string of [length] alphanumeric characters
     */
    fun generateMsToken(length: Int): String {
        val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(length) {
            repeat(length) {
                append(characters[random.nextInt(characters.length)])
            }
        }
    }

    /**
     * Compute the MD5 hash of a string and return it as a lowercase hex string.
     */
    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
