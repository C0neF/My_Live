package com.mylive.desktop

import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.site.LiveSiteAccount
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpCookie

class DesktopLoginServiceTest {
    @Test fun qrApiEncodesOpaqueKeysAndReadsSessionCookiesFromHeaders() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"qrcode_key":"a&b=1","url":"https://passport.bilibili.com/fixture"}}"""))
            server.enqueue(MockResponse().addHeader("Set-Cookie", "SESSDATA=fixture; HttpOnly; Path=/")
                .addHeader("Set-Cookie", "DedeUserID=12; Path=/").setBody("""{"code":0,"data":{"code":0}}"""))
            val service = DesktopLoginService(HttpClient(OkHttpClient()), DesktopAccounts(), server.url("/").toString().trimEnd('/'))
            val challenge = service.challenge(); val result = service.poll(challenge.key)
            assertEquals(0, result.code); assertTrue(result.cookie.contains("SESSDATA=fixture"))
            server.takeRequest(); assertEquals("a&b=1", server.takeRequest().requestUrl!!.queryParameter("qrcode_key"))
            assertFalse(result.toString().contains("fixture")); assertFalse(challenge.toString().contains("a&b"))
        }
    }
    @Test fun qrChallengeRejectsAnUnrelatedDomain() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"qrcode_key":"fixture","url":"https://bilibili.com.attacker.invalid/"}}"""))
            val service = DesktopLoginService(HttpClient(OkHttpClient()), DesktopAccounts(), server.url("/").toString().trimEnd('/'))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { service.challenge() } }
        }
    }
    @Test fun webLoginCookieCaptureRejectsVisitorOnlyCookiesAndUnrelatedDomains() {
        fun cookie(name: String, value: String, domain: String) = HttpCookie(name, value).apply { this.domain = domain }
        val cookies = listOf(cookie("sessionid", "fixture", ".douyin.com"), cookie("private", "outside", "douyin.com.attacker.invalid"))
        assertEquals("sessionid=fixture", loginCookieHeader(LiveSiteAccount.DOUYIN, cookies))
        assertEquals("", loginCookieHeader(LiveSiteAccount.DOUYIN, listOf(cookie("msToken", "visitor", ".douyin.com"))))
        assertEquals("", loginCookieHeader(LiveSiteAccount.DOUYU, cookies))
        assertEquals("dy_auth=fixture", loginCookieHeader(LiveSiteAccount.DOUYU, listOf(cookie("dy_auth", "fixture", ".douyu.com"))))
    }
}
