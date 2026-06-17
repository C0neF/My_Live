package com.mylive.app.ui.screen.settings

import org.junit.Assert.assertTrue
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
}
