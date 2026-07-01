package com.mylive.app.ui.screen.follow

import org.junit.Assert.assertEquals
import org.junit.Test

class FollowPlatformOrderTest {

    @Test
    fun platformGroupsFollowConfiguredSiteOrder() {
        assertEquals(
            listOf("douyin", "bilibili", "unknown"),
            sortFollowPlatformIds(
                siteIds = listOf("bilibili", "unknown", "douyin"),
                sortOrder = "douyin,bilibili,huya,douyu"
            )
        )
    }

    @Test
    fun unknownPlatformsKeepTheirOriginalRelativeOrder() {
        assertEquals(
            listOf("huya", "beta", "alpha"),
            sortFollowPlatformIds(
                siteIds = listOf("beta", "huya", "alpha", "beta"),
                sortOrder = "bilibili,huya"
            )
        )
    }
}
