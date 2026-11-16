package com.mylive.desktop

import com.mylive.app.core.script.JsExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.io.IOAccess
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Each signing operation owns its context; cancellation stops guest execution, including loops. */
class GraalJsExecutor(private val timeoutMillis: Long = 8_000) : JsExecutor, AutoCloseable {
    private val permits = Semaphore(1)
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "mylive-js").apply { isDaemon = true }
    }
    private val timer = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "mylive-js-deadline").apply { isDaemon = true }
    }
    private val active = ConcurrentHashMap.newKeySet<Context>()
    private val scripts = ConcurrentHashMap<String, String>()
    private val closed = AtomicBoolean(false)

    override fun loadAssetScript(scriptName: String): String {
        require(scriptName.matches(Regex("[a-zA-Z0-9_]+\\.js"))) { "Invalid script name" }
        return scripts.computeIfAbsent(scriptName) {
            javaClass.getResourceAsStream("/scripts/$scriptName")?.bufferedReader()?.use { it.readText() }
                ?: error("Bundled script missing: $scriptName")
        }
    }

    override suspend fun execute(scriptName: String, functionName: String, vararg args: String): String =
        evaluate(loadAssetScript(scriptName), functionName, args, 1) { true }

    override suspend fun executeRaw(script: String, functionName: String, vararg args: String): String =
        evaluate(script, functionName, args, 1) { true }

    override suspend fun executeWithRetry(
        scriptName: String, functionName: String, args: Array<String>, maxAttempts: Int,
        isValid: (String) -> Boolean
    ): String {
        require(maxAttempts > 0)
        return evaluate(loadAssetScript(scriptName), functionName, args, maxAttempts, isValid)
    }

    private suspend fun evaluate(
        script: String, functionName: String, args: Array<out String>, attempts: Int,
        isValid: (String) -> Boolean
    ): String = permits.withPermit {
        check(!closed.get()) { "JavaScript executor is closed" }
        suspendCancellableCoroutine { continuation ->
            val running = AtomicReference<Context?>()
            val cancelled = AtomicBoolean(false)
            val timedOut = AtomicBoolean(false)
            val deadline = timer.schedule({
                timedOut.set(true)
                cancelled.set(true)
                running.get()?.close(true)
            }, timeoutMillis, TimeUnit.MILLISECONDS)
            continuation.invokeOnCancellation {
                cancelled.set(true)
                running.get()?.let { context -> timer.execute { context.close(true) } }
            }
            worker.execute {
                try {
                    val context = Context.newBuilder("js")
                        .allowHostAccess(HostAccess.NONE)
                        .allowHostClassLookup { false }
                        .allowIO(IOAccess.NONE)
                        .allowCreateThread(false)
                        .allowNativeAccess(false)
                        .out(OutputStream.nullOutputStream())
                        .err(OutputStream.nullOutputStream())
                        .option("engine.WarnInterpreterOnly", "false")
                        .build()
                    active.add(context)
                    running.set(context)
                    try {
                        check(!cancelled.get() && !closed.get()) { "Signing cancelled" }
                        context.eval("js", script)
                        val function = context.getBindings("js").getMember(functionName)
                        check(function?.canExecute() == true) { "Signing function missing" }
                        var result = ""
                        repeat(attempts) {
                            val value = function.execute(*args)
                            check(!value.isNull) { "Signing returned null" }
                            result = if (value.isString) value.asString() else value.toString()
                            if (isValid(result)) {
                                if (continuation.isActive) continuation.resume(result)
                                return@execute
                            }
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } finally {
                        running.set(null)
                        active.remove(context)
                        context.close(true)
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(
                        if (timedOut.get()) IllegalStateException("Signing exceeded its deadline") else error
                    )
                } finally {
                    deadline.cancel(false)
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        active.forEach { it.close(true) }
        worker.shutdownNow()
        timer.shutdownNow()
    }
}
