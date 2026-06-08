package com.mylive.app.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Navigator that manages the back stack for Navigation 3.
 *
 * Uses [SnapshotStateList] which survives recomposition but not process death.
 * For process death recovery, Navigation 3's [NavDisplay] handles state restoration
 * when routes are [kotlinx.serialization.Serializable].
 */
@Singleton
class Navigator @Inject constructor() {
    val backStack: SnapshotStateList<NavKey> = mutableStateListOf(Route.Index)

    /**
     * Remove the top entry from the back stack.
     *
     * NOTE: Do NOT use [MutableList.removeLast] here. Compiling against compileSdk 35+
     * binds `removeLast()` to the API-35 `SequencedCollection.removeLast()` member, which
     * does not exist on Android 8–14 (minSdk 26) and crashes with NoSuchMethodError at
     * runtime. [removeAt] maps to `List.remove(int)`, available on all API levels.
     */
    private fun popTop() {
        backStack.removeAt(backStack.lastIndex)
    }

    fun navigate(
        route: NavKey,
        singleTop: Boolean = false,
        popUpToRoute: Class<out Route>? = null,
        inclusive: Boolean = false
    ) {
        if (route is Route.Index) {
            backStack.clear()
            backStack.add(Route.Index)
            return
        }

        // Handle popUpTo option
        if (popUpToRoute != null) {
            val index = backStack.indexOfLast { popUpToRoute.isInstance(it) }
            if (index != -1) {
                val dropCount = backStack.size - if (inclusive) index else (index + 1)
                repeat(dropCount.coerceAtLeast(0)) {
                    if (backStack.size > 1) {
                        popTop()
                    }
                }
            }
        }

        // Handle singleTop option
        if (singleTop && backStack.lastOrNull()?.javaClass == route.javaClass) {
            // Replace the top element
            popTop()
            backStack.add(route)
        } else {
            backStack.add(route)
        }
    }

    fun goBack(): Boolean {
        if (backStack.size > 1) {
            popTop()
            return true
        }
        return false
    }
}
