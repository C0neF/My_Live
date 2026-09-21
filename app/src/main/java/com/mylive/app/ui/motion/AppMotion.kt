package com.mylive.app.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import kotlin.math.abs

/**
 * Physics tokens shared by navigation, pagers, and gesture settling.
 *
 * Springs have no artificial completion timer: a new target starts from the current presentation
 * value and Compose carries its live velocity across the interruption.
 */
object AppMotion {
    const val RouteSpringStiffness = 380f
    const val ContentSpringStiffness = 520f
    const val MomentumSpringStiffness = 450f
    const val ReducedMotionSpringStiffness = 1_600f
    const val DefaultDampingRatio = 1f
    const val MomentumDampingRatio = 0.82f
    const val MomentumProjectionRate = 0.99f

    fun indexDirection(fromIndex: Int, toIndex: Int): Int {
        return toIndex.compareTo(fromIndex)
    }

    fun preJumpPageForTarget(currentPage: Int, targetPage: Int): Int? {
        val direction = indexDirection(currentPage, targetPage)
        if (direction == 0 || abs(targetPage - currentPage) <= 1) return null
        return targetPage - direction
    }

    fun <T> routeSpec(reducedMotion: Boolean = false): SpringSpec<T> {
        return spring(
            dampingRatio = DefaultDampingRatio,
            stiffness = if (reducedMotion) {
                ReducedMotionSpringStiffness
            } else {
                RouteSpringStiffness
            }
        )
    }

    fun <T> contentSpec(reducedMotion: Boolean = false): SpringSpec<T> {
        return spring(
            dampingRatio = DefaultDampingRatio,
            stiffness = if (reducedMotion) {
                ReducedMotionSpringStiffness
            } else {
                ContentSpringStiffness
            }
        )
    }

    fun pagerSpec(): SpringSpec<Float> {
        return spring(
            dampingRatio = DefaultDampingRatio,
            stiffness = ContentSpringStiffness
        )
    }

    fun momentumSpec(): SpringSpec<Float> {
        return spring(
            dampingRatio = MomentumDampingRatio,
            stiffness = MomentumSpringStiffness
        )
    }

    /**
     * Exponential velocity projection used before choosing a drag's resting target.
     */
    fun projectedEndpoint(
        currentValue: Float,
        velocity: Float,
        decelerationRate: Float = MomentumProjectionRate
    ): Float {
        if (!currentValue.isFinite() || !velocity.isFinite()) return currentValue
        val rate = decelerationRate.coerceIn(0f, 0.999f)
        return currentValue + (velocity / 1_000f) * rate / (1f - rate)
    }
}

fun <S> AnimatedContentTransitionScope<S>.horizontalContentTransform(
    direction: Int,
    motionPolicy: AppMotionPolicy
): ContentTransform {
    if (direction == 0 || motionPolicy.reducedMotion) {
        return fadeIn(animationSpec = motionPolicy.effectsSpec()) togetherWith
            fadeOut(animationSpec = motionPolicy.effectsSpec())
    }

    val slideDirection = if (direction > 0) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }

    return ((slideIntoContainer(
        towards = slideDirection,
        animationSpec = motionPolicy.contentSpec()
    ) + fadeIn(animationSpec = motionPolicy.effectsSpec())) togetherWith
        (slideOutOfContainer(
            towards = slideDirection,
            animationSpec = motionPolicy.contentSpec()
        ) + fadeOut(animationSpec = motionPolicy.effectsSpec()))).using(
        SizeTransform(clip = false)
    )
}

fun <S> AnimatedContentTransitionScope<S>.verticalContentTransform(
    direction: Int,
    motionPolicy: AppMotionPolicy
): ContentTransform {
    if (direction == 0 || motionPolicy.reducedMotion) {
        return fadeIn(animationSpec = motionPolicy.effectsSpec()) togetherWith
            fadeOut(animationSpec = motionPolicy.effectsSpec())
    }

    val slideDirection = if (direction > 0) {
        AnimatedContentTransitionScope.SlideDirection.Up
    } else {
        AnimatedContentTransitionScope.SlideDirection.Down
    }

    return ((slideIntoContainer(
        towards = slideDirection,
        animationSpec = motionPolicy.contentSpec()
    ) + fadeIn(animationSpec = motionPolicy.effectsSpec())) togetherWith
        (slideOutOfContainer(
            towards = slideDirection,
            animationSpec = motionPolicy.contentSpec()
        ) + fadeOut(animationSpec = motionPolicy.effectsSpec()))).using(
        SizeTransform(clip = false)
    )
}

fun <S> AnimatedContentTransitionScope<S>.adaptiveNavigationContentTransform(
    direction: Int,
    useSideNavigation: Boolean,
    motionPolicy: AppMotionPolicy
): ContentTransform {
    return if (useSideNavigation) {
        verticalContentTransform(direction, motionPolicy)
    } else {
        horizontalContentTransform(direction, motionPolicy)
    }
}
