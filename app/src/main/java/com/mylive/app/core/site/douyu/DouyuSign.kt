package com.mylive.app.core.site.douyu

import com.mylive.app.core.script.JsEngine

/**
 * Douyu signing helper.
 * Combines a bundled CryptoJS library (loaded from assets/scripts/cryptojs.js)
 * with room-specific JS from the homeH5Enc API, executes the signing function
 * [ub98484234] via [JsEngine], and returns the signed parameters as a query-string-style value.
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
     * @return A query-string-style signing result.
     */
    suspend fun getSign(roomJs: String, roomId: String, jsEngine: JsEngine): String {
        val time = (System.currentTimeMillis() / 1000).toString()
        val cryptoJs = jsEngine.loadAssetScript(CRYPTOJS_SCRIPT)
        val script = "$cryptoJs\n$roomJs"
        return jsEngine.executeRaw(script, "ub98484234", roomId, DID, time)
    }
}
