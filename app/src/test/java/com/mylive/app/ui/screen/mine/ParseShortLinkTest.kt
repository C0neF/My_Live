package com.mylive.app.ui.screen.mine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression guard for Douyin share-link parsing.
 *
 * Real Douyin live share codes can contain '_' (e.g. `6yEygkv2_Fo`). The original
 * regex `[a-zA-Z0-9]+` stopped at the underscore, so only `6yEygkv2` was requested;
 * Douyin bounces that truncated code to `https://www.douyin.com`, which has no parse
 * branch -> "无法解析此链接". The fixed charset `[A-Za-z0-9_]+` keeps the full code,
 * which 302s to `webcast.amemv.com/.../reflow/<roomId>` and parses correctly.
 */
class ParseShortLinkTest {

    @Test
    fun vDouyinShortCodeKeepsUnderscore() {
        val shareText =
            "5- #在抖音，记录美好生活#【赛车运动部门】正在直播，来和我一起支持Ta吧。" +
                "复制下方链接，打开【抖音】，直接观看直播！ https://v.douyin.com/6yEygkv2_Fo/ 2@8.com :5pm"

        val match = V_DOUYIN_SHORTLINK_REGEX.find(shareText)?.value

        // Must capture the FULL code including the underscore, not truncate at it.
        assertEquals("https://v.douyin.com/6yEygkv2_Fo", match)
    }

    @Test
    fun vDouyinShortCodeWithoutUnderscoreStillMatches() {
        val match = V_DOUYIN_SHORTLINK_REGEX.find("https://v.douyin.com/iEAbCdEf/")?.value
        assertEquals("https://v.douyin.com/iEAbCdEf", match)
    }

    @Test
    fun nonDouyinShortLinkDoesNotMatch() {
        assertNull(V_DOUYIN_SHORTLINK_REGEX.find("https://b23.tv/abc123")?.value)
    }
}
