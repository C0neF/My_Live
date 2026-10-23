package com.mylive.app.ui.screen.room.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylive.app.MainActivity
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.secure.AndroidKeystoreCredentialCipher
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import com.mylive.app.data.repository.LiveRoomPreferences
import com.mylive.app.data.repository.SettingsRepository
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Real encoder -> loopback HTTP -> real decoder, host orientation, controls and surface changes. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class PortraitPlaybackAndroidTest {
    private val effects = StandardTestDispatcher()
    @get:Rule val compose = createAndroidComposeRule<MainActivity>(effectContext = effects)
    private val server = MockWebServer()
    private var showHost by mutableStateOf(true)
    private var exiting by mutableStateOf(false)
    private var host: LivePlaybackHost? = null
    private var playbackEngine: LivePlaybackEngine? = null

    @Before fun requireIsolatedApp() {
        assertEquals("com.mylive.app.audit", compose.activity.packageName)
    }

    @After fun closeHostAndServer() {
        compose.runOnUiThread { showHost = false }
        compose.waitForIdle()
        server.shutdown()
    }

    @Test fun verticalAndHorizontalSourcesChooseTheirOwnFullscreenWithoutReplacingThePlayer() {
        val portrait = syntheticVideo("portrait", 144, 256)
        val landscape = syntheticVideo("landscape", 256, 144)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                .setHeader("Content-Type", "video/mp4")
                .setBody(Buffer().write(if (request.path?.contains("landscape") == true) landscape else portrait))
        }
        server.start()
        val portraitUrl = server.url("/portrait.mp4").toString()
        val landscapeUrl = server.url("/landscape.mp4").toString()
        renderPlayer(portraitUrl)

        waitForFirstFrame()
        assertEquals(9f / 16f, host!!.videoAspectRatio!!, 0.01f)
        val originalPlayer = host!!.playerController!!.player
        compose.runOnUiThread { host!!.toggleFullscreen() }
        compose.waitUntil(15_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
                compose.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        assertSame(originalPlayer, host!!.playerController!!.player)
        capture("portrait-fullscreen")

        // A source/quality replacement uses the same player and only adopts the next source's
        // orientation once that source reports a real VideoSize.
        compose.runOnUiThread { playbackEngine!!.play(listOf(landscapeUrl)) }
        compose.waitUntil(20_000) {
            host?.videoAspectRatio?.let { it > 1f } == true &&
                host?.playerController?.state?.value?.firstFrameRendered == true &&
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, compose.activity.requestedOrientation)
        assertSame(originalPlayer, host!!.playerController!!.player)
        capture("landscape-fullscreen")

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(15_000) {
            host?.isFullscreen == false &&
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        assertSame(originalPlayer, host!!.playerController!!.player)
        assertNull(host!!.playerController!!.state.value.error)
    }

    @Test fun automaticFullscreenUsesPortraitAndDoesNotReenterAfterBack() {
        val body = syntheticVideo("portrait", 144, 256)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(Buffer().write(body))
        }
        server.start()
        val url = server.url("/portrait.mp4").toString()
        renderPlayer(url, automaticFullscreen = true)
        waitForFirstFrame()
        compose.waitUntil(15_000) {
            host?.isFullscreen == true &&
                compose.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) { host?.isFullscreen == false }
        compose.runOnUiThread { playbackEngine!!.play(listOf(url)) }
        waitForFirstFrame()
        assertFalse(host!!.isFullscreen)
    }

    @Test fun leavingManualFullscreenDuringLoadingCancelsThePendingAutomaticEntry() {
        server.enqueue(MockResponse().setHeadersDelay(3, TimeUnit.SECONDS)
            .setBody(Buffer().write(syntheticVideo("portrait", 144, 256))))
        server.start()
        renderPlayer(server.url("/portrait.mp4").toString(), automaticFullscreen = true)
        compose.waitUntil(10_000) { host?.playerController != null }
        compose.runOnUiThread { host!!.toggleFullscreen() }
        compose.waitUntil(10_000) { host?.isFullscreen == true }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) { host?.isFullscreen == false }
        waitForFirstFrame()
        assertFalse("Metadata arriving later must not undo the user's fullscreen exit", host!!.isFullscreen)
    }

    @Test fun exitingRoomDoesNotRelockOrientationAfterGeometryIsCleared() {
        server.enqueue(MockResponse().setBody(Buffer().write(syntheticVideo("portrait", 144, 256))))
        server.start()
        renderPlayer(server.url("/portrait.mp4").toString())
        waitForFirstFrame()
        compose.runOnUiThread { host!!.toggleFullscreen() }
        compose.waitUntil(15_000) {
            host?.isFullscreen == true &&
                compose.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT &&
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        var expectedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        compose.runOnUiThread {
            host!!.prepareToLeave()
            expectedOrientation = compose.activity.requestedOrientation
            exiting = true
        }
        compose.waitUntil(10_000) { host?.playerController?.state?.value?.videoAspectRatio == null }
        compose.waitForIdle()
        assertEquals("The exiting host must not reclaim the next screen's window", expectedOrientation,
            compose.activity.requestedOrientation)
    }

    private fun waitForFirstFrame() {
        compose.waitUntil(20_000) {
            host?.playerController?.state?.value?.firstFrameRendered == true && host?.videoAspectRatio != null
        }
    }

    private fun renderPlayer(initialUrl: String, automaticFullscreen: Boolean = false) {
        val context = compose.activity.applicationContext
        val store = SettingsDataStore(context)
        val settings = SettingsRepository(store, SensitiveCredentialStore(store, AndroidKeystoreCredentialCipher()))
        val binding = object : LivePlaybackSessionBinding {
            override fun bindPlaybackEngine(engine: LivePlaybackEngine) { playbackEngine = engine }
            override fun unbindPlaybackEngine(engine: LivePlaybackEngine) = Unit
            override fun onPlaybackEngineReady() {
                playbackEngine!!.beginRoomLoad(SystemClock.elapsedRealtime())
                playbackEngine!!.play(listOf(initialUrl))
            }
        }
        compose.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    if (showHost) {
                        val playbackHost = rememberLivePlaybackHost(
                            content = LivePlaybackHostContent("fixture", "test", true, "Portrait fixture", "QA", "test"),
                            preferences = LiveRoomPreferences(danmuEnable = false, autoFullScreen = automaticFullscreen),
                            controlsLocked = false,
                            exiting = exiting,
                            binding = binding,
                            settingsRepository = settings,
                            onPlaybackSourceExhausted = { false }
                        )
                        SideEffect { host = playbackHost }
                        Box(Modifier.fillMaxSize()) {
                            PlayerView(
                                playerController = playbackHost.playerController,
                                viewState = PlayerViewUiState(danmakuEnabled = false),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

    }

    private fun capture(name: String) {
        val file = File(compose.activity.getExternalFilesDir(null), "$name.png")
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

    /** Generates a small deterministic 30-second MP4 without network fixtures or extra tooling. */
    private fun syntheticVideo(name: String, width: Int, height: Int): ByteArray {
        val file = File(compose.activity.cacheDir, "$name-fixture.mp4")
        val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 100_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 10)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType("video/avc")
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        var track = -1
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            var frame = 0
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (!outputDone && SystemClock.elapsedRealtime() < deadline) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        if (frame == 300) {
                            codec.queueInputBuffer(index, 0, 0, frame * 100_000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val image = checkNotNull(codec.getInputImage(index))
                            image.planes.forEachIndexed { planeIndex, plane ->
                                val rows = if (planeIndex == 0) height else height / 2
                                val columns = if (planeIndex == 0) width else width / 2
                                for (row in 0 until rows) for (column in 0 until columns) {
                                    val value = if (planeIndex == 0) {
                                        if ((column < columns / 2) == (row < rows / 2)) 80 else 180
                                    } else if (planeIndex == 1) 90 else 180
                                    plane.buffer.put(row * plane.rowStride + column * plane.pixelStride, value.toByte())
                                }
                            }
                            codec.queueInputBuffer(index, 0, width * height * 3 / 2, frame * 100_000L, 0)
                            frame++
                        }
                    }
                }
                when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    }
                    else -> if (index >= 0) {
                        val buffer = checkNotNull(codec.getOutputBuffer(index))
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            check(started)
                            muxer.writeSampleData(track, buffer, info)
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
            check(outputDone) { "Synthetic video encoder did not finish within 30 seconds" }
        } finally {
            codec.stop()
            codec.release()
            if (started) muxer.stop()
            muxer.release()
        }
        return file.readBytes()
    }
}
