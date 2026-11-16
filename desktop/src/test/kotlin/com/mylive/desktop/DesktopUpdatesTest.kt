package com.mylive.desktop

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DesktopUpdatesTest {
    private fun release(tag: String, names: List<String>, draft: Boolean = false, prerelease: Boolean = false) = JSONObject()
        .put("tag_name", tag).put("html_url", "https://github.com/C0neF/My_Live/releases/tag/$tag")
        .put("draft", draft).put("prerelease", prerelease)
        .put("assets", JSONArray(names.map { name -> JSONObject().put("name", name)
            .put("browser_download_url", "https://github.com/C0neF/My_Live/releases/download/$tag/$name") }))

    private fun query(rows: List<JSONObject>): DesktopRelease? {
        val server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url("/releases")).build())
        }.build()
        try {
            server.enqueue(MockResponse().setBody(JSONArray(rows).toString()))
            return runBlocking { DesktopUpdates(client).latest() }
        } finally { server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun windowsUpdatesRemainAvailableWhenTheNewestReleaseOnlyContainsAndroid() {
        val found = requireNotNull(query(listOf(
            release("v9.0.0", listOf("MyLive-v9.0.0.apk")),
            release("windows-v2.2.0", listOf("MyLive.exe")),
            release("windows-v2.1.1", listOf("MyLive-2.1.1.msi"))
        )))
        assertEquals("2.2.0", found.version)
        assertEquals(listOf("MyLive.exe"), found.windowsAssets.map { it.first })
    }

    @Test fun draftPreviewAndSourceOnlyReleasesAreNotOfferedAsStableUpdates() {
        val found = requireNotNull(query(listOf(
            release("windows-v9.0.0", listOf("MyLive-9.0.0.msi"), draft = true),
            release("windows-v8.0.0", listOf("MyLive-8.0.0.msi"), prerelease = true),
            release("windows-v7.0.0", listOf("MyLive-Windows-sources-7.0.0.zip")),
            release("windows-v2.1.2", listOf("MyLive-2.1.2.msi"))
        )))
        assertEquals("2.1.2", found.version)
        assertNull(query(listOf(release("v2.1.0", listOf("MyLive-v2.1.0.apk")))))
    }

    @Test fun windowsTagVersionsUseNumericOrderingAndExcludePrereleases() {
        assertTrue(desktopVersionIsNewer("windows-v2.10.0", "2.9.9"))
        assertFalse(desktopVersionIsNewer("windows-v2.1.1", "2.1.1"))
        assertFalse(desktopVersionIsNewer("windows-v9.0.0-rc.1", "2.1.1"))
    }

    @Test fun downloadAssetsMustBelongToTheSameReleaseAndExcludeSourceArchives() {
        val row = release("v2.2.0", emptyList())
        row.put("assets", JSONArray().put(JSONObject().put("name", "MyLive-2.2.0.exe")
            .put("browser_download_url", "https://github.com/C0neF/My_Live/releases/download/v8.0.0/MyLive-2.2.0.exe"))
            .put(JSONObject().put("name", "MyLive-Windows-sources-2.2.0.zip")
                .put("browser_download_url", "https://github.com/C0neF/My_Live/releases/download/v2.2.0/MyLive-Windows-sources-2.2.0.zip")))
        assertTrue(DesktopUpdates(OkHttpClient()).decode(row.toString()).windowsAssets.isEmpty())
    }

    @Test fun releaseLinksCannotCarryCredentialsOrUnexpectedTags() {
        val row = release("v2.2.0", listOf("MyLive-2.2.0.msi"))
        row.put("html_url", "https://user@github.com/C0neF/My_Live/releases/tag/v2.2.0")
        assertThrows(IllegalArgumentException::class.java) { DesktopUpdates(OkHttpClient()).decode(row.toString()) }
        row.put("html_url", "https://github.com/C0neF/My_Live/releases/tag/v9.0.0")
        assertThrows(IllegalArgumentException::class.java) { DesktopUpdates(OkHttpClient()).decode(row.toString()) }
    }
}
