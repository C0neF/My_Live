package com.mylive.desktop

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test

/** Opt in with -PdesktopLiveRoom=douyu:9999; network availability is not a unit-test assumption. */
class LiveStreamIntegrationTest {
    private suspend fun roomId(site: com.mylive.app.core.site.LiveSite, target: String): String {
        if (target.substringAfter(':') != "auto") return target.substringAfter(':')
        for (item in site.getRecommendRooms(1).items.take(20)) {
            val detail = runCatching { site.getRoomDetail(item.roomId) }.getOrNull()
            if (detail?.status == true) { println("${site.id} selected live room ${detail.roomId}"); return detail.roomId }
        }
        error("No currently live room found in the recommendation sample")
    }
    @Test fun connectsLiveChat() = runBlocking {
        val target = System.getProperty("mylive.liveRoom")
        assumeTrue("Pass -PdesktopLiveRoom=site:room for a live network probe", target != null)
        DesktopServices().use { services ->
            val site = services.sites.first { it.id == target.substringBefore(':') }
            val id = roomId(site, target)
            val detail = site.getRoomDetail(id)
            val session = site.createDanmakuSession(id, detail)
            val ready = CompletableDeferred<Unit>()
            session.onReady = { ready.complete(Unit) }
            try {
                session.start()
                withTimeout(25_000) { ready.await() }
                println("${site.id} chat connected")
            } finally { session.stop() }
        }
    }

    @Test fun resolvesLiveSourceAndReadsMediaWithoutExposingSignedUrl() = runBlocking {
        val target = System.getProperty("mylive.liveRoom")
        assumeTrue("Pass -PdesktopLiveRoom=site:room for a live network probe", target != null)
        DesktopServices().use { services ->
            val site = services.sites.first { it.id == target.substringBefore(':') }
            val room = site.getRoomDetail(roomId(site, target))
            assertTrue("Room must be live for this integration test", room.status)
            val qualities = site.getPlayQualites(room)
            assertTrue(qualities.isNotEmpty())
            println("Platform ${site.id}: ${qualities.map { it.quality }}")
            val source = site.getPlayUrls(room, qualities.first())
            assertTrue(source.urls.isNotEmpty())
            var playable = false
            source.urls.forEach { url ->
                val request = Request.Builder().url(url).apply {
                    source.headers.orEmpty().forEach { (key, value) -> header(key, value) }
                }.build()
                try {
                    services.client.newCall(request).execute().use { response ->
                        val bytes = response.body?.source()?.readByteArray(16)
                        println("CDN ${request.url.host}: HTTP ${response.code}, media prefix ${bytes?.take(4)}")
                        playable = playable || (response.isSuccessful && bytes?.isNotEmpty() == true)
                    }
                } catch (error: Exception) {
                    println("CDN ${request.url.host}: ${error.javaClass.simpleName}")
                }
            }
            assertTrue("No CDN returned a readable media body", playable)
        }
    }
}
