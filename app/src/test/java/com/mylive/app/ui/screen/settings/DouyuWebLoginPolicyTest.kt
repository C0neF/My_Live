package com.mylive.app.ui.screen.settings

import android.webkit.CookieManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DouyuWebLoginPolicyTest {
    @Test fun onlyOfficialHttpsPagesTriggerCookieCollection() {
        assertTrue(isDouyuLoginPage("https://passport.douyu.com/"))
        assertTrue(isDouyuLoginPage("https://www.douyu.com/71415"))
        assertFalse(isDouyuLoginPage("https://douyu.com.example.invalid/"))
        assertFalse(isDouyuLoginPage("http://passport.douyu.com/"))
        assertFalse(isDouyuLoginPage("file:///data/cookie"))
    }

    @Test fun collectorReadsWebsiteAndPassportDomainCookiesTogether() {
        val manager = CookieManager.getInstance()
        manager.setCookie("https://www.douyu.com", "dy_auth=test-login; Path=/; Secure")
        manager.setCookie("https://passport.douyu.com", "LTP0=test-renewal; Path=/; Secure; HttpOnly")
        manager.setCookie("https://passport.douyu.com", "dy_did=test-device; Path=/; Secure")
        val cookie = readDouyuWebCookies(manager)
        assertTrue(cookie.contains("dy_auth=test-login"))
        assertTrue(cookie.contains("LTP0=test-renewal"))
    }

    @Test fun clearingDouyuCookiesPreservesOtherPlatformSessions() {
        val manager = CookieManager.getInstance()
        manager.setCookie("https://www.douyu.com", "dy_auth=to-clear; Path=/; Secure")
        manager.setCookie("https://www.bilibili.com", "SESSDATA=keep-bili; Path=/; Secure")
        clearDouyuWebCookies()
        assertFalse(manager.getCookie("https://www.douyu.com").orEmpty().contains("to-clear"))
        assertTrue(manager.getCookie("https://www.bilibili.com").orEmpty().contains("keep-bili"))
    }
}
