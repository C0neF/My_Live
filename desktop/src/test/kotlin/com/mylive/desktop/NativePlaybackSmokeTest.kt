package com.mylive.desktop

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.openani.mediamp.mpv.EventListener
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import java.nio.file.Files

/** Runs separately from unit tests. CI must exercise the packaged DLLs, not just compile the player. */
class NativePlaybackSmokeTest {
    @Test fun decodesLocalFlv() = runBlocking {
        val fixture = Files.createTempFile("mylive-native-", ".flv")
        try {
            Files.write(fixture, fixtureBytes())
            assertDecodes(fixture.toString())
        } finally { Files.deleteIfExists(fixture) }
    }

    @Test fun decodesHttpFlvWithRequiredHeaders() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "video/x-flv").setBody(Buffer().write(fixtureBytes())))
            server.start()
            assertDecodes(server.url("/live.flv").toString(), referer = "https://www.douyu.com/9999")
            val request = server.takeRequest()
            assertEquals("https://www.douyu.com/9999", request.getHeader("Referer"))
            assertEquals("MyLive native test", request.getHeader("User-Agent"))
        }
    }

    private suspend fun assertDecodes(uri: String, referer: String = "") {
        MPVHandle(Any()).use { handle ->
            handle.setEventListener(object : EventListener {
                override fun onPropertyChange(name: String) {}
                override fun onPropertyChange(name: String, value: Boolean) {}
                override fun onPropertyChange(name: String, value: Long) {}
                override fun onPropertyChange(name: String, value: Double) {}
                override fun onPropertyChange(name: String, value: String) {}
                override fun onEvent(event: Int) {}
                override fun onStartFile(playlistEntryId: Long) {}
                override fun onEndFile(reason: Int, mpvError: Int, playlistEntryId: Long) {}
            })
            check(handle.option("vo", "null"))
            check(handle.option("ao", "null"))
            check(handle.option("keep-open", "yes"))
            check(handle.option("user-agent", "MyLive native test"))
            check(handle.option("referrer", referer))
            check(handle.initialize())
            check(handle.command("loadfile", uri))
            withTimeout(10_000) {
                while (handle.getPropertyInt("width") <= 0) delay(25)
            }
            assertEquals(320, handle.getPropertyInt("width"))
            assertEquals(180, handle.getPropertyInt("height"))
            assertEquals("h264", handle.getPropertyString("video-format"))
        }
    }

    private fun fixtureBytes() = javaClass.getResourceAsStream("/sample.flv")!!.use { it.readBytes() }

    companion object {
        @JvmStatic @BeforeClass fun configureLibraries() {
            MpvMediampPlayer.prepareLibraries(System.getProperty("mylive.nativeRuntime"), extractRuntimeLibrary = false)
        }
    }
}
