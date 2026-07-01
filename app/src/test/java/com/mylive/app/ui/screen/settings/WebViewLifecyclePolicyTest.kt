package com.mylive.app.ui.screen.settings

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class WebViewLifecyclePolicyTest {

    @Test
    fun bilibiliLoginWebViewIsDestroyedWhenAndroidViewIsReleased() {
        val source = File("src/main/java/com/mylive/app/ui/screen/settings/LoginWebViewScreen.kt").readText()

        assertTrue(source.contains("onRelease = { view ->"))
        assertTrue(source.contains("view.stopLoading()"))
        assertTrue(source.contains("view.destroy()"))
    }

    @Test
    fun douyinLoginWebViewIsDestroyedAndClearsRememberedReferenceWhenAndroidViewIsReleased() {
        val source = File("src/main/java/com/mylive/app/ui/screen/settings/DouyinWebLoginScreen.kt").readText()

        assertTrue(source.contains("onRelease = { view ->"))
        assertTrue(source.contains("if (webViewInstance === view)"))
        assertTrue(source.contains("webViewInstance = null"))
        assertTrue(source.contains("view.stopLoading()"))
        assertTrue(source.contains("view.destroy()"))
    }

    @Test
    fun douyinCookieParsingPreservesEqualsInsideCookieValues() {
        val source = File("src/main/java/com/mylive/app/ui/screen/settings/DouyinWebLoginScreen.kt").readText()

        assertTrue(source.contains("parseCookiePairPreservingEquals(pair)"))
        assertTrue(source.contains("pair.indexOf('=')"))
        assertFalse(source.contains("pair.split(\"=\")"))
    }

    @Test
    fun loginWebViewsRejectNonHttpsNavigationAndLocalContentAccess() {
        val bilibili = File("src/main/java/com/mylive/app/ui/screen/settings/LoginWebViewScreen.kt").readText()
        val douyin = File("src/main/java/com/mylive/app/ui/screen/settings/DouyinWebLoginScreen.kt").readText()

        listOf(bilibili, douyin).forEach { source ->
            assertTrue(source.contains("settings.allowFileAccess = false"))
            assertTrue(source.contains("settings.allowContentAccess = false"))
            assertTrue(source.contains("settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW"))
            assertTrue(source.contains("if (!isSafeLoginWebUrl(url)) return true"))
        }
        assertTrue(bilibili.contains("internal fun isSafeLoginWebUrl("))
    }
}
