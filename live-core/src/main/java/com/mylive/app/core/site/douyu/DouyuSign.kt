package com.mylive.app.core.site.douyu

import com.mylive.app.core.script.JsExecutor
import com.mylive.app.core.script.RemoteScriptPolicy

/**
 * Douyu signing helper.
 * Combines a bundled CryptoJS library (loaded from assets/scripts/cryptojs.js)
 * with room-specific JS from the homeH5Enc API, executes the signing function
 * [ub98484234] via [JsExecutor], and returns the signed parameters as a query-string-style value.
 *
     * The room-specific part is remote code, so it is screened by [RemoteScriptPolicy] (allowlisted
     * HTTPS host, non-empty, size-bounded, optional SHA-256 pin) before evaluation. A rejected
     * script is not passed to [JsExecutor]; [getSign] returns null so the caller can fall back.
     */
object DouyuSign {

    private const val CRYPTOJS_SCRIPT = "cryptojs.js"

    /**
     * Fixed device ID used when calling the signing function.
     */
    private const val DID = "10000000000000000000000000001501"

    /**
     * Generate the signed parameters for a Douyu room.
     *
     * @param roomJs  Room-specific JS obtained from the homeH5Enc API
     * @param roomId  The numeric room ID as a string.
     * @param jsEngine  The JS engine used to evaluate the combined script.
     * @param sourceUrl  The URL [roomJs] was downloaded from; must be an allowlisted HTTPS host.
     * @param expectedSha256 optional SHA-256 pin of [roomJs]. A mismatch refuses evaluation.
     * @return A query-string-style signing result, or null when the remote script was rejected.
     */
    suspend fun getSign(
        roomJs: String,
        roomId: String,
        jsEngine: JsExecutor,
        sourceUrl: String,
        expectedSha256: String? = null
    ): String? {
        val trusted = RemoteScriptPolicy.trustedRemoteScriptOrNull(sourceUrl, roomJs, expectedSha256)
            ?: return null
        val time = (System.currentTimeMillis() / 1000).toString()
        val cryptoJs = jsEngine.loadAssetScript(CRYPTOJS_SCRIPT)
        val script = "$cryptoJs\n$trusted"
        return jsEngine.executeRaw(script, "ub98484234", roomId, DID, time)
    }
}
