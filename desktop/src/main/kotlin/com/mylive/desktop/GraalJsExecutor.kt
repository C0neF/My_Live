package com.mylive.desktop

import com.mylive.app.core.script.JsExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Source
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
    private val engineLock = Any()
    private val engineRef = AtomicReference<Engine?>(null)
    private val sourceLock = Any()
    private val parsedSources = object : LinkedHashMap<String, Source>(MAX_CACHED_SOURCES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Source>?): Boolean =
            size > MAX_CACHED_SOURCES
    }
    private val closed = AtomicBoolean(false)

    private companion object {
        private const val MAX_CACHED_SOURCES = 32
        private const val RETRY_MIN_TIMEOUT_MILLIS = 20_000L
    }

    private fun getOrCreateEngine(): Engine {
        engineRef.get()?.let { return it }
        synchronized(engineLock) {
            engineRef.get()?.let { return it }
            return Engine.newBuilder("js")
                .out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream())
                .option("engine.WarnInterpreterOnly", "false")
                .build()
                .also { engineRef.set(it) }
        }
    }

    private fun getOrBuildSource(script: String): Source = synchronized(sourceLock) {
        parsedSources[script] ?: Source.newBuilder("js", script, "mylive-sign-${script.hashCode()}.js")
            .cached(true)
            .build()
            .also { parsedSources[script] = it }
    }

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
            val effectiveTimeoutMillis = if (attempts > 1) maxOf(timeoutMillis, RETRY_MIN_TIMEOUT_MILLIS) else timeoutMillis
            val deadline = timer.schedule({
                timedOut.set(true)
                cancelled.set(true)
                running.get()?.close(true)
            }, effectiveTimeoutMillis, TimeUnit.MILLISECONDS)
            continuation.invokeOnCancellation {
                cancelled.set(true)
                running.get()?.let { context ->
                    runCatching { timer.execute { runCatching { context.close(true) } } }
                }
            }
            val submitted = runCatching {
                worker.execute {
                    var engine: Engine? = null
                    try {
                        check(!cancelled.get() && !closed.get()) { "Signing cancelled" }
                        val activeEngine = getOrCreateEngine()
                        engine = activeEngine
                        val created = Context.newBuilder("js")
                            .engine(activeEngine)
                            .allowHostAccess(HostAccess.NONE)
                            .allowHostClassLookup { false }
                            .allowIO(IOAccess.NONE)
                            .allowCreateThread(false)
                            .allowNativeAccess(false)
                            .out(OutputStream.nullOutputStream())
                            .err(OutputStream.nullOutputStream())
                            .build()
                        active.add(created)
                        running.set(created)
                        try {
                            check(!cancelled.get() && !closed.get()) { "Signing cancelled" }
                            created.eval(getOrBuildSource(script))
                            val function = created.getBindings("js").getMember(functionName)
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
                            active.remove(created)
                            created.close(true)
                            if (cancelled.get() || timedOut.get()) {
                                if (engineRef.compareAndSet(activeEngine, null)) {
                                    activeEngine.close(true)
                                }
                            }
                        }
                    } catch (error: Throwable) {
                        if ((cancelled.get() || timedOut.get()) && engine != null) {
                            if (engineRef.compareAndSet(engine, null)) {
                                runCatching { engine.close(true) }
                            }
                        }
                        if (continuation.isActive) continuation.resumeWithException(
                            if (timedOut.get()) IllegalStateException("Signing exceeded its deadline") else error
                        )
                    } finally {
                        deadline.cancel(false)
                    }
                }
            }
            submitted.onFailure { error ->
                deadline.cancel(false)
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        active.forEach { it.close(true) }
        engineRef.getAndSet(null)?.close(true)
        synchronized(sourceLock) { parsedSources.clear() }
        worker.shutdownNow()
        timer.shutdownNow()
    }
}
