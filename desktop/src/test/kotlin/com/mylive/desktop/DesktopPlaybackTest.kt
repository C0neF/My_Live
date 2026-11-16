package com.mylive.desktop

import com.mylive.app.core.model.LivePlayUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPlaybackTest {
    @Test fun aLateSourceAppliesTheLatestPauseIntentAndTheNextSourceStartsPaused() = runTest {
        val intent = DesktopPlaybackIntent()
        val sourceReady = CompletableDeferred<Unit>()
        val commands = mutableListOf<Boolean>()
        launch { intent.loadSource({ commands += it; sourceReady.await() }, { commands += it }) }
        runCurrent()
        intent.request(false)
        sourceReady.complete(Unit); runCurrent()
        assertEquals(listOf(true, false), commands)
        intent.loadSource({ commands += it }, { commands += it })
        assertEquals(listOf(true, false, false), commands)
    }

    @Test fun resumingDuringALoadWinsOverTheOlderPausedSourceRequest() = runTest {
        val intent = DesktopPlaybackIntent().apply { request(false) }
        val sourceReady = CompletableDeferred<Unit>()
        val commands = mutableListOf<Boolean>()
        launch { intent.loadSource({ commands += it; sourceReady.await() }, { commands += it }) }
        runCurrent()
        intent.request(true)
        sourceReady.complete(Unit); runCurrent()
        assertEquals(listOf(false, true), commands)
    }
    @Test fun nativeErrorsNeverLogSignedUrlsOrAccountHeaders() {
        val line = sanitizeMediaLogLine("Failed https://user:password@cdn.example/live.flv?token=secret\nCookie: SESSDATA=secret\nAuthorization: Bearer secret")
        assertFalse(line.contains("secret"))
        assertFalse(line.contains("password"))
        assertFalse(line.contains("SESSDATA"))
        assertTrue(line.contains("cdn.example"))
    }

    @Test fun filePickerUriBecomesAPlatformFilePath() {
        val file = java.io.File("build/test video.mp4").absoluteFile
        assertEquals(file.toPath().toString(), normalizeMediaLocation(file.toURI().toString()))
        assertEquals("https://cdn.example/live.flv?key=abc", normalizeMediaLocation("https://cdn.example/live.flv?key=abc"))
    }

    @Test fun cdnFailureFallsBackAndPreservesHeaders() = runBlocking {
        val attempts = mutableListOf<Pair<String, Map<String, String>>>()
        val playback = object : DesktopPlayback {
            override suspend fun open(url: String, headers: Map<String, String>) {
                attempts += url to headers
                if (url == "bad") error("CDN unavailable")
            }
            override fun stop() {}
            override fun close() {}
        }
        val headers = mapOf("Referer" to "https://www.douyu.com/", "User-Agent" to "test")
        assertEquals(1, openPlaybackLine(playback, LivePlayUrl(listOf("bad", "good"), headers)))
        assertEquals(listOf("bad", "good"), attempts.map { it.first })
        assertTrue(attempts.all { it.second == headers })
    }

    @Test fun cancellationNeverFallsBackToAnotherCdn() {
        var attempts = 0
        val playback = object : DesktopPlayback {
            override suspend fun open(url: String, headers: Map<String, String>) {
                attempts++
                throw CancellationException("room changed")
            }
            override fun stop() {}
            override fun close() {}
        }
        assertThrows(CancellationException::class.java) {
            runBlocking { openPlaybackLine(playback, LivePlayUrl(listOf("first", "second"))) }
        }
        assertEquals(1, attempts)
    }

    @Test fun preferredLineIsTriedFirst() = runBlocking {
        var opened = ""
        val playback = object : DesktopPlayback {
            override suspend fun open(url: String, headers: Map<String, String>) { opened = url }
            override fun stop() {}
            override fun close() {}
        }
        assertEquals(2, openPlaybackLine(playback, LivePlayUrl(listOf("a", "b", "c")), 2))
        assertEquals("c", opened)
    }
}
