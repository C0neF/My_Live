package com.mylive.app.ui.emoji

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.common.EmojiAtlasRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiAtlasRepositoryAndroidTest {

    @Test
    fun loadsDouyinEmojiBitmapFromAtlasAssets() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val bitmap = EmojiAtlasRepository.getBitmap(
            context,
            EmojiAtlasRef.douyin("1f642").toUrl()
        )

        assertNotNull(bitmap)
        assertEquals(96, bitmap!!.width)
        assertEquals(96, bitmap.height)
    }

    @Test
    fun ignoresNonAtlasUrls() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val bitmap = EmojiAtlasRepository.getBitmap(context, "https://example.com/image.png")

        assertNull(bitmap)
    }
}
