package com.mylive.app.core.script

import android.content.Context
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JavaScript engine wrapper for executing signing scripts. Uses QuickJS.
 *
 * The native QuickJS runtime is THREAD-AFFINE — each context must only ever be touched from
 * the thread that created it. Every context/eval/call/close operation is therefore confined
 * to a single dedicated engine thread owned by [guard].
 *
 * (The original implementation merely `synchronized` on the runtime while running on the
 * shared `Dispatchers.IO` pool. That serialized calls, but still invoked the native runtime
 * from many different OS threads, which can corrupt QuickJS state or crash with a native
 * `java.lang.Error`.)
 *
 * Every call also runs under a hard deadline. Site-provided scripts (Douyu's signer) are remote
 * input: a hostile or simply broken script could spin forever inside native code, where the JVM
 * cannot interrupt it. [JsExecutionGuard] fails such a call fast and replaces the wedged thread so
 * later playback attempts are not blocked behind it. Remote scripts are additionally screened by
 * [RemoteScriptPolicy] before they are handed to the engine.
 */
@Singleton
class JsEngine @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext

    // Owns the single dedicated thread the QuickJS runtime lives on, and enforces deadlines.
    private val guard = JsExecutionGuard(threadNamePrefix = "quickjs")

    // QuickJSLoader.init() loads a process-global native library and may now be reached from a
    // replacement engine thread, so guard it explicitly.
    private val loaderLock = Any()
    private var loaderInitialized = false

    // Asset script sources, cached to avoid repeated reads. Safe to touch from any thread.
    private val scriptCache = ConcurrentHashMap<String, String>()

    /**
     * Execute a JS function from a script file in assets/scripts/.
     * @return the function's String result (throws if it returns null).
     */
    suspend fun execute(scriptName: String, functionName: String, vararg args: String): String {
        val script = loadAssetScriptOffThread(scriptName)
        return runOnEngineThread(BUNDLED_SCRIPT_TIMEOUT_MS, "$scriptName#$functionName") {
            evalAndCall(script, functionName, args)
        }
    }

    /**
     * Execute a JS function from a raw script string (e.g. Douyu's dynamically fetched signer).
     *
     * Callers that pass a script downloaded at runtime MUST screen it first with
     * [RemoteScriptPolicy.requireTrustedRemoteScript].
     */
    suspend fun executeRaw(script: String, functionName: String, vararg args: String): String =
        runOnEngineThread(REMOTE_SCRIPT_TIMEOUT_MS, "raw#$functionName") {
            evalAndCall(script, functionName, args)
        }

    /**
     * Evaluate the asset script ONCE, then repeatedly invoke [functionName] until [isValid]
     * accepts the result or [maxAttempts] is reached — all within a single context. This avoids
     * re-parsing a large script (e.g. the ~625 KB webmssdk) on every retry attempt.
     */
    suspend fun executeWithRetry(
        scriptName: String,
        functionName: String,
        args: Array<String>,
        maxAttempts: Int,
        isValid: (String) -> Boolean
    ): String {
        val script = loadAssetScriptOffThread(scriptName)
        return runOnEngineThread(RETRY_SCRIPT_TIMEOUT_MS, "$scriptName#$functionName x$maxAttempts") {
            val ctx = createContext()
            try {
                ctx.evaluate(script, scriptName)
                var result = callFunction(ctx, functionName, args)
                var attempts = 1
                while (!isValid(result) && attempts < maxAttempts) {
                    result = callFunction(ctx, functionName, args)
                    attempts++
                }
                result
            } finally {
                ctx.close()
            }
        }
    }

    /** Number of engine threads abandoned so far because a script exceeded its deadline. */
    val timedOutExecutionCount: Int
        get() = guard.quarantinedThreadCount

    // ── scheduling ───────────────────────────────────────────────────────────

    private suspend fun <T> runOnEngineThread(
        timeoutMs: Long,
        label: String,
        block: () -> T
    ): T = withContext(Dispatchers.IO) {
        guard.call(timeoutMs, label, block)
    }

    private suspend fun loadAssetScriptOffThread(scriptName: String): String =
        withContext(Dispatchers.IO) { loadAssetScript(scriptName) }

    // ── quickjs-thread-only helpers ──────────────────────────────────────────

    private fun createContext(): QuickJSContext {
        ensureLoaderInitialized()
        return QuickJSContext.create()
    }

    private fun ensureLoaderInitialized() {
        synchronized(loaderLock) {
            if (!loaderInitialized) {
                QuickJSLoader.init()
                loaderInitialized = true
            }
        }
    }

    private fun evalAndCall(script: String, functionName: String, args: Array<out String>): String {
        val ctx = createContext()
        return try {
            ctx.evaluate(script, "script.js")
            callFunction(ctx, functionName, args)
        } finally {
            // Closing the context releases every JS object created during the call.
            ctx.close()
        }
    }

    private fun callFunction(ctx: QuickJSContext, functionName: String, args: Array<out String>): String {
        val global = ctx.globalObject
        val function = global.getJSFunction(functionName)
            ?: throw IllegalStateException("JS function '$functionName' was not found")
        val result = function.call(*args)
        return result?.toString()
            ?: throw IllegalStateException("JS function '$functionName' returned null")
    }

    /**
     * Load a script file from assets/scripts/ (cached). Safe to call from any thread.
     */
    fun loadAssetScript(scriptName: String): String =
        scriptCache.getOrPut(scriptName) {
            appContext.assets.open("scripts/$scriptName").bufferedReader().readText()
        }

    fun clearCache() {
        scriptCache.clear()
    }

    /**
     * Release all resources. Call when the application is shutting down.
     */
    fun destroy() {
        scriptCache.clear()
        guard.shutdown()
    }

    companion object {
        /** Bundled asset scripts are trusted but can still be heavy (webmssdk is ~625 KB). */
        const val BUNDLED_SCRIPT_TIMEOUT_MS: Long = 10_000L

        /** Site-provided scripts get a tighter budget; the Douyu signer runs in milliseconds. */
        const val REMOTE_SCRIPT_TIMEOUT_MS: Long = 5_000L

        /** Retry loops evaluate once and then call repeatedly inside one context. */
        const val RETRY_SCRIPT_TIMEOUT_MS: Long = 20_000L
    }
}
