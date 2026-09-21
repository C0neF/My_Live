package com.mylive.app.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class NavigationStateRestoreTest {
    @Test fun newProcessRestoresFullBackStackAndArguments() {
        val original = Navigator()
        original.navigate(Route.CategoryDetail("douyin", "a", "分类 A"))
        original.navigate(Route.LiveRoomDetail("123", "douyin", true))
        val restored = Navigator().apply { restoreState(original.saveState()) }
        assertEquals(original.backStack.toList(), restored.backStack.toList())
        assertTrue(restored.goBack(Route.LiveRoomDetail("123", "douyin", true)))
        assertEquals(Route.CategoryDetail("douyin", "a", "分类 A"), restored.backStack.last())
    }

    @Test fun invalidOrMissingStateStartsAtIndex() {
        val navigator = Navigator()
        for (state in listOf(null, "broken", "[]", "[{\"type\":\"obsolete\"}]")) {
            navigator.navigate(Route.Search)
            navigator.restoreState(state)
            assertEquals(listOf(Route.Index), navigator.backStack)
        }
    }
}
