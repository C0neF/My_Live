package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageColor
import com.mylive.app.core.model.LiveMessageType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveMessageShieldPolicyTest {

    @Test
    fun shieldHotPathUsesPrecompiledRules() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveMessageShieldPolicy.kt")
            .readText()
        val hotPath = source.substringAfter("internal fun shouldShieldLiveMessage(")
            .substringBefore("private data class UserShieldRule")

        assertTrue(source.contains("val keywordRules: KeywordShieldRules"))
        assertTrue(source.contains("val userRules: UserShieldRules"))
        assertFalse(hotPath.contains("Regex("))
        assertFalse(hotPath.contains("keywordShieldValue()"))
        assertFalse(hotPath.contains("userShieldRule()"))
    }

    @Test
    fun keywordShieldMatchesPlainText() {
        val config = LiveMessageShieldConfig(
            shieldValues = listOf("keyword:广告"),
            shieldEnabled = true,
            keywordShieldEnabled = true,
            userShieldEnabled = false
        )

        assertTrue(shouldShieldLiveMessage(chat(message = "这是一条广告"), "bilibili", config))
    }

    @Test
    fun keywordShieldMatchesRegexFormat() {
        val config = LiveMessageShieldConfig(
            shieldValues = listOf("""keyword:/\d{4}/"""),
            shieldEnabled = true,
            keywordShieldEnabled = true,
            userShieldEnabled = false
        )

        assertTrue(shouldShieldLiveMessage(chat(message = "房间号 1234"), "douyu", config))
    }

    @Test
    fun invalidRegexKeywordDoesNotShieldOrCrash() {
        val config = LiveMessageShieldConfig(
            shieldValues = listOf("keyword:/[/"),
            shieldEnabled = true,
            keywordShieldEnabled = true,
            userShieldEnabled = false
        )

        assertFalse(shouldShieldLiveMessage(chat(message = "普通弹幕"), "douyu", config))
    }

    @Test
    fun keywordShieldCanBeDisabledIndependently() {
        val config = LiveMessageShieldConfig(
            shieldValues = listOf("keyword:广告"),
            shieldEnabled = true,
            keywordShieldEnabled = false,
            userShieldEnabled = true
        )

        assertFalse(shouldShieldLiveMessage(chat(message = "广告"), "bilibili", config))
    }

    @Test
    fun userShieldMatchesGlobalAndCurrentSiteOnly() {
        val config = LiveMessageShieldConfig(
            shieldValues = listOf(
                "user:__all__:全平台用户",
                "user:bilibili:B站用户",
                "user:douyu:斗鱼用户"
            ),
            shieldEnabled = true,
            keywordShieldEnabled = false,
            userShieldEnabled = true
        )

        assertTrue(shouldShieldLiveMessage(chat(userName = "全平台用户"), "huya", config))
        assertTrue(shouldShieldLiveMessage(chat(userName = "B站用户"), "bilibili", config))
        assertFalse(shouldShieldLiveMessage(chat(userName = "B站用户"), "douyu", config))
    }

    @Test
    fun nonChatMessagesBypassDanmuShield() {
        val config = LiveMessageShieldConfig(
            shieldValues = listOf("keyword:广告", "user:__all__:坏用户"),
            shieldEnabled = true,
            keywordShieldEnabled = true,
            userShieldEnabled = true
        )

        assertFalse(
            shouldShieldLiveMessage(
                LiveMessage(
                    type = LiveMessageType.SUPER_CHAT,
                    userName = "坏用户",
                    message = "广告",
                    color = LiveMessageColor.WHITE
                ),
                "bilibili",
                config
            )
        )
    }

    @Test
    fun globalShieldSwitchDisablesAllRules() {
        val config = LiveMessageShieldConfig(
            shieldValues = listOf("keyword:广告", "user:__all__:坏用户"),
            shieldEnabled = false,
            keywordShieldEnabled = true,
            userShieldEnabled = true
        )

        assertFalse(shouldShieldLiveMessage(chat(userName = "坏用户", message = "广告"), "bilibili", config))
    }

    private fun chat(
        userName: String = "Alice",
        message: String = "你好"
    ): LiveMessage {
        return LiveMessage(
            type = LiveMessageType.CHAT,
            userName = userName,
            message = message,
            color = LiveMessageColor.WHITE
        )
    }
}
