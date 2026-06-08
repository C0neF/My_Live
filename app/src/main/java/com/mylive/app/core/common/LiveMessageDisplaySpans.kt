package com.mylive.app.core.common

import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageSpan

private val BracketEmojiPlaceholderRegex = Regex("""\[[^\[\]\r\n]{1,32}]""")
private val TrailingBracketEmojiPlaceholderRegex = Regex("""\s*\[[^\[\]\r\n]{1,32}]\s*$""")
private val LeadingBracketEmojiPlaceholderRegex = Regex("""^\s*\[[^\[\]\r\n]{1,32}]\s*""")

fun buildLiveMessageDisplaySpans(
    message: String,
    imageUrls: List<String>?
): List<LiveMessageSpan> {
    val urls = imageUrls.orEmpty()
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (urls.isEmpty()) {
        return if (message.isEmpty()) emptyList() else listOf(LiveMessageSpan.Text(message))
    }

    val spans = mutableListOf<LiveMessageSpan>()
    val matches = BracketEmojiPlaceholderRegex.findAll(message).take(urls.size).toList()
    if (matches.isEmpty()) {
        if (message.isNotEmpty()) spans.add(LiveMessageSpan.Text(message))
        urls.forEach { spans.add(LiveMessageSpan.Image(it)) }
        return spans
    }

    var lastIndex = 0
    var imageIndex = 0
    for (match in matches) {
        if (match.range.first > lastIndex) {
            spans.add(LiveMessageSpan.Text(message.substring(lastIndex, match.range.first)))
        }
        spans.add(LiveMessageSpan.Image(urls[imageIndex++]))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < message.length) {
        spans.add(LiveMessageSpan.Text(message.substring(lastIndex)))
    }
    while (imageIndex < urls.size) {
        spans.add(LiveMessageSpan.Image(urls[imageIndex++]))
    }
    return spans
}

fun normalizeLiveMessageDisplaySpans(
    spans: List<LiveMessageSpan>
): List<LiveMessageSpan> {
    if (spans.isEmpty()) return emptyList()

    val normalized = ArrayList<LiveMessageSpan>(spans.size)
    for (index in spans.indices) {
        val span = spans[index]
        val previous = spans.nearestNonBlankTextBefore(index)
        val next = spans.nearestNonBlankTextAfter(index)
        when {
            span is LiveMessageSpan.Text &&
                span.text.isBlank() &&
                previous is LiveMessageSpan.Image &&
                next is LiveMessageSpan.Text &&
                next.text.startsWithBracketEmojiPlaceholder() -> {
                // Drop spacer text between an image span and its duplicated placeholder.
            }
            span is LiveMessageSpan.Text &&
                span.text.isBlank() &&
                previous is LiveMessageSpan.Text &&
                previous.text.endsWithBracketEmojiPlaceholder() &&
                next is LiveMessageSpan.Image -> {
                // Drop spacer text between a duplicated placeholder and its image span.
            }
            span is LiveMessageSpan.Text &&
                next is LiveMessageSpan.Image -> {
                val text = span.text.replace(TrailingBracketEmojiPlaceholderRegex, "")
                if (text.isNotEmpty()) {
                    normalized.add(LiveMessageSpan.Text(text))
                }
            }
            span is LiveMessageSpan.Text &&
                previous is LiveMessageSpan.Image -> {
                val text = span.text.replace(LeadingBracketEmojiPlaceholderRegex, "")
                if (text.isNotEmpty()) {
                    normalized.add(LiveMessageSpan.Text(text))
                }
            }
            else -> normalized.add(span)
        }
    }
    return normalized
}

internal fun buildPlayerDanmakuDisplaySpans(
    message: LiveMessage,
    renderEmoji: Boolean
): List<LiveMessageSpan> {
    val spans = message.spans
    if (!spans.isNullOrEmpty()) {
        val normalized = normalizeLiveMessageDisplaySpans(spans)
        return if (renderEmoji) {
            normalized
        } else {
            normalized.filterIsInstance<LiveMessageSpan.Text>()
        }
    }

    return if (renderEmoji) {
        buildLiveMessageDisplaySpans(message.message, message.imageUrls)
    } else if (message.message.isEmpty()) {
        emptyList()
    } else {
        listOf(LiveMessageSpan.Text(message.message))
    }
}

private fun List<LiveMessageSpan>.nearestNonBlankTextBefore(index: Int): LiveMessageSpan? {
    for (i in index - 1 downTo 0) {
        val span = this[i]
        if (span !is LiveMessageSpan.Text || span.text.isNotBlank()) return span
    }
    return null
}

private fun List<LiveMessageSpan>.nearestNonBlankTextAfter(index: Int): LiveMessageSpan? {
    for (i in index + 1 until size) {
        val span = this[i]
        if (span !is LiveMessageSpan.Text || span.text.isNotBlank()) return span
    }
    return null
}

private fun String.startsWithBracketEmojiPlaceholder(): Boolean {
    return LeadingBracketEmojiPlaceholderRegex.containsMatchIn(this)
}

private fun String.endsWithBracketEmojiPlaceholder(): Boolean {
    return TrailingBracketEmojiPlaceholderRegex.containsMatchIn(this)
}
