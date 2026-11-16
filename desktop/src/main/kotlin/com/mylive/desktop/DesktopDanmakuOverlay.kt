package com.mylive.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.core.model.LiveMessageDanmakuPosition
import kotlinx.coroutines.delay

private val DanmakuTextShadow = Shadow(Color.Black.copy(alpha = .85f), blurRadius = 3f)

private data class OverlayGeometry(val width: Float, val laneHeight: Float, val travelSeconds: Float)

private class OverlayEntry(val chat: ChatEntry, val lane: Int, val geometry: OverlayGeometry) {
    var textWidth by mutableIntStateOf(-1)
    var startedAtMillis = -1L
    val scrolling = chat.message.danmakuPosition == LiveMessageDanmakuPosition.SCROLL
    private val durationMillis get() = if (scrolling) desktopDanmakuDurationMillis(geometry.width, textWidth.toFloat(), geometry.travelSeconds)
        else (geometry.travelSeconds * 1000).toInt()
    fun finished(now: Long) = startedAtMillis >= 0 && now - startedAtMillis >= durationMillis
    fun remainingMillis(now: Long) = startedAtMillis + durationMillis - now
    fun x(now: Long): Float {
        if (!scrolling) return (geometry.width - textWidth) / 2
        val progress = if (startedAtMillis < 0) 0f else ((now - startedAtMillis).toFloat() / durationMillis).coerceIn(0f, 1f)
        return geometry.width - (geometry.width + textWidth) * progress
    }
    fun occupancy(now: Long) = DesktopDanmakuOccupant(lane, scrolling,
        if (textWidth < 0) Float.POSITIVE_INFINITY else x(now) + textWidth)
}
@Composable
internal fun DesktopDanmakuOverlay(messages: List<ChatEntry>, preferences: DesktopDanmakuPreferences, siteId: String, modifier: Modifier) {
    val active: SnapshotStateList<OverlayEntry> = remember { mutableStateListOf() }
    val frameTime = remember { mutableLongStateOf(0L) }
    var lastId by remember { mutableLongStateOf(0L) }
    val baseTextStyle = LocalTextStyle.current
    val danmakuTextStyle = remember(baseTextStyle) {
        baseTextStyle.copy(
            fontFamily = baseTextStyle.fontFamily ?: DesktopFontFamily,
            fontWeight = FontWeight.Medium,
            shadow = DanmakuTextShadow
        )
    }
    // Scrolling messages share a frame clock. Fixed messages wake only for arrival and expiry.
    val scrolling = active.any { it.scrolling }
    LaunchedEffect(active.isNotEmpty(), scrolling, if (scrolling) null else active.lastOrNull()?.chat?.id) {
        if (active.isEmpty()) return@LaunchedEffect
        while (active.isNotEmpty()) {
            val now = withFrameNanos { nanos ->
                val timestamp = nanos / 1_000_000L
                active.forEach { if (it.startedAtMillis < 0 && (!it.scrolling || it.textWidth >= 0)) it.startedAtMillis = timestamp }
                frameTime.longValue = timestamp
                active.removeAll { it.finished(timestamp) }
                timestamp
            }
            if (!scrolling && active.isNotEmpty()) delay(active.minOf { it.remainingMillis(now) }.coerceAtLeast(1L))
        }
    }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(0.dp))) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }
        val laneHeight = with(LocalDensity.current) { preferences.fontSize.sp.toDp().value } + 12f
        val slots = (maxHeight.value / laneHeight).toInt().coerceIn(1, 200)
        val scrollSlots = (slots * preferences.area).toInt().coerceAtLeast(1)
        val limit = minOf(slots, preferences.lines)
        val geometry = OverlayGeometry(width, laneHeight, preferences.travelSeconds)
        val gap = with(LocalDensity.current) { 24.dp.toPx() }
        LaunchedEffect(messages, slots, geometry, preferences, siteId) {
            if (messages.isEmpty()) active.clear()
            active.removeAll { it.geometry != geometry || it.lane >= slots || (it.scrolling && it.lane >= scrollSlots) ||
                !preferences.allows(it.chat.message, siteId) || !preferences.positionAllowed(it.chat.message) }
            val retainedLanes = active.map { it.lane }.distinct().take(limit).toSet()
            active.removeAll { it.lane !in retainedLanes }
            val planner = DesktopDanmakuLanePlanner(slots, scrollSlots, limit, width, gap, active.map { it.occupancy(frameTime.longValue) })
            val batch = messages.filter { it.id > lastId && preferences.allows(it.message, siteId) && preferences.positionAllowed(it.message) }
                .takeLast((MAX_DESKTOP_DANMAKU - active.size).coerceAtLeast(0))
            lastId = messages.lastOrNull()?.id ?: lastId
            batch.forEach { entry ->
                planner.reserve(entry.message.danmakuPosition)?.let { lane -> active.add(OverlayEntry(entry, lane, geometry)) }
            }
        }
        active.toList().forEach { overlay -> key(overlay.chat.id) {
            val entry = overlay.chat
            val color = if (preferences.colored) Color(entry.message.color.r.coerceIn(0, 255), entry.message.color.g.coerceIn(0, 255), entry.message.color.b.coerceIn(0, 255)) else Color.White
            DesktopMessageText(entry.message, preferences.fontSize.sp, color = color.copy(alpha = preferences.opacity),
                style = danmakuTextStyle, singleLine = true, images = preferences.renderEmoji,
                modifier = Modifier.offset(y = (overlay.lane * laneHeight).dp)
                    .then(if (overlay.scrolling) Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true) else Modifier)
                    .onSizeChanged { overlay.textWidth = it.width }
                    .graphicsLayer { translationX = overlay.x(if (overlay.scrolling) frameTime.longValue else 0L) })
        } }
    }
}

