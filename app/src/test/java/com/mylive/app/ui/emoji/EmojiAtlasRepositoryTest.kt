package com.mylive.app.ui.emoji

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmojiAtlasRepositoryTest {

    @Test
    fun atlasBitmapResolutionRejectsMainThreadWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val atlasUrl = "atlas://douyin/missing"

        val failure = runCatching {
            EmojiAtlasRepository.getBitmap(context, atlasUrl)
        }.exceptionOrNull()

        assertTrue(Looper.myLooper() == Looper.getMainLooper())
        assertTrue(failure is IllegalStateException)
    }
}
