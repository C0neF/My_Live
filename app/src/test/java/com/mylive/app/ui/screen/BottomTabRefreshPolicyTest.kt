package com.mylive.app.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomTabRefreshPolicyTest {

    @Test
    fun repeatsOnRefreshableTabsTriggerRefresh() {
        assertTrue(shouldRefreshBottomTab(currentKey = "recommend", clickedKey = "recommend"))
        assertTrue(shouldRefreshBottomTab(currentKey = "follow", clickedKey = "follow"))
        assertTrue(shouldRefreshBottomTab(currentKey = "category", clickedKey = "category"))
    }

    @Test
    fun switchingTabsDoesNotTriggerRefresh() {
        assertFalse(shouldRefreshBottomTab(currentKey = "recommend", clickedKey = "follow"))
        assertFalse(shouldRefreshBottomTab(currentKey = "follow", clickedKey = "category"))
        assertFalse(shouldRefreshBottomTab(currentKey = "category", clickedKey = "recommend"))
    }

    @Test
    fun mineTabDoesNotRefreshOnRepeatClick() {
        assertFalse(shouldRefreshBottomTab(currentKey = "user", clickedKey = "user"))
    }
}
