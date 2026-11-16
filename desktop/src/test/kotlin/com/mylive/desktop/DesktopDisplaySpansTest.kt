package com.mylive.desktop

import com.mylive.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DesktopDisplaySpansTest {
    @Test fun renderingUsesOneTotalTextBudgetAcrossRichSpans() {
        val message = LiveMessage(LiveMessageType.CHAT, "A", "fallback", spans = List(100) { LiveMessageSpan.Text("x".repeat(1000)) })
        val spans = desktopDisplaySpans(message, true, 160)
        assertEquals(160, spans.filterIsInstance<LiveMessageSpan.Text>().sumOf { it.text.length })
    }
    @Test fun emojiOrderIsPreservedAndNonHttpImagesUseTextFallback() {
        val message = LiveMessage(LiveMessageType.CHAT, "A", "A[笑]B", imageMap = mapOf("[笑]" to "https://example.org/emoji.png"))
        assertEquals(listOf(LiveMessageSpan.Text("A"), LiveMessageSpan.Image("https://example.org/emoji.png"), LiveMessageSpan.Text("B")), desktopDisplaySpans(message, true, 160))
        val local = message.copy(spans = listOf(LiveMessageSpan.Image("file:///private/image.png")))
        assertEquals(listOf(LiveMessageSpan.Text("[表情]")), desktopDisplaySpans(local, true, 160))
        assertEquals(listOf(LiveMessageSpan.Text("A[笑]B")), desktopDisplaySpans(message, false, 160))
    }
}
