package com.mylive.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import dagger.hilt.android.scopes.ActivityRetainedScoped

/**
 * Navigator that manages the back stack for Navigation 3.
 *
 * Uses a snapshot-aware list internally and exposes it read-only so all removals pass through
 * route-exit dispatch. MainActivity saves and restores the serialized routes through its
 * saved-instance state; entry decorators own each page's ViewModels and saveable UI state.
 */
@ActivityRetainedScoped
class Navigator @Inject constructor() {
    private val mutableBackStack = mutableStateListOf<NavKey>(Route.Index)
    val backStack: List<NavKey>
        get() = mutableBackStack
    private val routeExitHandlers = mutableMapOf<NavKey, MutableMap<Any, () -> Unit>>()

    fun saveState(): String = Json.encodeToString(backStack.filterIsInstance<Route>())

    fun restoreState(savedState: String?) {
        val restored = savedState?.let {
            runCatching { Json.decodeFromString<List<Route>>(it) }.getOrNull()
        }.orEmpty()
        routeExitHandlers.clear()
        mutableBackStack.clear()
        mutableBackStack.add(Route.Index)
        mutableBackStack.addAll(restored.filter { it != Route.Index })
    }

    /**
     * Remove the top entry from the back stack.
     *
     * NOTE: Do NOT use [MutableList.removeLast] here. Compiling against compileSdk 35+
     * binds `removeLast()` to the API-35 `SequencedCollection.removeLast()` member, which
     * does not exist on Android 8–14 (minSdk 26) and crashes with NoSuchMethodError at
     * runtime. [removeAt] maps to `List.remove(int)`, available on all API levels.
     */
    private fun popTop() {
        dispatchRouteExit(mutableBackStack.last())
        mutableBackStack.removeAt(mutableBackStack.lastIndex)
    }

    private fun dispatchRouteExit(route: NavKey) {
        routeExitHandlers.remove(route)
            ?.values
            ?.toList()
            ?.forEach { handler -> runCatching(handler) }
    }

    fun navigate(
        route: NavKey,
        singleTop: Boolean = false,
        popUpToRoute: Class<out Route>? = null,
        inclusive: Boolean = false
    ) {
        if (route is Route.Index) {
            while (mutableBackStack.size > 1) {
                popTop()
            }
            if (mutableBackStack.singleOrNull() !is Route.Index) {
                mutableBackStack.clear()
                mutableBackStack.add(Route.Index)
            }
            return
        }

        // Handle popUpTo option
        if (popUpToRoute != null) {
            val index = mutableBackStack.indexOfLast { popUpToRoute.isInstance(it) }
            if (index != -1) {
                val dropCount = mutableBackStack.size - if (inclusive) index else (index + 1)
                repeat(dropCount.coerceAtLeast(0)) {
                    if (mutableBackStack.size > 1) {
                        popTop()
                    }
                }
            }
        }

        // Handle singleTop option
        if (singleTop && mutableBackStack.lastOrNull()?.javaClass == route.javaClass) {
            // Replace the top element
            popTop()
            mutableBackStack.add(route)
        } else {
            mutableBackStack.add(route)
        }
    }

    /**
     * Pops only when [expectedTop] is still the visible route.
     *
     * The route token makes repeated events from an outgoing page idempotent without blocking
     * input or waiting for an animation timer. A newly visible route gets its own token and can be
     * acted on immediately, even while the previous spring is still settling.
     */
    internal fun goBack(expectedTop: NavKey): Boolean {
        if (mutableBackStack.size > 1 && mutableBackStack.lastOrNull() == expectedTop) {
            popTop()
            return true
        }
        return false
    }

    internal fun navigateFrom(
        source: NavKey,
        route: NavKey,
        singleTop: Boolean = false,
        popUpToRoute: Class<out Route>? = null,
        inclusive: Boolean = false
    ): Boolean {
        if (mutableBackStack.lastOrNull() != source) return false
        navigate(
            route = route,
            singleTop = singleTop,
            popUpToRoute = popUpToRoute,
            inclusive = inclusive
        )
        return true
    }

    internal fun registerRouteExitHandler(route: NavKey, handler: () -> Unit): () -> Unit {
        val registration = Any()
        routeExitHandlers.getOrPut(route, ::linkedMapOf)[registration] = handler
        return {
            routeExitHandlers[route]?.let { handlers ->
                handlers.remove(registration)
                if (handlers.isEmpty()) {
                    routeExitHandlers.remove(route)
                }
            }
        }
    }

    /**
     * Captures the route that owns the current composition.
     *
     * The capture intentionally happens only once for that page instance. When the back stack
     * changes, an outgoing page keeps its original token instead of accidentally targeting the
     * page underneath it.
     */
    @Composable
    fun rememberRouteActions(): RouteNavigationActions {
        val source = remember(this) {
            checkNotNull(backStack.lastOrNull()) { "A route action requires a non-empty back stack" }
        }
        return remember(this, source) {
            RouteNavigationActions(navigator = this, source = source)
        }
    }
}

@Stable
class RouteNavigationActions internal constructor(
    private val navigator: Navigator,
    internal val source: NavKey
) {
    val onBack: () -> Unit = {
        navigator.goBack(expectedTop = source)
    }

    val isCurrent: Boolean
        get() = navigator.backStack.lastOrNull() == source

    fun navigate(
        route: NavKey,
        singleTop: Boolean = false,
        popUpToRoute: Class<out Route>? = null,
        inclusive: Boolean = false
    ): Boolean {
        return navigator.navigateFrom(
            source = source,
            route = route,
            singleTop = singleTop,
            popUpToRoute = popUpToRoute,
            inclusive = inclusive
        )
    }
}

/**
 * Registers route-specific cleanup without making the page own navigation timing.
 *
 * The navigator invokes [onExit] exactly when this route is removed, regardless of whether the
 * pop came from a toolbar button, system back, predictive back completion, or another nav action.
 */
@Composable
fun NavigationExitEffect(
    navigator: Navigator,
    route: NavKey,
    onExit: () -> Unit
) {
    val currentOnExit = rememberUpdatedState(onExit)
    DisposableEffect(navigator, route) {
        val unregister = navigator.registerRouteExitHandler(route) {
            currentOnExit.value()
        }
        onDispose(unregister)
    }
}
