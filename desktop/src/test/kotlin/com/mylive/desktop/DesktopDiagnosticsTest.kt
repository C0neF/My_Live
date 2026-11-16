package com.mylive.desktop

import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DesktopDiagnosticsTest {
    @Test fun exportedLogsNeverContainCredentialsSignedPathsOrUserHome() {
        DesktopDiagnostics.clear()
        val secrets = listOf("cookie-value", "password-value", "sess-value", "token-value", "user-secret", "signed-secret", "web-secret")
        DesktopDiagnostics.record("TEST", "Cookie: cookie-value\npassword=password-value\nSESSDATA=sess-value\ntoken=token-value\nC:\\Users\\user-secret\\file\nhttps://cdn.example/signed-secret?auth=secret\nwss://example/web-secret")
        val text = DesktopDiagnostics.snapshot(DesktopRoomState(), PlaybackDiagnostics())
        secrets.forEach { assertFalse(it, text.contains(it)) }
        repeat(510) { DesktopDiagnostics.record("TEST", "entry$it") }
        assertEquals(500, JSONObject(DesktopDiagnostics.snapshot(DesktopRoomState(), PlaybackDiagnostics())).getJSONArray("logs").length())
        DesktopDiagnostics.clear()
    }
    @Test fun updatesUseSemanticVersionAndOnlyOfferWindowsAssetsFromThisRepository() {
        assertTrue(desktopVersionIsNewer("v3.10.0", "3.9.9")); assertFalse(desktopVersionIsNewer("3.0.0", "3.0.0"))
        assertFalse(desktopVersionIsNewer("3.1.0-beta", "3.0.0"))
        val release = DesktopUpdates(OkHttpClient()).decode("""{"tag_name":"v3.1.0","html_url":"https://github.com/C0neF/My_Live/releases/tag/v3.1.0",
          "assets":[{"name":"app.apk","browser_download_url":"https://github.com/C0neF/My_Live/releases/download/v3.1.0/app.apk"},
          {"name":"MyLive-Windows.zip","browser_download_url":"https://github.com/C0neF/My_Live/releases/download/v3.1.0/MyLive-Windows.zip"},
          {"name":"fake.exe","browser_download_url":"https://evil.example/fake.exe"}]}""")
        assertTrue(release.newer); assertEquals(listOf("MyLive-Windows.zip"), release.windowsAssets.map { it.first })
    }
}
