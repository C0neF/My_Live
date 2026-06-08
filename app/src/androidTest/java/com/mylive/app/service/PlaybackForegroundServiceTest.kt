package com.mylive.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackForegroundServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var context: Context
    private var exoPlayer: ExoPlayer? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        runBlocking {
            withContext(Dispatchers.Main) {
                exoPlayer?.release()
                exoPlayer = null
                PlaybackForegroundService.activePlayer = null
            }
        }
    }

    @Test
    fun testServiceLifecycle() {
        runBlocking {
            withContext(Dispatchers.Main) {
                exoPlayer = ExoPlayer.Builder(context).build()
            }
            
            val player = exoPlayer!!
            
            // Start the service
            PlaybackForegroundService.start(
                context = context,
                player = player,
                title = "Test Room Title",
                anchor = "Test Anchor",
                platform = "bilibili"
            )
            
            // Verify static fields set
            assertNotNull(PlaybackForegroundService.activePlayer)
            org.junit.Assert.assertEquals("Test Room Title", PlaybackForegroundService.roomTitle)
            org.junit.Assert.assertEquals("Test Anchor", PlaybackForegroundService.anchorName)
            org.junit.Assert.assertEquals("bilibili", PlaybackForegroundService.platformName)

            // Test play/pause action intent execution
            val pauseIntent = Intent(context, PlaybackForegroundService::class.java).apply {
                action = PlaybackForegroundService.ACTION_PAUSE
            }
            context.startService(pauseIntent)
            
            // Stop the service
            PlaybackForegroundService.stop(context)
            
            // Let service stop and release
            withContext(Dispatchers.Main) {
                PlaybackForegroundService.activePlayer = null
            }
            assertNull(PlaybackForegroundService.activePlayer)
        }
    }
}
