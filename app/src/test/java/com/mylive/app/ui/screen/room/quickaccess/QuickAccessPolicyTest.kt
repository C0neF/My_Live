package com.mylive.app.ui.screen.room.quickaccess

import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.local.entity.HistoryEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class QuickAccessPolicyTest {
    @Test fun tabOrderDropsUnknownKeysAndPreservesEverySupportedTabExactlyOnce() {
        assertEquals(listOf("history", "follow", "recommendation", "room_settings"),
            orderedQuickAccessTabs(" history,unknown,follow,history, ", listOf("room_settings", "room_settings")))
    }

    @Test fun currentRoomComparisonHandlesAliasesWithoutCrossingPlatforms() {
        assertTrue(isCurrentQuickAccessRoom("bilibili", "999", "bilibili", "1", "999"))
        assertTrue(isCurrentQuickAccessRoom("bilibili", "1", "bilibili", "1", "999"))
        assertFalse(isCurrentQuickAccessRoom("douyin", "999", "bilibili", "1", "999"))
    }

    @Test fun searchCombinesRemarksPlatformStatusAndCanonicalGroupMembership() {
        val a = follow("1", "bilibili", "Alpha", 1).copy(tag = "stale name")
        val b = follow("2", "douyin", "Alpha", 2)
        val tags = listOf(FollowUserTagEntity("group", "favorites", listOf(a.id)))
        val filters = QuickAccessFilters(query = "老师 B站", siteId = "bilibili", status = QuickAccessStatus.Live, tagId = "group")
        assertEquals(listOf(a), filterQuickAccessFollows(listOf(a, b), tags,
            mapOf("bilibili::Alpha" to "老师 B站"), filters))
        assertTrue(filterQuickAccessFollows(listOf(a, b), tags, emptyMap(), filters).isEmpty())
    }

    @Test fun liveOfflineAndUnknownRemainDistinctAndSpecialFollowsKeepPriority() {
        val offline = follow("1", "bilibili", "offline", 2)
        val live = follow("2", "bilibili", "live", 1)
        val unknown = follow("3", "bilibili", "unknown", 0)
        val pinned = follow("4", "bilibili", "pinned", 2).copy(isSpecialFollow = true)
        val input = listOf(offline, unknown, live, pinned)
        assertEquals(listOf(pinned, live, unknown, offline), filterQuickAccessFollows(input, emptyList(), emptyMap(), QuickAccessFilters()))
        assertEquals(listOf(unknown), filterQuickAccessFollows(input, emptyList(), emptyMap(), QuickAccessFilters(status = QuickAccessStatus.Unknown)))
        assertEquals(listOf(pinned, offline), filterQuickAccessFollows(input, emptyList(), emptyMap(), QuickAccessFilters(status = QuickAccessStatus.Offline)))
    }

    @Test fun historyUsesLatestDuplicateAndCanSearchByRoomId() {
        val older = HistoryEntity("old", "123", "huya", "name", "", 10)
        val latest = older.copy(id = "new", updateTime = 20)
        assertEquals(listOf(latest), filterQuickAccessHistory(listOf(older, latest), emptyMap(), QuickAccessFilters(query = "123")))
    }

    @Test fun historyDaysFollowLocalMidnightAndFutureClockSkewDoesNotShowNegativeTime() {
        val zone = ZoneId.of("Asia/Singapore")
        val today = LocalDate.of(2026, 9, 8)
        val justBeforeMidnight = today.atStartOfDay(zone).toInstant().toEpochMilli() - 1
        assertEquals("昨天", quickAccessHistoryDay(justBeforeMidnight, today, zone))
        assertEquals("今天", quickAccessHistoryDay(justBeforeMidnight + 1, today, zone))
        assertEquals("刚刚看过", formatQuickAccessRelativeTime(200, 100))
    }

    @Test fun shortLandscapePanelNeverExceedsAvailableHeight() {
        for (height in listOf(0, 200, 300, 400, 800)) {
            assertTrue(resolveQuickAccessPanelHeightDp(height) <= (height - 80).coerceAtLeast(0))
        }
    }

    private fun follow(id: String, site: String, name: String, status: Int) =
        FollowUserEntity("${site}_$id", id, site, name, "", 0, liveStatus = status)
}
