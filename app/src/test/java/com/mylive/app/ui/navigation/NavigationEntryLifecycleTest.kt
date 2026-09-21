package com.mylive.app.ui.navigation

import android.app.Application
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class NavigationProbeViewModel : ViewModel() {
    var cleared = false
    override fun onCleared() { cleared = true }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NavigationEntryLifecycleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun destinationsHaveIndependentStoresAndPopClearsOnlyRemovedEntry() {
        val stack = mutableStateListOf<NavKey>(Route.Index)
        val models = mutableMapOf<NavKey, NavigationProbeViewModel>()
        val a = Route.CategoryDetail("bilibili", "a")
        val b = Route.CategoryDetail("bilibili", "b")
        compose.setContent {
            NavDisplay(
                backStack = stack,
                entryDecorators = rememberAppEntryDecorators(),
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                entryProvider = { key ->
                    NavEntry(key) {
                        val model: NavigationProbeViewModel = viewModel()
                        SideEffect { models[key] = model }
                        Text(key.toString())
                    }
                }
            )
        }
        compose.runOnIdle { stack.add(a) }
        compose.waitForIdle()
        val modelA = models.getValue(a)
        compose.runOnIdle { stack.add(b) }
        compose.waitForIdle()
        val modelB = models.getValue(b)
        assertNotSame(modelA, modelB)
        compose.runOnIdle { stack.removeAt(stack.lastIndex) }
        compose.waitForIdle()
        assertTrue(modelB.cleared)
        assertFalse(modelA.cleared)
        compose.runOnIdle { stack.removeAt(stack.lastIndex) }
        compose.waitForIdle()
        assertTrue(modelA.cleared)
        compose.runOnIdle { stack.add(a) }
        compose.waitForIdle()
        assertNotSame(modelA, models.getValue(a))
    }
}
