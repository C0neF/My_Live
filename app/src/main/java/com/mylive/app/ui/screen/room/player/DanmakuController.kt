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
import com.mylive.app.core.model.DanmakuSettingsPolicy
import com.mylive.app.core.model.LiveMessageDanmakuPosition
import com.mylive.app.core.model.LiveMessageSpan
import com.mylive.app.core.model.LiveMessageType
import com.mylive.app.ui.emoji.EmojiAtlasRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.lang.ref.WeakReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt

sealed class DanmakuPart {
    internal var advancePx = 0f
    internal var measurementVersion = -1L
    data class Text(val content: String, val color: Int) : DanmakuPart()
    data class Image(val url: String, @Volatile var bitmap: Bitmap? = null) : DanmakuPart() {
        internal var measuredBitmap: Bitmap? = null
    }
}

class DanmakuItem(
    val parts: List<DanmakuPart>,
    var x: Float,
    var y: Float,
    var speedPxPerMs: Float,
    var width: Float,
    var track: Int,
    val position: LiveMessageDanmakuPosition,
    val expiresAtMs: Long? = null
)

internal data class DanmakuItemSnapshot(
    val x: Float, val y: Float, val width: Float, val speed: Float,
    val track: Int, val position: LiveMessageDanmakuPosition
)

internal data class DanmakuScheduleSnapshot(
    val pendingCount: Int,
    val activeCount: Int,
    val activeTracks: List<Int>,
    val activeItems: List<DanmakuItemSnapshot> = emptyList(),
    val droppedCount: Long = 0
)

internal data class DanmakuRenderDiagnostics(
    val activeCount: Int, val pendingCount: Int, val droppedCount: Long,
    val lastDrawMillis: Double, val cacheBytes: Int
)

private class PendingDanmaku(
    val parts: List<DanmakuPart>,
    val position: LiveMessageDanmakuPosition,
    val receivedAtMs: Long
)

private data class DanmakuGeometry(
    val width: Int, val height: Int, val density: Float, val size: Float,
    val weight: Int, val stroke: Float, val area: Float, val lines: Int,
    val topMargin: Float, val bottomMargin: Float, val speed: Float,
    val hideScroll: Boolean, val hideTop: Boolean, val hideBottom: Boolean,
    val renderEmoji: Boolean
) {
    val fontHeight: Float get() = size * density
    val durationMillis: Float get() = 8000f / speed
    fun shows(position: LiveMessageDanmakuPosition) =
        shouldDisplayDanmakuPosition(position, hideScroll, hideTop, hideBottom)
}

internal fun normalizeDanmakuFontWeight(weight: Int): Int = DanmakuSettingsPolicy.fontWeight(weight) / 100

/**
 * Producers only parse/enqueue. Paint, geometry and the active list belong to the render thread.
 * UI configuration is applied as a batch; images are loaded only for admitted messages.
 */
class DanmakuController(
    private val context: Context,
    private val clock: DanmakuClock = SystemDanmakuClock,
    private val measureText: (Paint, String) -> Float = { paint, text -> paint.measureText(text) }
) {
    private val imageLoader = Coil.imageLoader(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bitmapCache = object : LruCache<String, Bitmap>(MAX_BITMAP_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }
    private class BitmapLoad {
        val targets = ArrayList<WeakReference<DanmakuPart.Image>>()
    }
    private val imageLock = Any()
    private val inFlightBitmaps = HashMap<String, BitmapLoad>()
    private val imagePermits = Semaphore(4)
    private val pending = ArrayBlockingQueue<PendingDanmaku>(MAX_PENDING_DANMAKU)
    private val submissionLock = Any()
    private val configurationLock = Any()
    private val droppedCount = AtomicLong()
    @Volatile private var released = false
    private val activeDanmakus = ArrayList<DanmakuItem>()
    private val lastDanmakusOnTrack = arrayOfNulls<DanmakuItem>(MAX_TRACKS)
    private var lastFrameNanos = 0L
    private var geometry: DanmakuGeometry? = null
    private var measurementVersion = 0L
    private val dedupeLock = Any()
    private val dedupeWindow = DanmakuDedupeWindow()

    @Volatile var danmuSize = 16f
    @Volatile var danmuSpeed = 1f
    @Volatile var danmuArea = 0.8f
    @Volatile var danmuLineCount = 8
    @Volatile var danmuDelayMs = 0
    @Volatile var danmuOpacity = 1f
    @Volatile var danmuFontWeight = 4
    @Volatile var danmuStrokeWidth = 2f
    @Volatile var danmuTopMargin = 0f
    @Volatile var danmuBottomMargin = 0f
    @Volatile var danmuHideScroll = false
    @Volatile var danmuHideTop = false
    @Volatile var danmuHideBottom = false
    @Volatile var dedupeEnabled = false
    @Volatile var dedupeWindowSize = 20
    @Volatile var dedupeStepSize = 2
    @Volatile var dedupeStrictMode = false
    @Volatile var danmuRenderEmoji = true
    @Volatile var width = 0
    @Volatile var height = 0
    @Volatile private var clearRequested = false
    @Volatile private var activeCount = 0
    @Volatile private var lastDrawMillis = 0.0
    @Volatile private var frameRequester: (() -> Unit)? = null
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val destRect = RectF()

    internal fun updateConfiguration(update: () -> Unit) {
        synchronized(configurationLock) { update() }
        requestFrame()
    }

    internal fun updateViewport(newWidth: Int, newHeight: Int) = updateConfiguration {
        width = newWidth
        height = newHeight
    }

    fun addDanmaku(msg: LiveMessage) {
        if (released || msg.type != LiveMessageType.CHAT || danmuLineCount <= 0) return
        if (!shouldDisplayDanmakuPosition(msg.danmakuPosition, danmuHideScroll, danmuHideTop, danmuHideBottom)) return
        synchronized(dedupeLock) {
            dedupeWindow.configure(dedupeEnabled, dedupeWindowSize, dedupeStepSize, dedupeStrictMode)
            if (dedupeWindow.shouldDrop(msg)) return
        }
        val parts = buildParts(msg)
        if (parts.isEmpty()) return
        synchronized(submissionLock) {
            if (released) return
            val item = PendingDanmaku(parts, msg.danmakuPosition, clock.uptimeMillis())
            while (!pending.offer(item)) {
                if (pending.poll() != null) droppedCount.incrementAndGet()
            }
        }
        requestFrame()
    }

    private fun buildParts(msg: LiveMessage): List<DanmakuPart> {
        val color = Color.rgb(msg.color.r, msg.color.g, msg.color.b)
        return buildPlayerDanmakuDisplaySpans(msg, danmuRenderEmoji).map { span ->
            when (span) {
                is LiveMessageSpan.Text -> DanmakuPart.Text(span.text, color)
                is LiveMessageSpan.Image -> DanmakuPart.Image(span.imageUrl)
            }
        }
    }

    private fun preloadImages(parts: List<DanmakuPart>, fontHeight: Float) {
        val targetPixels = (fontHeight * 1.5f).roundToInt().coerceIn(32, 256)
        for (part in parts) {
            if (part !is DanmakuPart.Image || released) continue
            val key = "$targetPixels:${part.url}"
            val cached = bitmapCache.get(key)
            if (cached != null) {
                part.bitmap = cached
                continue
            }
            var newLoad: BitmapLoad? = null
            synchronized(imageLock) {
                if (released) return
                val ready = bitmapCache.get(key)
                if (ready != null) { part.bitmap = ready; return@synchronized }
                var load = inFlightBitmaps[key]
                if (load == null && inFlightBitmaps.size < MAX_IMAGE_LOADS) {
                    load = BitmapLoad()
                    inFlightBitmaps[key] = load
                    newLoad = load
                }
                if (load != null) {
                    if (load.targets.size >= MAX_IMAGE_SUBSCRIBERS) load.targets.removeAll { it.get() == null }
                    if (load.targets.size < MAX_IMAGE_SUBSCRIBERS && load.targets.none { it.get() === part }) {
                        load.targets.add(WeakReference(part))
                    }
                }
            }
            val load = newLoad ?: continue
            val url = part.url
            scope.launch {
                try {
                    val bitmap = try {
                        imagePermits.withPermit {
                            EmojiAtlasRepository.getBitmap(context, url) ?: loadBitmap(url, targetPixels)
                        }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        null
                    }
                    if (bitmap != null) {
                        synchronized(submissionLock) {
                            if (!released) {
                                synchronized(imageLock) {
                                    bitmapCache.put(key, bitmap)
                                    load.targets.forEach { it.get()?.bitmap = bitmap }
                                    if (inFlightBitmaps[key] === load) inFlightBitmaps.remove(key)
                                }
                            }
                        }
                        requestFrame()
                    }
                } finally {
                    synchronized(imageLock) {
                        if (inFlightBitmaps[key] === load) inFlightBitmaps.remove(key)
                    }
                }
            }
        }
    }

    private suspend fun loadBitmap(url: String, targetPixels: Int): Bitmap? = try {
        val request = ImageRequest.Builder(context).data(url).size(targetPixels)
            .allowHardware(false).build()
        (imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    }

    fun clear() {
        if (released) return
        clearRequested = true
        pending.clear()
        synchronized(dedupeLock) { dedupeWindow.clear() }
        requestFrame()
    }

    internal fun setFrameRequester(requester: (() -> Unit)?) { frameRequester = requester }
    internal fun needsFrame(): Boolean = !released && (clearRequested || activeCount > 0 || pending.isNotEmpty())

    internal fun nextFrameDelayMillis(): Long? {
        if (released) return null
        if (clearRequested || activeCount > 0) return 0L
        val nextReceived = pending.minOfOrNull { it.receivedAtMs } ?: return null
        return (nextReceived + danmuDelayMs.coerceIn(0, 5000) - clock.uptimeMillis()).coerceAtLeast(0L)
    }

    private fun requestFrame() { if (!released) frameRequester?.invoke() }

    fun release() {
        synchronized(submissionLock) {
            released = true
            frameRequester = null
            pending.clear()
        }
        scope.cancel()
        synchronized(imageLock) { inFlightBitmaps.clear() }
        bitmapCache.evictAll()
    }

    internal fun scheduleSnapshot() = DanmakuScheduleSnapshot(
        pending.size, activeDanmakus.size, activeDanmakus.map { it.track },
        activeDanmakus.map { DanmakuItemSnapshot(it.x, it.y, it.width, it.speedPxPerMs, it.track, it.position) },
        droppedCount.get()
    )

    internal fun diagnostics() = DanmakuRenderDiagnostics(activeCount, pending.size, droppedCount.get(), lastDrawMillis, bitmapCache.size())

    /** Called only by the surface render thread. */
    fun draw(canvas: Canvas, frameTimeNanos: Long) {
        val started = System.nanoTime()
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (released) return
        if (clearRequested) {
            clearRequested = false
            activeDanmakus.clear()
            lastDanmakusOnTrack.fill(null)
            lastFrameNanos = 0L
        }
        val nextGeometry = synchronized(configurationLock) { captureGeometry() }
        val previousGeometry = geometry
        val geometryChanged = nextGeometry != previousGeometry
        if (geometryChanged) {
            geometry = nextGeometry
            configurePaints(nextGeometry)
            measurementVersion++
        }
        val nowMs = clock.uptimeMillis()
        val layout = resolveDanmakuTrackLayout(nextGeometry.height, nextGeometry.density, nextGeometry.size,
            nextGeometry.area, nextGeometry.lines, nextGeometry.topMargin, nextGeometry.bottomMargin, MAX_TRACKS)
        if (nextGeometry.width <= 0 || layout.trackCount == 0) {
            pending.clear()
            activeDanmakus.clear()
            lastDanmakusOnTrack.fill(null)
            activeCount = 0
            lastFrameNanos = 0L
            return
        }
        val imageGeometryChanged = activeDanmakus.any { item ->
            item.parts.any { it is DanmakuPart.Image && it.bitmap !== it.measuredBitmap }
        }
        if (geometryChanged || imageGeometryChanged) {
            reflowActive(previousGeometry ?: nextGeometry, nextGeometry, layout, nowMs)
        }
        val dtMs = if (lastFrameNanos == 0L) 0f else
            ((frameTimeNanos - lastFrameNanos) / 1_000_000f).coerceAtLeast(0f)
        lastFrameNanos = frameTimeNanos
        val iterator = activeDanmakus.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.position == LiveMessageDanmakuPosition.SCROLL) item.x -= item.speedPxPerMs * dtMs
            if (!nextGeometry.shows(item.position) ||
                (item.position == LiveMessageDanmakuPosition.SCROLL && item.x + item.width < 0f) ||
                (item.expiresAtMs != null && nowMs >= item.expiresAtMs)
            ) {
                releaseTrack(item)
                iterator.remove()
            }
        }
        drainPending(nextGeometry, layout, nowMs)
        val opacity = danmuOpacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        val alphaVal = (opacity * 255).roundToInt()
        strokePaint.alpha = alphaVal
        imagePaint.alpha = alphaVal
        for (item in activeDanmakus) {
            var curX = item.x
            for (part in item.parts) {
                when (part) {
                    is DanmakuPart.Text -> {
                        textPaint.color = part.color
                        textPaint.alpha = alphaVal
                        if (nextGeometry.stroke > 0f) canvas.drawText(part.content, curX, item.y, strokePaint)
                        canvas.drawText(part.content, curX, item.y, textPaint)
                    }
                    is DanmakuPart.Image -> {
                        val bitmap = part.bitmap
                        if (nextGeometry.renderEmoji && bitmap != null && part.advancePx > 0f) {
                            val scale = minOf(part.advancePx / bitmap.width, nextGeometry.fontHeight / bitmap.height)
                            val drawWidth = bitmap.width * scale
                            val drawHeight = bitmap.height * scale
                            val left = curX + (part.advancePx - drawWidth) / 2f
                            val top = item.y - nextGeometry.fontHeight + nextGeometry.fontHeight * 0.1f +
                                (nextGeometry.fontHeight - drawHeight) / 2f
                            srcRect.set(0, 0, bitmap.width, bitmap.height)
                            destRect.set(left, top, left + drawWidth, top + drawHeight)
                            canvas.drawBitmap(bitmap, srcRect, destRect, imagePaint)
                        }
                    }
                }
                curX += part.advancePx
            }
        }
        activeCount = activeDanmakus.size
        if (activeCount == 0 && pending.isEmpty()) lastFrameNanos = 0L
        lastDrawMillis = (System.nanoTime() - started) / 1_000_000.0
    }

    private fun captureGeometry(): DanmakuGeometry {
        fun finite(value: Float, fallback: Float) = value.takeIf { it.isFinite() } ?: fallback
        return DanmakuGeometry(width.coerceAtLeast(0), height.coerceAtLeast(0),
            finite(context.resources.displayMetrics.density, 1f).coerceAtLeast(0.1f),
            finite(danmuSize, 16f).coerceIn(1f, 96f), normalizeDanmakuFontWeight(danmuFontWeight),
            finite(danmuStrokeWidth, 2f).coerceIn(0f, 8f), finite(danmuArea, 0.8f).coerceIn(0f, 1f),
            danmuLineCount.coerceIn(0, MAX_TRACKS), finite(danmuTopMargin, 0f).coerceAtLeast(0f),
            finite(danmuBottomMargin, 0f).coerceAtLeast(0f), finite(danmuSpeed, 1f).coerceIn(0.1f, 5f),
            danmuHideScroll, danmuHideTop, danmuHideBottom, danmuRenderEmoji)
    }

    private fun configurePaints(config: DanmakuGeometry) {
        textPaint.textSize = config.fontHeight
        strokePaint.textSize = config.fontHeight
        strokePaint.strokeWidth = config.stroke * config.density
        val typeface = when (config.weight) {
            2 -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            6 -> Typeface.create("sans-serif", Typeface.BOLD)
            8 -> Typeface.create("sans-serif-black", Typeface.BOLD)
            else -> Typeface.DEFAULT
        }
        textPaint.typeface = typeface
        strokePaint.typeface = typeface
    }

    private fun measureWidth(parts: List<DanmakuPart>, config: DanmakuGeometry): Float {
        var total = 0f
        for (part in parts) {
            when (part) {
                is DanmakuPart.Text -> if (part.measurementVersion != measurementVersion) {
                    part.advancePx = measureText(textPaint, part.content)
                }
                is DanmakuPart.Image -> if (part.measurementVersion != measurementVersion || part.bitmap !== part.measuredBitmap) {
                    val bitmap = part.bitmap
                    part.advancePx = when {
                        !config.renderEmoji -> 0f
                        bitmap != null -> config.fontHeight / bitmap.height * bitmap.width
                        else -> config.fontHeight
                    }
                    part.measuredBitmap = bitmap
                }
            }
            part.measurementVersion = measurementVersion
            total += part.advancePx
        }
        return total
    }

    private fun reflowActive(old: DanmakuGeometry, config: DanmakuGeometry, layout: DanmakuTrackLayout, nowMs: Long) {
        val previous = activeDanmakus.sortedBy { it.position == LiveMessageDanmakuPosition.SCROLL }
        activeDanmakus.clear()
        lastDanmakusOnTrack.fill(null)
        for (item in previous) {
            if (!config.shows(item.position) || (item.expiresAtMs != null && nowMs >= item.expiresAtMs)) continue
            val progress = ((old.width - item.x) / (old.width + item.width).coerceAtLeast(1f)).coerceIn(0f, 1f)
            item.width = measureWidth(item.parts, config)
            if (item.width <= 0f) continue
            if (item.position == LiveMessageDanmakuPosition.SCROLL) {
                item.x = config.width - progress * (config.width + item.width)
                item.speedPxPerMs = (config.width + item.width) / config.durationMillis
            } else {
                item.x = ((config.width - item.width) / 2f).coerceAtLeast(0f)
            }
            val track = findTrack(item.position, item.x, item.speedPxPerMs, config, layout, preferredTrack = item.track)
            if (track < 0) {
                droppedCount.incrementAndGet()
                continue
            }
            item.track = track
            item.y = baseline(item.position, track, config, layout)
            activeDanmakus.add(item)
            if (item.position == LiveMessageDanmakuPosition.SCROLL) lastDanmakusOnTrack[track] = item
            if (config.renderEmoji) preloadImages(item.parts, config.fontHeight)
        }
    }

    private fun drainPending(config: DanmakuGeometry, layout: DanmakuTrackLayout, nowMs: Long) {
        val checks = pending.size.coerceAtMost(MAX_PENDING_CHECKS_PER_FRAME)
        val delay = danmuDelayMs.coerceIn(0, 5000)
        var admitted = 0
        repeat(checks) {
            val item = pending.poll() ?: return
            val due = item.receivedAtMs + delay
            if (nowMs - due > MAX_PENDING_WAIT_MILLIS) {
                droppedCount.incrementAndGet()
                return@repeat
            }
            if (!config.shows(item.position)) return@repeat
            if (nowMs < due || admitted >= MAX_ADMISSIONS_PER_FRAME || activeDanmakus.size >= MAX_ACTIVE_DANMAKU) {
                requeue(item)
                return@repeat
            }
            val itemWidth = measureWidth(item.parts, config)
            if (itemWidth <= 0f) return@repeat
            val w = config.width
            val scrolling = item.position == LiveMessageDanmakuPosition.SCROLL
            val x = if (scrolling) w.toFloat() else ((w - itemWidth) / 2f).coerceAtLeast(0f)
            val speed = if (scrolling) (w + itemWidth) / config.durationMillis else 0f
            val track = findTrack(item.position, x, speed, config, layout)
            if (track < 0) {
                requeue(item)
                return@repeat
            }
            val active = DanmakuItem(item.parts, x, baseline(item.position, track, config, layout),
                speed, itemWidth, track, item.position,
                expiresAtMs = if (scrolling) null else nowMs + FIXED_DANMAKU_DURATION_MS)
            activeDanmakus.add(active)
            if (scrolling) lastDanmakusOnTrack[track] = active
            if (config.renderEmoji) preloadImages(item.parts, config.fontHeight)
            admitted++
        }
    }

    private fun requeue(item: PendingDanmaku) {
        // A producer may have filled the slot meanwhile; newer live messages take precedence.
        if (!pending.offer(item)) droppedCount.incrementAndGet()
    }

    private fun findTrack(position: LiveMessageDanmakuPosition, x: Float, speed: Float,
        config: DanmakuGeometry, layout: DanmakuTrackLayout, preferredTrack: Int = -1): Int {
        fun available(track: Int): Boolean {
            val y = baseline(position, track, config, layout)
            if (activeDanmakus.any { other ->
                    (position != LiveMessageDanmakuPosition.SCROLL || other.position != LiveMessageDanmakuPosition.SCROLL) &&
                        abs(other.y - y) < layout.trackHeightPx
                }) return false
            if (position != LiveMessageDanmakuPosition.SCROLL) return true
            val last = lastDanmakusOnTrack[track] ?: return true
            return canFollowDanmaku(last.x + last.width, last.speedPxPerMs, x, speed, 24f * config.density)
        }
        if (preferredTrack in 0 until layout.trackCount && available(preferredTrack)) return preferredTrack
        for (track in 0 until layout.trackCount) if (track != preferredTrack && available(track)) return track
        return -1
    }

    private fun baseline(position: LiveMessageDanmakuPosition, track: Int,
        config: DanmakuGeometry, layout: DanmakuTrackLayout): Float =
        resolveFixedDanmakuBaseline(position, track, config.height, config.fontHeight, layout)

    private fun releaseTrack(item: DanmakuItem) {
        if (item.position == LiveMessageDanmakuPosition.SCROLL && lastDanmakusOnTrack[item.track] === item) {
            lastDanmakusOnTrack[item.track] = null
        }
    }

    companion object {
        private const val MAX_TRACKS = 32
        internal const val MAX_PENDING_DANMAKU = 512
        internal const val MAX_ACTIVE_DANMAKU = 96
        internal const val MAX_ADMISSIONS_PER_FRAME = 12
        internal const val MAX_PENDING_CHECKS_PER_FRAME = 48
        internal const val MAX_PENDING_WAIT_MILLIS = 2_000L
        private const val FIXED_DANMAKU_DURATION_MS = 5_000L
        private const val MAX_BITMAP_BYTES = 4 * 1024 * 1024
        private const val MAX_IMAGE_LOADS = 32
        private const val MAX_IMAGE_SUBSCRIBERS = 256
    }
}
