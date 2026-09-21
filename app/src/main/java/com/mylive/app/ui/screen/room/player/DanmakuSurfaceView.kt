package com.mylive.app.ui.screen.room.player

import android.content.Context
import android.graphics.PixelFormat
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

class DanmakuSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {
    val controller = DanmakuController(context)
    private var renderThread: RenderThread? = null
    private var frameCallbackPosted = false
    private val requestPosted = AtomicBoolean()
    private var wakeupAtMillis: Long? = null
    private val postedRequest = Runnable {
        requestPosted.set(false)
        requestFrame()
    }
    private val delayedRequest = Runnable {
        wakeupAtMillis = null
        requestFrame()
    }
    private val frameCallback = Choreographer.FrameCallback { nanos ->
        frameCallbackPosted = false
        renderThread?.takeIf { it.running }?.onVsync(nanos)
    }

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
        controller.setFrameRequester { requestFrame() }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        renderThread = RenderThread(holder, controller) { requestFrame() }.apply {
            running = true
            start()
        }
        requestFrame(force = true)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        controller.updateViewport(width, height)
        requestFrame(force = true)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) { stopRenderThread() }

    fun release() {
        stopRenderThread()
        controller.setFrameRequester(null)
        controller.release()
    }

    private fun requestFrame(force: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            if (requestPosted.compareAndSet(false, true)) post(postedRequest)
            return
        }
        val thread = renderThread
        if (thread == null || !thread.running) return
        val delayMillis = if (force) 0L else controller.nextFrameDelayMillis() ?: return
        if (delayMillis > 0L) {
            if (frameCallbackPosted) return
            val nextWakeup = SystemClock.uptimeMillis() + delayMillis
            if (wakeupAtMillis?.let { it <= nextWakeup } == true) return
            removeCallbacks(delayedRequest)
            wakeupAtMillis = nextWakeup
            postDelayed(delayedRequest, delayMillis)
            return
        }
        removeCallbacks(delayedRequest)
        wakeupAtMillis = null
        if (frameCallbackPosted) return
        frameCallbackPosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopRenderThread() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        removeCallbacks(delayedRequest)
        removeCallbacks(postedRequest)
        frameCallbackPosted = false
        wakeupAtMillis = null
        requestPosted.set(false)
        val stoppedThread = renderThread
        renderThread = null
        stoppedThread?.let {
            it.running = false
            it.wake()
            joinStoppedRenderThread(it)
        }
    }

    private fun joinStoppedRenderThread(thread: Thread) {
        Thread({
            try {
                thread.join(1000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Timber.e(e, "Failed to stop danmaku thread")
            }
        }, "DanmakuRenderThreadStopper").apply { isDaemon = true; start() }
    }

    private class RenderThread(
        private val surfaceHolder: SurfaceHolder,
        private val controller: DanmakuController,
        private val onFrameFinished: () -> Unit
    ) : Thread("DanmakuRenderThread") {
        @Volatile var running = false
        private val frameSignal = java.lang.Object()
        private var hasFrameSignal = false
        private var frameTimeNanos = 0L

        fun onVsync(nanos: Long) = synchronized(frameSignal) {
            frameTimeNanos = nanos
            hasFrameSignal = true
            frameSignal.notifyAll()
        }

        fun wake() = synchronized(frameSignal) {
            hasFrameSignal = true
            frameSignal.notifyAll()
        }

        override fun run() {
            while (running) {
                val nanos: Long
                synchronized(frameSignal) {
                    while (!hasFrameSignal && running) {
                        try { frameSignal.wait() } catch (_: InterruptedException) { return }
                    }
                    hasFrameSignal = false
                    nanos = frameTimeNanos
                }
                if (!running) break
                try {
                    val canvas = surfaceHolder.lockCanvas() ?: continue
                    try {
                        synchronized(surfaceHolder) { controller.draw(canvas, nanos) }
                    } finally {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error in danmaku render loop")
                } finally {
                    // Schedule after the hand-off is consumed, avoiding a lost wakeup when
                    // pending becomes empty just before activeCount is published.
                    if (running && controller.needsFrame()) onFrameFinished()
                }
            }
        }
    }
}
