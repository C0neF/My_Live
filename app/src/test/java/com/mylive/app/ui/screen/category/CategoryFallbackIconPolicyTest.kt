package com.mylive.app.ui.screen.category

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryFallbackIconPolicyTest {

    @Test
    fun douyinCategoryNamesMapToStableFallbackIcons() {
        assertEquals("chat", categoryFallbackIconKey("聊天"))
        assertEquals("music", categoryFallbackIconKey("音乐"))
        assertEquals("game", categoryFallbackIconKey("射击游戏"))
        assertEquals("game", categoryFallbackIconKey("竞技游戏"))
        assertEquals("cards", categoryFallbackIconKey("棋牌"))
        assertEquals("sport", categoryFallbackIconKey("运动"))
    }
}
