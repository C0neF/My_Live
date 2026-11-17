@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.mylive.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.*
import org.jetbrains.skia.Image
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Surface
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.GraphicsApi
import org.json.JSONObject
import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface
import org.openani.mediamp.mpv.utils.findSkiaLayer
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Exercises the real GPU and video surface without ever showing or focusing a window. */
@OptIn(InternalMediampApi::class, ExperimentalComposeUiApi::class)
class NativeDesktopRenderingTest {
    companion object {
        /**
         * Hosted runners only have Microsoft OpenGL 1.1. CI puts llvmpipe in a Mesa-only
         * directory and this process searches there before System32. The DLLs stay out of
         * JAVA_HOME so jpackage cannot copy them into the bundled JRE.
         * A local run without that directory keeps the machine's GPU driver.
         */
        @JvmStatic
        @BeforeClass
        fun preferMesaDriverWhenPresent() {
            val driver = System.getProperty("mylive.mesaDriver")?.let(::File) ?: return
            if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
            if (!File(driver, "opengl32.dll").isFile) return
            check(TestKernel32.INSTANCE.SetDllDirectory(driver.absolutePath)) {
                "Could not add the test OpenGL directory to the DLL search path: ${driver.absolutePath}"
            }
        }
    }

    @Test fun legacyReadbackSurfaceRemainsAvailable() = runBlocking {
        prepareDesktopMediaRuntime(File(System.getProperty("mylive.nativeRuntime")))
        val previous = System.getProperty("mylive.video.upload")
        System.setProperty("mylive.video.upload", "legacy")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val (window, playback) = withContext(Dispatchers.Main) {
            val window = ComposeWindow().apply { focusableWindowState = false; setSize(640, 360) }
            val playback = MpvDesktopPlayback(window, scope).apply { applyPreferences(DesktopPlayerPreferences(volume = 0f, muted = true)) }
            window.setContent { Box(Modifier.fillMaxSize().background(Color.Black)) { DesktopVideoSurface(playback, Modifier.fillMaxSize()) } }
            window.addNotify(); window.validate(); window.renderImmediately()
            window to playback
        }
        try {
            val media = File(javaClass.getResource("/sample.flv")!!.toURI())
            withContext(Dispatchers.Main) { playback.open(media.absolutePath, emptyMap()) }
            withTimeout(15_000) {
                while (true) {
                    delay(50)
                    val displayed = withContext(Dispatchers.Main) {
                        window.renderImmediately()
                        captureGpuSurface(window.findSkiaLayer()!!).use { bitmap ->
                            val colors = (1..8).flatMap { y ->
                                (1..8).map { x -> bitmap.getColor(bitmap.width * x / 9, bitmap.height * y / 9) }
                            }.toSet()
                            colors.size > 12
                        }
                    }
                    if (displayed) break
                }
            }
        } finally {
            withContext(Dispatchers.Main) { window.dispose(); playback.close() }
            scope.cancel()
            if (previous == null) System.clearProperty("mylive.video.upload") else System.setProperty("mylive.video.upload", previous)
        }
    }

    @Test fun hiddenOpenGlWindowDisplaysHardwareDecodedVideo() = runBlocking {
        assumeTrue(
            "Hardware decode needs a GPU. GitHub-hosted runners set CI=true and have no D3D device, " +
                "so this check is skipped there and still runs on a local Windows machine with a GPU.",
            !System.getenv("CI").equals("true", ignoreCase = true)
        )
        prepareDesktopMediaRuntime(File(System.getProperty("mylive.nativeRuntime")))
        val reportDirectory = Path.of(System.getProperty("mylive.nativeRenderingReport"))
        Files.createDirectories(reportDirectory)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val preferences = DesktopPlayerPreferences(volume = 0f, muted = true)
        val (window, playback) = withContext(Dispatchers.Main) {
            val window = ComposeWindow().apply {
                isUndecorated = true
                focusableWindowState = false
                setSize(800, 450)
            }
            val playback = MpvDesktopPlayback(window, scope)
            assertFalse(playback.playerInitialized)
            playback.applyPreferences(preferences)
            assertFalse(playback.playerInitialized)
            window.setContent {
                DesktopTheme(DesktopAppearancePreferences()) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        DesktopVideoSurface(playback, Modifier.fillMaxSize())
                    }
                }
            }
            window.addNotify()
            window.validate()
            window.renderImmediately()
            assertFalse(window.isVisible)
            assertEquals(GraphicsApi.OPENGL, window.renderApi)
            window to playback
        }
        try {
            val handle = playback.player.impl as MPVHandle
            assertEquals(0.0, handle.getPropertyDouble("volume"), 0.001)
            assertTrue(handle.getPropertyBoolean("mute"))
            val media = File(javaClass.getResource("/sample.flv")!!.toURI())
            withContext(Dispatchers.Main) {
                handle.setPropertyString("loop-file", "inf")
                playback.open(media.absolutePath, emptyMap())
            }
            val dimensions = IntArray(2)
            withTimeout(15_000) {
                while (true) {
                    val frame = withContext(Dispatchers.Main) {
                        window.renderImmediately()
                        playback.player.readSurfacePixels(dimensions)
                    }
                    if (frame != null && frame.any { (it and 0x00ffffff) != 0 }) break
                    delay(20)
                }
            }
            repeat(16) {
                delay(50)
                withContext(Dispatchers.Main) { window.renderImmediately() }
            }
            withContext(Dispatchers.Main) {
                window.renderImmediately()
                val layer = window.findSkiaLayer()!!
                captureGpuSurface(layer).use { bitmap ->
                    val colors = (1..8).flatMap { y ->
                        (1..8).map { x -> bitmap.getColor(bitmap.width * x / 9, bitmap.height * y / 9) }
                    }.toSet()
                    Image.makeFromBitmap(bitmap).use { image ->
                        image.encodeToData()!!.use { data ->
                            Files.write(reportDirectory.resolve("hidden-opengl-video.png"), data.bytes)
                        }
                    }
                    assertTrue("The video must reach the Compose GPU surface: ${colors.size} colors, ${playback.videoUploadStats}", colors.size > 12)
                }
                val diagnostics = playback.diagnostics()
                assertTrue(diagnostics.hardwareDecoder in setOf("d3d11va", "d3d11va-copy"))
                val gpuContext = playback.player.createSkiaInterop(layer)!!.directContext!!
                assertEquals(DESKTOP_GRAPHICS_CACHE_BYTES, gpuContext.resourceCacheLimit)
                val defaultCacheLimit = DirectContext.makeGL().use { it.resourceCacheLimit }
                val report = JSONObject().put("version", DESKTOP_VERSION)
                    .put("renderApi", window.renderApi.name).put("renderInfo", layer.renderInfo)
                    .put("windowVisible", window.isVisible)
                    .put("surfaceWidth", dimensions[0]).put("surfaceHeight", dimensions[1])
                    .put("decoder", diagnostics.hardwareDecoder).put("passed", true)
                    .put("resourceCacheBudgetBytes", gpuContext.resourceCacheLimit).put("platformDefaultCacheBudgetBytes", defaultCacheLimit)
                    .put("uploadStats", JSONObject().put("cpuBuffers", playback.videoUploadStats.cpuBuffers)
                        .put("gpuTextures", playback.videoUploadStats.gpuTextures).put("uploads", playback.videoUploadStats.uploads))
                assertTrue("Video frames continue arriving: ${playback.videoUploadStats}", playback.videoUploadStats.uploads > 1)
                assertEquals(0L, playback.videoUploadStats.cpuBuffers)
                assertTrue(playback.videoUploadStats.gpuTextures <= 4)
                Files.writeString(reportDirectory.resolve("hidden-opengl-video.json"), report.toString(2))
                assertFalse(window.isVisible)
            }
            withContext(Dispatchers.Main) { playback.pause() }
            delay(150)
            val pausedPosition = playback.diagnostics().positionSeconds
            // A second Compose window deliberately leaves a different GL context
            // current before the player's next recording pass.
            withContext(Dispatchers.Main) {
                val popup = ComposeWindow().apply {
                    focusableWindowState = false
                    setSize(240, 180)
                    setContent { Box(Modifier.fillMaxSize().background(Color.Blue)) }
                    addNotify(); validate(); renderImmediately()
                }
                try {
                    window.renderImmediately()
                    captureGpuSurface(window.findSkiaLayer()!!).use { bitmap ->
                        assertTrue(bitmap.getColor(bitmap.width / 2, bitmap.height / 2) != 0xff000000.toInt())
                    }
                } finally { popup.dispose() }
                window.removeNotify()
                window.isUndecorated = false
                window.addNotify(); window.validate(); window.renderImmediately()
            }
            repeat(6) {
                delay(50)
                withContext(Dispatchers.Main) { window.renderImmediately() }
            }
            withContext(Dispatchers.Main) {
                assertFalse(playback.player.state.value.playWhenReady)
                assertEquals(pausedPosition, playback.diagnostics().positionSeconds, .1)
                captureGpuSurface(window.findSkiaLayer()!!).use { bitmap ->
                    val colors = (1..8).flatMap { y -> (1..8).map { x -> bitmap.getColor(bitmap.width * x / 9, bitmap.height * y / 9) } }.toSet()
                    assertTrue("A paused frame survives an AWT peer rebuild", colors.size > 12)
                    Image.makeFromBitmap(bitmap).use { image ->
                        image.encodeToData()!!.use { Files.write(reportDirectory.resolve("paused-peer-rebuild.png"), it.bytes) }
                    }
                }
                playback.resume()
            }
            withTimeout(5_000) {
                while (playback.diagnostics().positionSeconds <= pausedPosition + .2) delay(50)
            }
        } finally {
            withContext(Dispatchers.Main) {
                window.dispose()
                playback.close()
            }
            scope.cancel()
        }
    }

    private fun captureGpuSurface(layer: SkiaLayer): Bitmap {
        val redrawer = SkiaLayer::class.java.getMethod("getRedrawer\$skiko").invoke(layer)
        val handler = redrawer.javaClass.getDeclaredField("contextHandler").apply { isAccessible = true }.get(redrawer)
        val surface = Class.forName("org.jetbrains.skiko.context.ContextHandler")
            .getDeclaredField("surface").apply { isAccessible = true }.get(handler) as Surface
        return Bitmap().apply {
            allocN32Pixels(surface.width, surface.height)
            assertTrue("Read the actual GPU render target", surface.readPixels(this, 0, 0))
        }
    }
}

interface TestKernel32 : StdCallLibrary {
    fun SetDllDirectory(path: String?): Boolean

    companion object {
        val INSTANCE: TestKernel32 = Native.load(
            "kernel32",
            TestKernel32::class.java,
            W32APIOptions.DEFAULT_OPTIONS
        )
    }
}
