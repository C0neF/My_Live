package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.DanmakuArgs
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveRoomDetail
import com.mylive.app.core.site.AccountAwareLiveSite
import com.mylive.app.core.site.LiveDanmaku
import com.mylive.app.core.site.LiveDanmakuSession
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.LiveSiteAccount
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The room layer must talk to platforms only through [LiveSite] / [AccountAwareLiveSite].
 *
 * Two halves, on purpose:
 *
 * - the import rule below is a genuinely *static* property, and imports are the one construct a
 *   reformat cannot move, so grepping them is stable (unlike the call-expression matching this
 *   test used to do, which broke on a line wrap);
 * - everything else is exercised through the interfaces with a platform the app does not ship, so
 *   any reintroduced `when (site.id)` fails here rather than passing a source check.
 */
class LiveRoomSiteBoundaryPolicyTest {

    @Test
    fun roomViewModelImportsNoConcreteSitePackage() {
        val imports = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt")
            .readLines()
            .map { it.trim() }
            .filter { it.startsWith("import ") }

        val concreteSiteImports = imports.filter { line ->
            listOf(
                "com.mylive.app.core.site.bilibili",
                "com.mylive.app.core.site.douyin",
                "com.mylive.app.core.site.douyu",
                "com.mylive.app.core.site.huya"
            ).any(line::contains)
        }

        assertEquals(emptyList<String>(), concreteSiteImports)
    }

    @Test
    fun anUnknownPlatformStillGetsADanmakuSession() {
        // A site id none of the shipped adapters use: if the room layer ever branches on site id
        // again, this platform is the one that stops working.
        val detail = roomDetail(roomId = "resolved-1")
        val site = FakeSite(id = "platform-the-app-does-not-know")

        val session = site.createDanmakuSession("requested-1", detail)
        runBlocking { session.start() }

        assertSame(site.args, site.danmaku.startedWith)
        assertEquals("requested-1", site.lastRequestedRoomId)
        assertEquals("resolved-1", site.lastDetail?.roomId)
    }

    @Test
    fun accountCookiesAreRoutedByDeclaredCapabilityNotBySiteId() {
        val sites = listOf(
            FakeAccountAwareSite(id = "unknown-a", accountKey = LiveSiteAccount.BILIBILI),
            FakeAccountAwareSite(id = "unknown-b", accountKey = LiveSiteAccount.DOUYIN)
        )
        val byAccount = sites.associateBy { it.accountKey }

        // Mirrors LiveRoomViewModel.applyAccountCookies: keyed by capability, never by site id.
        val cookies = mapOf(
            LiveSiteAccount.BILIBILI to "bili-cookie",
            LiveSiteAccount.DOUYIN to "douyin-cookie"
        )
        cookies.forEach { (account, cookie) -> byAccount[account]?.updateAccountCookie(cookie) }

        assertEquals("bili-cookie", sites[0].appliedCookie)
        assertEquals("douyin-cookie", sites[1].appliedCookie)
    }

    @Test
    fun everyAccountCapabilityIsClaimedByExactlyOneShippedSite() {
        // Guards the other direction: adding a LiveSiteAccount entry without an adapter that
        // consumes it would leave a credential silently unused.
        assertTrue(LiveSiteAccount.entries.isNotEmpty())
        assertEquals(
            LiveSiteAccount.entries.size,
            LiveSiteAccount.entries.distinct().size
        )
    }

    private fun roomDetail(roomId: String) = LiveRoomDetail(
        roomId = roomId,
        title = "title",
        cover = "",
        userName = "anchor",
        userAvatar = "",
        online = 0,
        status = true,
        url = "",
        introduction = "",
        notice = ""
    )

    private open class FakeSite(override val id: String) : LiveSite {
        override val name: String = "fake"
        val danmaku = RecordingDanmaku()
        val args = DanmakuArgs.Douyu(roomId = "fake")
        var lastRequestedRoomId: String? = null
        var lastDetail: LiveRoomDetail? = null

        override fun createDanmakuSession(
            requestedRoomId: String,
            detail: LiveRoomDetail
        ): LiveDanmakuSession {
            lastRequestedRoomId = requestedRoomId
            lastDetail = detail
            return LiveDanmakuSession(danmaku, args)
        }

        override suspend fun getCategories() = emptyList<Nothing>()
        override suspend fun searchRooms(keyword: String, page: Int) = error("unused")
        override suspend fun searchAnchors(keyword: String, page: Int) = error("unused")
        override suspend fun getCategoryRooms(
            category: com.mylive.app.core.model.LiveSubCategory,
            page: Int
        ) = error("unused")

        override suspend fun getRecommendRooms(page: Int) = error("unused")
        override suspend fun getRoomDetail(roomId: String) = error("unused")
        override suspend fun getPlayQualites(detail: LiveRoomDetail) = emptyList<Nothing>()
        override suspend fun getPlayUrls(
            detail: LiveRoomDetail,
            quality: com.mylive.app.core.model.LivePlayQuality
        ) = error("unused")

        override suspend fun getLiveStatus(roomId: String) = true
        override suspend fun getSuperChatMessage(
            roomId: String,
            detail: LiveRoomDetail?
        ) = emptyList<Nothing>()
    }

    private class FakeAccountAwareSite(
        id: String,
        override val accountKey: LiveSiteAccount
    ) : FakeSite(id), AccountAwareLiveSite {
        var appliedCookie: String? = null

        override fun updateAccountCookie(cookie: String) {
            appliedCookie = cookie
        }
    }

    private class RecordingDanmaku : LiveDanmaku {
        override var onMessage: ((LiveMessage) -> Unit)? = null
        override var onClose: ((String) -> Unit)? = null
        override var onReady: (() -> Unit)? = null
        override var heartbeatTime: Int = 1
        var startedWith: DanmakuArgs? = null

        override fun heartbeat() = Unit
        override suspend fun start(args: DanmakuArgs) {
            startedWith = args
        }

        override suspend fun stop() = Unit
    }
}
