package com.mylive.app.ui.screen.follow

import org.junit.Assert.assertEquals
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
}
