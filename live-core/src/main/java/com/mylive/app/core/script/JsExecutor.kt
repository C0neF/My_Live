package com.mylive.app.core.script

/** Signing scripts share their contract while each platform owns its JS runtime and assets. */
interface JsExecutor {
    suspend fun execute(scriptName: String, functionName: String, vararg args: String): String
    suspend fun executeRaw(script: String, functionName: String, vararg args: String): String
    suspend fun executeWithRetry(
        scriptName: String,
        functionName: String,
        args: Array<String>,
        maxAttempts: Int,
        isValid: (String) -> Boolean
    ): String
    fun loadAssetScript(scriptName: String): String
}
