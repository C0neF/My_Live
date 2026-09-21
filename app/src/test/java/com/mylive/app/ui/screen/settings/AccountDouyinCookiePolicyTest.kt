package com.mylive.app.ui.screen.settings

import com.mylive.app.core.site.douyin.DouyinCookieInspection
import com.mylive.app.core.site.douyin.DouyinCookieKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDouyinCookiePolicyTest {

    @Test
    fun statusCopyDescribesStructureWithoutClaimingThatTheCookieIsValid() {
        val inspection = DouyinCookieInspection(
            kind = DouyinCookieKind.LoginStateShape,
            estimatedExpiryEpochMillis = 3_700_000L
        )

        assertEquals("检测到登录态", douyinCookieStatusLabel(inspection.kind))
        val description = douyinCookieStatusDescription(inspection, nowMillis = 100_000L)
        assertTrue(description.contains("预计剩余 1 小时"))
        assertTrue(description.contains("服务端"))
        assertFalse(description.contains("有效 Cookie"))
    }

    @Test
    fun ttwidIsPresentedAsPlaybackOnlyRatherThanLoggedIn() {
        val inspection = DouyinCookieInspection(DouyinCookieKind.TtwidOnly)

        assertEquals("仅 ttwid", douyinCookieStatusLabel(inspection.kind))
        assertTrue(douyinCookieStatusDescription(inspection, 0L).contains("搜索与关注刷新可能受限"))
        assertFalse(isDouyinLoginState(inspection))
        assertTrue(isDouyinLoginState(DouyinCookieInspection(DouyinCookieKind.LoginStateShape)))
    }
}
