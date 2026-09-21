package com.mylive.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {

    @Test
    fun repeatedBackFromOutgoingRouteCannotPopTheRouteUnderneath() {
        val navigator = Navigator()
        navigator.navigate(Route.Settings)
        navigator.navigate(Route.SettingsDanmu)

        assertTrue(navigator.goBack(expectedTop = Route.SettingsDanmu))
        assertFalse(navigator.goBack(expectedTop = Route.SettingsDanmu))
        assertEquals(listOf(Route.Index, Route.Settings), navigator.backStack.toList())
    }

    @Test
    fun stalePageCannotNavigateAfterItHasLeftTheTop() {
        val navigator = Navigator()
        navigator.navigate(Route.Settings)
        navigator.navigate(Route.SettingsDanmu)

        assertFalse(
            navigator.navigateFrom(
                source = Route.Settings,
                route = Route.SettingsPlay
            )
        )
        assertEquals(Route.SettingsDanmu, navigator.backStack.last())
    }

    @Test
    fun exitHandlerRunsOnlyForTheRouteThatActuallyPops() {
        val navigator = Navigator()
        navigator.navigate(Route.Settings)
        var exitCount = 0
        val unregister = navigator.registerRouteExitHandler(Route.Settings) {
            exitCount += 1
        }

        assertFalse(navigator.goBack(expectedTop = Route.SettingsDanmu))
        assertEquals(0, exitCount)
        assertTrue(navigator.goBack(expectedTop = Route.Settings))
        assertEquals(1, exitCount)

        unregister()
    }

    @Test
    fun navigatingToIndexKeepsASingleRootEntry() {
        val navigator = Navigator()

        navigator.navigate(Route.Index)
        navigator.navigate(Route.Settings)
        navigator.navigate(Route.Index)

        assertEquals(listOf(Route.Index), navigator.backStack.toList())
    }
}
