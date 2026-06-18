package com.mylive.app.ui.screen.follow

import com.mylive.app.data.local.entity.FollowUserEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowCompactLayoutPolicyTest {

    @Test
    fun compactFilterModeLabelKeepsCurrentGroupingVisible() {
        assertEquals("状态", followCompactGroupModeLabel(FollowGroupMode.STATUS))
        assertEquals("平台", followCompactGroupModeLabel(FollowGroupMode.PLATFORM))
        assertEquals("标签", followCompactGroupModeLabel(FollowGroupMode.TAG))
    }

    @Test
    fun platformFilterOptionsAreNotHorizontallyScrollable() {
        assertEquals(false, followCompactFilterOptionsScrollable(FollowGroupMode.PLATFORM))
        assertEquals(true, followCompactFilterOptionsScrollable(FollowGroupMode.STATUS))
        assertEquals(true, followCompactFilterOptionsScrollable(FollowGroupMode.TAG))
    }

    @Test
    fun platformFilterUsesSingleLineDenseMetrics() {
        val metrics = followCompactPlatformFilterMetrics()

        assertEquals(16, metrics.horizontalPaddingDp)
        assertEquals(8, metrics.itemGapDp)
        assertEquals(6, metrics.pillHorizontalPaddingDp)
        assertEquals(12, metrics.fontSizeSp)
    }

    @Test
    fun filterModesUseCompactPlatformSelectorWidth() {
        assertEquals(64, followCompactModeSelectorWidthDp())
    }

    @Test
    fun compactCardMetricsReduceVerticalFootprint() {
        val metrics = followCompactCardMetrics()

        assertEquals(40, metrics.avatarSizeDp)
        assertEquals(8, metrics.contentPaddingDp)
        assertEquals(8, metrics.horizontalGapDp)
        assertEquals(3, metrics.cardVerticalPaddingDp)
    }

    @Test
    fun tabletFollowCardsUseTwoBalancedColumns() {
        assertEquals(1, followCardGridColumns(useSideNavigation = false))
        assertEquals(2, followCardGridColumns(useSideNavigation = true))
    }

    @Test
    fun tabletFollowLayoutUsesTwoStatusColumnsOnlyOnWideLayout() {
        assertFalse(followUseTabletTwoColumnLayout(followCardColumns = 1))
        assertTrue(followUseTabletTwoColumnLayout(followCardColumns = 2))
    }

    @Test
    fun tabletFollowLayoutSplitsLiveFromInactiveFollows() {
        val columns = followTabletStatusColumns(
            listOf(
                follow(id = "live", liveStatus = 1),
                follow(id = "unknown", liveStatus = 0),
                follow(id = "offline", liveStatus = 2)
            )
        )

        assertEquals(listOf("live"), columns.live.map { it.id })
        assertEquals(listOf("unknown", "offline"), columns.inactive.map { it.id })
    }

    @Test
    fun tabletFollowPlatformOptionsUseCountsAndStablePlatformOrder() {
        val options = followTabletPlatformOptions(
            listOf(
                follow(id = "douyin_1", siteId = "douyin"),
                follow(id = "bilibili_1", siteId = "bilibili"),
                follow(id = "douyu_1", siteId = "douyu"),
                follow(id = "huya_1", siteId = "huya"),
                follow(id = "bilibili_2", siteId = "bilibili")
            )
        )

        assertEquals(
            listOf("全部", "B站", "斗鱼", "虎牙", "抖音"),
            options.map { it.title }
        )
        assertEquals(listOf(5, 2, 1, 1, 1), options.map { it.count })
        assertEquals(listOf(null, "bilibili", "douyu", "huya", "douyin"), options.map { it.siteId })
    }

    private fun follow(
        id: String,
        siteId: String = "bilibili",
        liveStatus: Int = 2
    ): FollowUserEntity {
        return FollowUserEntity(
            id = id,
            roomId = id,
            siteId = siteId,
            userName = id,
            face = "",
            addTime = 0,
            liveStatus = liveStatus
        )
    }
}
