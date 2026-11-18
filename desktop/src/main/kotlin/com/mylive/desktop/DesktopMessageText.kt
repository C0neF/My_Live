package com.mylive.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mylive.app.core.common.buildLiveMessageDisplaySpans
import com.mylive.app.core.model.*

internal fun desktopDisplaySpans(message: LiveMessage, images: Boolean, limit: Int): List<LiveMessageSpan> {
    if (!images) return listOf(LiveMessageSpan.Text(message.message.take(limit)))
    val source = message.spans ?: buildLiveMessageDisplaySpans(message.message.take(limit), message.imageUrls?.take(20), message.imageMap)
    var remaining = limit
    return buildList {
        for (span in source.take(64)) {
            if (remaining <= 0) break
            when (span) {
                is LiveMessageSpan.Text -> { val text = span.text.take(remaining); add(LiveMessageSpan.Text(text)); remaining -= text.length }
                is LiveMessageSpan.Image -> {
                    val remote = span.imageUrl.length <= 2048 && runCatching { java.net.URI(span.imageUrl).let {
                        it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null
                    } }.getOrDefault(false)
                    if (remote) { add(span); remaining-- }
                    else { val fallback = "[表情]".take(remaining); add(LiveMessageSpan.Text(fallback)); remaining -= fallback.length }
                }
            }
        }
    }
}

@Composable
internal fun DesktopMessageText(message: LiveMessage, size: TextUnit, modifier: Modifier = Modifier,
    color: Color = Color.Unspecified, style: TextStyle = LocalTextStyle.current, singleLine: Boolean = false, images: Boolean = true) {
    val spans = remember(message, images, singleLine) { desktopDisplaySpans(message, images, if (singleLine) 160 else 1000) }
    val inline = remember(spans, size, style) { buildMap {
        spans.forEachIndexed { index, span -> if (span is LiveMessageSpan.Image) {
            put("emoji$index", InlineTextContent(Placeholder(size * 1.3f, size * 1.3f, PlaceholderVerticalAlign.Center)) {
                var failed by remember(span.imageUrl) { mutableStateOf(false) }
                if (failed) Text("[表情]", fontSize = size, style = style) else AsyncImage(span.imageUrl, "表情", Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit, onError = { failed = true })
            })
        } }
    } }
    val text = remember(spans) { buildAnnotatedString {
        spans.forEachIndexed { index, span -> when (span) {
            is LiveMessageSpan.Text -> append(span.text.take(1000))
            is LiveMessageSpan.Image -> appendInlineContent("emoji$index", "[表情]")
        } }
    } }
    Text(text, modifier, color = color, fontSize = size, lineHeight = if (singleLine) TextUnit.Unspecified else size * 1.5f,
        style = style, inlineContent = inline, maxLines = if (singleLine) 1 else Int.MAX_VALUE, softWrap = !singleLine, overflow = TextOverflow.Clip)
}
