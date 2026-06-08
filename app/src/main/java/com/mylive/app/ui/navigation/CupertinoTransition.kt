package com.mylive.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene

/**
 * Horizontal slide page transitions for Navigation 3.
 *
 * - Forward: new page slides in from right, old page stays completely still.
 * - Back: current page slides out to right, page underneath stays completely still.
 * No fade, no crossfade, no opacity changes on the underlying page.
 */
object CupertinoTransition {

    private const val DURATION = 300

    /**
     * Combined forward transition spec.
     * New content slides in from right, old content does nothing.
     */
    val transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(DURATION)
        ) togetherWith ExitTransition.None
    }

    /**
     * Combined back transition spec.
     * New content (underneath) does nothing, old content slides out to right.
     */
    val popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        EnterTransition.None togetherWith slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(DURATION)
        )
    }
}
