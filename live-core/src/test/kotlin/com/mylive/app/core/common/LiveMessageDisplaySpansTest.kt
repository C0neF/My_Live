package com.mylive.app.core.common

import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageColor
import com.mylive.app.core.model.LiveMessageSpan
import com.mylive.app.core.model.LiveMessageType
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveMessageDisplaySpansTest {

    @Test
    fun replacesBracketPlaceholderWithImage() {
        val imageUrl = "https://example.com/cry.png"

        val spans = buildLiveMessageDisplaySpans(
            message = "你好[大哭]",
            imageUrls = listOf(imageUrl)
        )

        assertEquals(
            listOf(
                LiveMessageSpan.Text("你好"),
                LiveMessageSpan.Image(imageUrl)
            ),
            spans
        )
    }

    @Test
    fun replacesMultiplePlaceholdersInOrder() {
        val firstImage = "https://example.com/1.png"
        val secondImage = "https://example.com/2.png"

        val spans = buildLiveMessageDisplaySpans(
            message = "[大哭]中间[微笑]",
            imageUrls = listOf(firstImage, secondImage)
        )

        assertEquals(
            listOf(
                LiveMessageSpan.Image(firstImage),
                LiveMessageSpan.Text("中间"),
                LiveMessageSpan.Image(secondImage)
            ),
            spans
        )
    }

    @Test
    fun appendsImagesWhenMessageHasNoPlaceholder() {
        val imageUrl = "https://example.com/emoji.png"

        val spans = buildLiveMessageDisplaySpans(
            message = "你好",
            imageUrls = listOf(imageUrl)
        )

        assertEquals(
            listOf(
                LiveMessageSpan.Text("你好"),
                LiveMessageSpan.Image(imageUrl)
            ),
            spans
        )
    }

    @Test
    fun keepsPlainTextWhenThereAreNoImages() {
        val spans = buildLiveMessageDisplaySpans(
            message = "你好[大哭]",
            imageUrls = null
        )

        assertEquals(
            listOf(LiveMessageSpan.Text("你好[大哭]")),
            spans
        )
    }

    @Test
    fun removesPlaceholderTextImmediatelyBeforeImageSpan() {
        val imageUrl = "https://example.com/cry.png"

        val spans = normalizeLiveMessageDisplaySpans(
            listOf(
                LiveMessageSpan.Text("你好[大哭]"),
                LiveMessageSpan.Image(imageUrl)
            )
        )

        assertEquals(
            listOf(
                LiveMessageSpan.Text("你好"),
                LiveMessageSpan.Image(imageUrl)
            ),
            spans
        )
    }

    @Test
    fun removesPlaceholderTextImmediatelyAfterImageSpan() {
        val imageUrl = "https://example.com/cry.png"

        val spans = normalizeLiveMessageDisplaySpans(
            listOf(
                LiveMessageSpan.Image(imageUrl),
                LiveMessageSpan.Text("[大哭]")
            )
        )

        assertEquals(
            listOf(LiveMessageSpan.Image(imageUrl)),
            spans
        )
    }

    @Test
    fun removesLeadingPlaceholderAfterImageSpanAndKeepsText() {
        val imageUrl = "https://example.com/cry.png"

        val spans = normalizeLiveMessageDisplaySpans(
            listOf(
                LiveMessageSpan.Image(imageUrl),
                LiveMessageSpan.Text("[大哭]后续")
            )
        )

        assertEquals(
            listOf(
                LiveMessageSpan.Image(imageUrl),
                LiveMessageSpan.Text("后续")
            ),
            spans
        )
    }

    @Test
    fun removesPlaceholderAfterImageWhenTextHasLeadingSpace() {
        val imageUrl = "https://example.com/miao.png"

        val spans = normalizeLiveMessageDisplaySpans(
            listOf(
                LiveMessageSpan.Image(imageUrl),
                LiveMessageSpan.Text(" [妙]")
            )
        )

        assertEquals(
            listOf(LiveMessageSpan.Image(imageUrl)),
            spans
        )
    }

    @Test
    fun removesPlaceholderAfterImageWhenSeparatedByBlankTextSpan() {
        val imageUrl = "https://example.com/miao.png"

        val spans = normalizeLiveMessageDisplaySpans(
            listOf(
                LiveMessageSpan.Image(imageUrl),
                LiveMessageSpan.Text(" "),
                LiveMessageSpan.Text("[妙]")
            )
        )

        assertEquals(
            listOf(LiveMessageSpan.Image(imageUrl)),
            spans
        )
    }

    @Test
    fun removesPlaceholderBeforeImageWhenTextHasTrailingSpace() {
        val imageUrl = "https://example.com/miao.png"

        val spans = normalizeLiveMessageDisplaySpans(
            listOf(
                LiveMessageSpan.Text("[妙] "),
                LiveMessageSpan.Image(imageUrl)
            )
        )

        assertEquals(
            listOf(LiveMessageSpan.Image(imageUrl)),
            spans
        )
    }

    @Test
    fun removesPlaceholderBeforeImageWhenSeparatedByBlankTextSpan() {
        val imageUrl = "https://example.com/miao.png"

        val spans = normalizeLiveMessageDisplaySpans(
            listOf(
                LiveMessageSpan.Text("[妙]"),
                LiveMessageSpan.Text(" "),
                LiveMessageSpan.Image(imageUrl)
            )
        )

        assertEquals(
            listOf(LiveMessageSpan.Image(imageUrl)),
            spans
        )
    }

    @Test
    fun buildsPlayerDanmakuSpansWithoutUserNamePrefix() {
        val spans = buildPlayerDanmakuDisplaySpans(
            message = LiveMessage(
                type = LiveMessageType.CHAT,
                userName = "Alice",
                message = "你好",
                color = LiveMessageColor.WHITE
            ),
            renderEmoji = true
        )

        assertEquals(
            listOf(LiveMessageSpan.Text("你好")),
            spans
        )
    }

    @Test
    fun buildsPlayerDanmakuPlainTextWithoutUserNamePrefixWhenEmojiRenderingDisabled() {
        val spans = buildPlayerDanmakuDisplaySpans(
            message = LiveMessage(
                type = LiveMessageType.CHAT,
                userName = "Alice",
                message = "你好[大哭]",
                color = LiveMessageColor.WHITE,
                imageUrls = listOf("https://example.com/cry.png")
            ),
            renderEmoji = false
        )

        assertEquals(
            listOf(LiveMessageSpan.Text("你好[大哭]")),
            spans
        )
    }
}
