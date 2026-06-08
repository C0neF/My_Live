package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaybackNetworkSecurityPolicyTest {

    @Test
    fun playbackAllowsCleartextLiveCdnUrls() {
        val config = File("src/main/res/xml/network_security_config.xml").readText()

        assertTrue(
            "Live playback URLs from Huya/Douyin CDNs can be plain http://, so playback must allow cleartext CDN traffic",
            config.contains("""<base-config cleartextTrafficPermitted="true"""")
        )
    }
}
