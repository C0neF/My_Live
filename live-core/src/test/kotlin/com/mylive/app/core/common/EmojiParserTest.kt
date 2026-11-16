package com.mylive.app.core.common

import com.mylive.app.core.model.LiveMessageSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EmojiParserTest {

    @Test
    fun douyinEmojiTextUsesAtlasReference() {
        val spans = EmojiParser.parse("你好[微笑]", "douyin")

        val image = spans.filterIsInstance<LiveMessageSpan.Image>().single()
        assertEquals("atlas://douyin/1f642", image.imageUrl)
        assertFalse(image.imageUrl.contains("douyin_emoji"))
    }
}
