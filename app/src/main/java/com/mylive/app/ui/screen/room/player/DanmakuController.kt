package com.mylive.app.ui.screen.room.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import coil.Coil
import coil.request.ImageRequest
import com.mylive.app.core.common.buildPlayerDanmakuDisplaySpans
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageSpan
import com.mylive.app.core.model.LiveMessageType
import com.mylive.app.ui.emoji.EmojiAtlasRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

sealed class DanmakuPart {
    data class Text(val content: String, val color: Int) : DanmakuPart()
    data class Image(val url: String, @Volatile var bitmap: Bitmap? = null) : DanmakuPart()
}

class DanmakuItem(
    val parts: List<DanmakuPart>,
    var x: Float,
    var y: Float,
    /** Pixels travelled per millisecond — framerate independent. */
    val speedPxPerMs: Float,
    var width: Float,
    val track: Int,
    val type: LiveMessageType
)

private class PendingDanmaku(val parts: List<DanmakuPart>, val type: LiveMessageType)

/**
 * Renders scrolling danmaku onto a SurfaceView canvas.
 *
 * Threading contract:
 *  - [addDanmaku] may be called from any thread (the danmaku/network collector). It only does
 *    de-duplication + parsing and hands work off through [pending]; it never touches Paint, the
 *    active list or per-track bookkeeping.
 *  - ALL rendering state (the [textPaint]/[strokePaint]/[imagePaint], reusable geometry, the
 *    [activeDanmakus] list and [lastDanmakusOnTrack]) is owned EXCLUSIVELY by the render thread
 *    inside [draw]. Pending items are assigned tracks and measured on the render thread.
 *  - Configuration fields are written on the UI thread and read on the render thread; they are
 *    `@Volatile` and applied via a cheap dirty-check so Typeface/Paint are reconfigured only when
 *    they change — never per frame.
 */
class DanmakuController(private val context: Context) {

    // Shared application-level Coil loader (NOT a per-instance loader with its own memory cache).
    private val imageLoader = Coil.imageLoader(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bitmapCache = LruCache<String, Bitmap>(50) // Cache 50 emoticons

    // Hand-off queue: produced by addDanmaku (any thread), consumed by the render thread.
    private val pending = ConcurrentLinkedQueue<PendingDanmaku>()

    // Render-thread-only state.
    private val activeDanmakus = ArrayList<DanmakuItem>()
    private val lastDanmakusOnTrack = arrayOfNulls<DanmakuItem>(MAX_TRACKS)
    private var lastFrameNanos = 0L

    // De-duplication window (touched on addDanmaku threads, guarded by its own lock).
    private val dedupeLock = Any()
    private val dedupeQueue = ArrayDeque<String>()
    private val dedupeSet = HashSet<String>()

    // Config options — written on UI thread, read on render thread.
    @Volatile var danmuSize = 16f // sp
    @Volatile var danmuSpeed = 1.0f // speed multiplier
    @Volatile var danmuArea = 0.8f // coverage percentage (0.0 to 1.0)
    @Volatile var danmuOpacity = 1.0f
    @Volatile var danmuFontWeight = 4 // 2=Light, 4=Normal, 6=Bold, 8=ExtraBold
    @Volatile var danmuStrokeWidth = 2.0f
    @Volatile var danmuHideScroll = false
    @Volatile var dedupeEnabled = false
    @Volatile var dedupeWindowSize = 20
    @Volatile var dedupeStrictMode = false
    @Volatile var danmuRenderEmoji = true

    // Canvas size (updated from SurfaceView surfaceChanged).
    @Volatile var width = 0
    @Volatile var height = 0

    @Volatile private var clearRequested = false

    // Render-thread-owned paints + reusable geometry. NEVER touched off the render thread.
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val srcRect = Rect()
    private val destRect = RectF()

    // Snapshot of the config the paints are currently configured for (render thread).
    private var cfgSize = -1f
    private var cfgWeight = -1
    private var cfgStroke = -1f
    private var cfgDensity = -1f

    private val density: Float get() = context.resources.displayMetrics.density

    /**
     * Add a new danmaku from a live message. Safe to call from any thread.
     */
    fun addDanmaku(msg: LiveMessage) {
        if (msg.type != LiveMessageType.CHAT) return
        if (danmuHideScroll) return

        // Repeat filter (de-duplication).
        if (dedupeEnabled) {
            val key = if (dedupeStrictMode) msg.message else "${msg.userName}:${msg.message}"
            synchronized(dedupeLock) {
                if (!dedupeSet.add(key)) return // duplicate
                dedupeQueue.addLast(key)
                while (dedupeQueue.size > dedupeWindowSize) {
                    dedupeSet.remove(dedupeQueue.removeFirst())
                }
            }
        }

        val parts = buildParts(msg)
        preloadImages(parts)
        pending.add(PendingDanmaku(parts, msg.type))
    }

    private fun buildParts(msg: LiveMessage): List<DanmakuPart> {
        val parts = ArrayList<DanmakuPart>()
        val defaultColor = Color.rgb(msg.color.r, msg.color.g, msg.color.b)
        for (span in buildPlayerDanmakuDisplaySpans(msg, danmuRenderEmoji)) {
            when (span) {
                is LiveMessageSpan.Text -> parts.add(DanmakuPart.Text(span.text, defaultColor))
                is LiveMessageSpan.Image -> {
                    parts.add(DanmakuPart.Image(span.imageUrl))
                }
            }
        }
        return parts
    }

    private fun preloadImages(parts: List<DanmakuPart>) {
        for (part in parts) {
            if (part !is DanmakuPart.Image) continue
            val cached = bitmapCache.get(part.url)
            if (cached != null) {
                part.bitmap = cached
            } else {
                val atlasBitmap = EmojiAtlasRepository.getBitmap(context, part.url)
                if (atlasBitmap != null) {
                    bitmapCache.put(part.url, atlasBitmap)
                    part.bitmap = atlasBitmap
                    continue
                }
                scope.launch {
                    val bitmap = loadBitmap(part.url)
                    if (bitmap != null) {
                        bitmapCache.put(part.url, bitmap)
                        part.bitmap = bitmap
                    }
                }
            }
        }
    }

    private suspend fun loadBitmap(url: String): Bitmap? {
        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // we draw the bitmap onto a software Canvas
                .build()
            (imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Request that all on-screen danmaku and the de-dup window be cleared. Safe to call from any
     * thread; the active list itself is cleared on the render thread on the next frame.
     */
    fun clear() {
        clearRequested = true
        pending.clear()
        synchronized(dedupeLock) {
            dedupeQueue.clear()
            dedupeSet.clear()
        }
    }

    /**
     * Release coroutine + bitmap resources. Call once when the view is permanently gone
     * (e.g. AndroidView.onRelease). The shared Coil [imageLoader] is intentionally NOT shut down.
     */
    fun release() {
        scope.cancel()
        pending.clear()
        bitmapCache.evictAll()
    }

    /**
     * Advance positions and draw all active danmaku. MUST be called only from the render thread.
     *
     * @param frameTimeNanos Choreographer frame time, used for framerate-independent motion.
     */
    fun draw(canvas: Canvas, frameTimeNanos: Long) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        if (clearRequested) {
            clearRequested = false
            activeDanmakus.clear()
            lastDanmakusOnTrack.fill(null)
        }

        configurePaintsIfNeeded()
        drainPending()

        val dtMs = if (lastFrameNanos == 0L) {
            16f
        } else {
            ((frameTimeNanos - lastFrameNanos) / 1_000_000f).coerceIn(0f, 64f)
        }
        lastFrameNanos = frameTimeNanos

        val fontHeight = danmuSize * cfgDensity
        val alphaVal = (danmuOpacity.coerceIn(0f, 1f) * 255).toInt()
        val drawStroke = danmuStrokeWidth > 0f

        val iterator = activeDanmakus.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            item.x -= item.speedPxPerMs * dtMs

            if (item.x + item.width < 0) {
                if (lastDanmakusOnTrack[item.track] === item) lastDanmakusOnTrack[item.track] = null
                iterator.remove()
                continue
            }

            var curX = item.x
            for (part in item.parts) {
                when (part) {
                    is DanmakuPart.Text -> {
                        textPaint.color = part.color
                        textPaint.alpha = alphaVal
                        if (drawStroke) {
                            strokePaint.alpha = alphaVal
                            canvas.drawText(part.content, curX, item.y, strokePaint)
                        }
                        canvas.drawText(part.content, curX, item.y, textPaint)
                        curX += textPaint.measureText(part.content)
                    }
                    is DanmakuPart.Image -> {
                        val bmp = part.bitmap
                        if (bmp != null) {
                            val ratio = fontHeight / bmp.height
                            val destW = bmp.width * ratio
                            srcRect.set(0, 0, bmp.width, bmp.height)
                            destRect.set(
                                curX,
                                item.y - fontHeight + fontHeight * 0.1f,
                                curX + destW,
                                item.y + fontHeight * 0.1f
                            )
                            imagePaint.alpha = alphaVal
                            canvas.drawBitmap(bmp, srcRect, destRect, imagePaint)
                            curX += destW
                        } else {
                            curX += fontHeight
                        }
                    }
                }
            }
        }
    }

    // ── render-thread helpers ────────────────────────────────────────────────

    private fun configurePaintsIfNeeded() {
        val d = density
        val size = danmuSize
        val weight = danmuFontWeight
        val stroke = danmuStrokeWidth
        if (size == cfgSize && weight == cfgWeight && stroke == cfgStroke && d == cfgDensity) return
        cfgSize = size; cfgWeight = weight; cfgStroke = stroke; cfgDensity = d

        val sizePx = size * d
        textPaint.textSize = sizePx
        strokePaint.textSize = sizePx
        strokePaint.strokeWidth = stroke * d
        val tf = when (weight) {
            2 -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            6 -> Typeface.create("sans-serif", Typeface.BOLD)
            8 -> Typeface.create("sans-serif-black", Typeface.BOLD)
            else -> Typeface.DEFAULT
        }
        textPaint.typeface = tf
        strokePaint.typeface = tf
    }

    private fun drainPending() {
        if (pending.isEmpty()) return
        val w = width
        val h = height
        if (w <= 0 || h <= 0) {
            // Surface not sized yet — drop pending to avoid stockpiling (matches old behavior,
            // which only enqueued once width/height were known).
            pending.clear()
            return
        }

        val fontHeight = danmuSize * cfgDensity
        val trackHeight = fontHeight * 1.5f
        val maxTracks = ((h * danmuArea) / trackHeight).toInt().coerceIn(1, MAX_TRACKS)
        val pad = 50 * cfgDensity
        val durationMs = 8000f / danmuSpeed.coerceAtLeast(0.1f)

        var p = pending.poll()
        while (p != null) {
            val itemWidth = measureWidth(p.parts, fontHeight)

            var track = -1
            for (t in 0 until maxTracks) {
                val last = lastDanmakusOnTrack[t]
                if (last == null || last.x + last.width < w - pad) {
                    track = t
                    break
                }
            }
            if (track == -1) track = (0 until maxTracks).random()

            val item = DanmakuItem(
                parts = p.parts,
                x = w.toFloat(),
                y = (track + 1) * trackHeight,
                speedPxPerMs = (w + itemWidth) / durationMs,
                width = itemWidth,
                track = track,
                type = p.type
            )
            lastDanmakusOnTrack[track] = item
            activeDanmakus.add(item)
            p = pending.poll()
        }
    }

    private fun measureWidth(parts: List<DanmakuPart>, fontHeight: Float): Float {
        var w = 0f
        for (part in parts) {
            w += when (part) {
                is DanmakuPart.Text -> textPaint.measureText(part.content)
                is DanmakuPart.Image -> {
                    val bmp = part.bitmap
                    if (bmp != null) fontHeight / bmp.height * bmp.width else fontHeight
                }
            }
        }
        return w
    }

    companion object {
        private const val MAX_TRACKS = 32
    }
}
