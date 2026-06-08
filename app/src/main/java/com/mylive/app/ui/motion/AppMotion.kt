package com.mylive.app.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import kotlin.math.abs

object AppMotion {
    const val RouteDurationMillis = 300
    const val RoutePopDurationMillis = 300
    const val ContentDurationMillis = 220
    const val FarPagerDurationMillis = 260
    const val NavItemDurationMillis = 180
    const val RouteParallaxFactor = 1f / 3f
    const val LiveRoomEnterDurationMillis = RouteDurationMillis
    const val LiveRoomExitDurationMillis = RoutePopDurationMillis
    const val LiveRoomDataStartupDelayMillis = 320
    const val LiveRoomPlayerStartupDelayMillis = 360
    const val LiveRoomParallaxFactor = RouteParallaxFactor

    val FlutterGetxRouteEasing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
    val FlutterCupertinoLinearToEaseOutEasing = CubicBezierEasing(0.35f, 0.91f, 0.33f, 0.97f)
    val FlutterCupertinoEaseInToLinearEasing = CubicBezierEasing(0.67f, 0.03f, 0.65f, 0.09f)
    private val FlutterCupertinoPrimaryEasing = threePointCubicEasing(
        a1x = 0.056f,
        a1y = 0.024f,
        b1x = 0.108f,
        b1y = 0.3085f,
        midpointX = 0.198f,
        midpointY = 0.541f,
        a2x = 0.3655f,
        a2y = 1.0f,
        b2x = 0.5465f,
        b2y = 0.989f
    )
    private val FlutterCupertinoPrimaryReverseEasing = flipped(FlutterCupertinoPrimaryEasing)
    val MyLivePrimaryForwardEasing = composeEasing(
        parent = FlutterGetxRouteEasing,
        child = FlutterCupertinoPrimaryEasing
    )
    val MyLivePrimaryPopEasing = reverseProgressEasing(
        parent = FlutterGetxRouteEasing,
        reverse = FlutterCupertinoPrimaryReverseEasing
    )
    val MyLiveSecondaryForwardEasing = FlutterCupertinoLinearToEaseOutEasing
    val MyLiveSecondaryPopEasing = reverseProgressEasing(
        parent = Easing { it },
        reverse = FlutterCupertinoEaseInToLinearEasing
    )
    val RouteForwardEasing = MyLivePrimaryForwardEasing
    val RoutePopEasing = MyLivePrimaryPopEasing
    val ContentEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val LiveRoomEnterEasing = MyLivePrimaryForwardEasing
    val LiveRoomExitEasing = MyLivePrimaryPopEasing

    fun pagerDurationMillis(pageDistance: Int): Int {
        return if (abs(pageDistance) <= 1) ContentDurationMillis else FarPagerDurationMillis
    }

    fun indexDirection(fromIndex: Int, toIndex: Int): Int {
        return toIndex.compareTo(fromIndex)
    }

    fun preJumpPageForTarget(currentPage: Int, targetPage: Int): Int? {
        val direction = indexDirection(currentPage, targetPage)
        if (direction == 0 || abs(targetPage - currentPage) <= 1) return null
        return targetPage - direction
    }

    fun routeParallaxOffset(containerWidth: Int): Int {
        return (containerWidth * RouteParallaxFactor).toInt()
    }

    fun liveRoomParallaxOffset(containerWidth: Int): Int {
        return (containerWidth * LiveRoomParallaxFactor).toInt()
    }

    fun <T> routeForwardSpec(): TweenSpec<T> {
        return tween(durationMillis = RouteDurationMillis, easing = RouteForwardEasing)
    }

    fun <T> routePopSpec(): TweenSpec<T> {
        return tween(durationMillis = RoutePopDurationMillis, easing = RoutePopEasing)
    }

    fun <T> routeSecondaryForwardSpec(): TweenSpec<T> {
        return tween(durationMillis = RouteDurationMillis, easing = MyLiveSecondaryForwardEasing)
    }

    fun <T> routeSecondaryPopSpec(): TweenSpec<T> {
        return tween(durationMillis = RoutePopDurationMillis, easing = MyLiveSecondaryPopEasing)
    }

    fun <T> liveRoomEnterSpec(): TweenSpec<T> {
        return tween(durationMillis = LiveRoomEnterDurationMillis, easing = LiveRoomEnterEasing)
    }

    fun <T> liveRoomExitSpec(): TweenSpec<T> {
        return tween(durationMillis = LiveRoomExitDurationMillis, easing = LiveRoomExitEasing)
    }

    fun pagerSpec(pageDistance: Int): TweenSpec<Float> {
        return tween(durationMillis = pagerDurationMillis(pageDistance), easing = ContentEasing)
    }

    fun <T> contentSpec(): TweenSpec<T> {
        return tween(durationMillis = ContentDurationMillis, easing = ContentEasing)
    }

    private fun composeEasing(parent: Easing, child: Easing): Easing {
        return Easing { fraction ->
            child.transform(parent.transform(fraction))
        }
    }

    private fun flipped(easing: Easing): Easing {
        return Easing { fraction ->
            1f - easing.transform(1f - fraction)
        }
    }

    private fun reverseProgressEasing(parent: Easing, reverse: Easing): Easing {
        return Easing { fraction ->
            1f - reverse.transform(parent.transform(1f - fraction))
        }
    }

    private fun threePointCubicEasing(
        a1x: Float,
        a1y: Float,
        b1x: Float,
        b1y: Float,
        midpointX: Float,
        midpointY: Float,
        a2x: Float,
        a2y: Float,
        b2x: Float,
        b2y: Float
    ): Easing {
        return Easing { fraction ->
            val firstCurve = fraction < midpointX
            val scaleX = if (firstCurve) midpointX else 1f - midpointX
            val scaleY = if (firstCurve) midpointY else 1f - midpointY
            val scaledFraction = ((fraction - if (firstCurve) 0f else midpointX) / scaleX)
                .coerceIn(0f, 1f)

            if (firstCurve) {
                CubicBezierEasing(
                    a1x / scaleX,
                    a1y / scaleY,
                    b1x / scaleX,
                    b1y / scaleY
                ).transform(scaledFraction) * scaleY
            } else {
                CubicBezierEasing(
                    (a2x - midpointX) / scaleX,
                    (a2y - midpointY) / scaleY,
                    (b2x - midpointX) / scaleX,
                    (b2y - midpointY) / scaleY
                ).transform(scaledFraction) * scaleY + midpointY
            }
        }
    }
}

fun <S> AnimatedContentTransitionScope<S>.horizontalContentTransform(
    direction: Int
): ContentTransform {
    if (direction == 0) {
        return fadeIn(animationSpec = AppMotion.contentSpec()) togetherWith
            fadeOut(animationSpec = AppMotion.contentSpec())
    }

    val slideDirection = if (direction > 0) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }

    return ((slideIntoContainer(
        towards = slideDirection,
        animationSpec = AppMotion.contentSpec()
    ) + fadeIn(animationSpec = AppMotion.contentSpec())) togetherWith
        (slideOutOfContainer(
            towards = slideDirection,
            animationSpec = AppMotion.contentSpec()
        ) + fadeOut(animationSpec = AppMotion.contentSpec()))).using(SizeTransform(clip = false))
}
