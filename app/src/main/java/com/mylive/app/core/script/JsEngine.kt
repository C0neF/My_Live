package com.mylive.app.core.script

import android.content.Context
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JavaScript engine wrapper for executing signing scripts. Uses QuickJS.
 *
 * The native QuickJS runtime is THREAD-AFFINE — each context must only ever be touched from
 * the thread that created it. Every context/eval/call/close operation is therefore confined
 * to a single dedicated thread via [dispatcher].
 *
 * (The previous implementation merely `synchronized` on the runtime while running on the
 * shared `Dispatchers.IO` pool. That serialized calls, but still invoked the native runtime
 * from many different OS threads, which can corrupt QuickJS state or crash with a native
 * `java.lang.Error` — the bug this rewrite fixes.)
 */
@Singleton
class JsEngine @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext

    // Single dedicated thread that owns the QuickJS runtime and all of its contexts.
    private val jsThread = Executors.newSingleThreadExecutor { r ->
        Thread(r, "quickjs").apply { isDaemon = true }
    }
    private val dispatcher = jsThread.asCoroutineDispatcher()

    // Touched ONLY on the quickjs thread — no synchronization needed.
    private var loaderInitialized = false

    // Asset script sources, cached to avoid repeated reads. Safe to touch from any thread.
    private val scriptCache = ConcurrentHashMap<String, String>()

    /**
     * Execute a JS function from a script file in assets/scripts/.
     * @return the function's String result (throws if it returns null).
     */
    suspend fun execute(scriptName: String, functionName: String, vararg args: String): String =
        withContext(dispatcher) {
            evalAndCall(loadAssetScript(scriptName), functionName, args)
        }

    /**
     * Execute a JS function from a raw script string (e.g. Douyu's dynamically fetched signer).
     */
    suspend fun executeRaw(script: String, functionName: String, vararg args: String): String =
        withContext(dispatcher) {
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
    ): String = withContext(dispatcher) {
        val ctx = createContext()
        try {
            ctx.evaluate(loadAssetScript(scriptName), scriptName)
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

    // ── quickjs-thread-only helpers ──────────────────────────────────────────

    private fun createContext(): QuickJSContext {
        ensureLoaderInitialized()
        return QuickJSContext.create()
    }

    private fun ensureLoaderInitialized() {
        if (!loaderInitialized) {
            QuickJSLoader.init()
            loaderInitialized = true
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
        return try {
            val result = function.call(*args)
            result?.toString()
                ?: throw IllegalStateException("JS function '$functionName' returned null")
        } finally {
            function.release()
            global.release()
        }
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
        jsThread.shutdown()
    }
}
