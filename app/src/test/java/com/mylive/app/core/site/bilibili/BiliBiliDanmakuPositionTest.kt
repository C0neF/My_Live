package com.mylive.app.core.site.bilibili

import com.mylive.app.core.common.WebSocketUtils
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageDanmakuPosition
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BiliBiliDanmakuPositionTest {

    @Test
    fun modeFourMapsToBottomFixedDanmaku() {
        assertEquals(
            LiveMessageDanmakuPosition.BOTTOM,
            resolveBiliBiliDanmakuPosition(4)
        )
    }

    @Test
    fun modeFiveMapsToTopFixedDanmaku() {
        assertEquals(
            LiveMessageDanmakuPosition.TOP,
            resolveBiliBiliDanmakuPosition(5)
        )
    }

    @Test
    fun otherModesRemainScrollingDanmaku() {
        listOf(1, 2, 3, 6, 7, 8).forEach { mode ->
            assertEquals(
                "mode=$mode",
                LiveMessageDanmakuPosition.SCROLL,
                resolveBiliBiliDanmakuPosition(mode)
            )
        }
    }

    @Test
    fun parserAttachesFixedPositionToEmittedMessage() {
        assertEquals(
            LiveMessageDanmakuPosition.BOTTOM,
            parseChatMessage(mode = 4).danmakuPosition
        )
        assertEquals(
            LiveMessageDanmakuPosition.TOP,
            parseChatMessage(mode = 5).danmakuPosition
        )
    }

    private fun parseChatMessage(mode: Int): LiveMessage {
        val danmaku = BiliBiliDanmaku(WebSocketUtils(OkHttpClient()))
        var captured: LiveMessage? = null
        danmaku.onMessage = { captured = it }

        val parseMethod = BiliBiliDanmaku::class.java
            .getDeclaredMethod("parseMessage", String::class.java)
            .apply { isAccessible = true }
        parseMethod.invoke(
            danmaku,
            """
            {
              "cmd": "DANMU_MSG",
              "info": [
                [25, $mode, 1, 16777215],
                "固定弹幕",
                [1, "测试用户"]
              ]
            }
            """.trimIndent()
        )

        return requireNotNull(captured)
    }
}
