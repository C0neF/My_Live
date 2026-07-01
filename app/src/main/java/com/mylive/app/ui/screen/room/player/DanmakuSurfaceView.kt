package com.mylive.app.ui.screen.room.player

import android.content.Context
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import timber.log.Timber

class DanmakuSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    val controller = DanmakuController(context)
    private var renderThread: RenderThread? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val thread = renderThread
            if (thread != null && thread.running) {
                thread.onVsync(frameTimeNanos)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    init {
        // Essential configurations to make SurfaceView background transparent
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Timber.d("DanmakuSurfaceView created")
        renderThread = RenderThread(holder, controller).apply {
            running = true
            start()
        }
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Timber.d("DanmakuSurfaceView changed: ${width}x${height}")
        controller.width = width
        controller.height = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Timber.d("DanmakuSurfaceView destroyed")
        // Stop the render thread, but keep the controller alive: the surface can be recreated
        // (rotation, visibility) on the same view, which restarts the render thread.
        stopRenderThread()
    }

    /**
     * Permanently release this view's resources. Call from AndroidView.onRelease when the
     * composable leaves composition for good (this also cancels the controller's IO scope).
     */
    fun release() {
        stopRenderThread()
        controller.release()
    }

    private fun stopRenderThread() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
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
        }, "DanmakuRenderThreadStopper").apply {
            isDaemon = true
            start()
        }
    }

    private class RenderThread(
        private val surfaceHolder: SurfaceHolder,
        private val controller: DanmakuController
    ) : Thread("DanmakuRenderThread") {

        @Volatile var running = false
        private val frameSignal = java.lang.Object()
        @Volatile private var hasFrameSignal = false
        @Volatile private var frameTimeNanos = 0L

        /** Signal a new vsync frame (carries the Choreographer frame time). */
        fun onVsync(nanos: Long) {
            synchronized(frameSignal) {
                frameTimeNanos = nanos
                hasFrameSignal = true
                frameSignal.notifyAll()
            }
        }

        /** Wake the thread without a real frame (used to unblock it for shutdown). */
        fun wake() {
            synchronized(frameSignal) {
                hasFrameSignal = true
                frameSignal.notifyAll()
            }
        }

        override fun run() {
            while (running) {
                val nanos: Long
                synchronized(frameSignal) {
                    while (!hasFrameSignal && running) {
                        try {
                            frameSignal.wait()
                        } catch (e: InterruptedException) {
                            return
                        }
                    }
                    hasFrameSignal = false
                    nanos = frameTimeNanos
                }
                if (!running) break

                try {
                    val c = surfaceHolder.lockCanvas() ?: continue
                    try {
                        synchronized(surfaceHolder) {
                            controller.draw(c, nanos)
                        }
                    } finally {
                        surfaceHolder.unlockCanvasAndPost(c)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error in danmaku render loop")
                }
            }
        }
    }
}
