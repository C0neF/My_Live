package com.mylive.app.ui.motion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MotionRegressionTest {

    @Test
    fun liveRoomRouteDoesNotOverrideSharedCupertinoTransition() {
        val source = readMainSource("com/mylive/app/ui/navigation/AppNavGraph.kt")

        assertFalse(source.contains("custom 10% slide"))
        assertFalse(source.contains("slideInHorizontally(animationSpec = tween(400)) { width -> width / 10 }"))
        assertFalse(source.contains("slideOutHorizontally(animationSpec = tween(400)) { width -> width / 10 }"))
    }

    @Test
    fun cupertinoTransitionHasNoLiveRoomSpecialCase() {
        val source = readMainSource("com/mylive/app/ui/navigation/CupertinoTransition.kt")

        assertFalse(source.contains("startsWith(Route.LIVE_ROOM_DETAIL)"))
        assertFalse(source.contains("fadeOut(animationSpec = tween(DURATION))"))
        assertFalse(source.contains("fadeIn(animationSpec = tween(DURATION))"))
    }

    @Test
    fun indexRouteUsesFullSizeTransparentNavDisplayPlaceholder() {
        val source = readMainSource("com/mylive/app/ui/navigation/AppNavGraph.kt")
        val indexEntryStart = source.indexOf("entry<Route.Index>")
        val indexEntryEnd = source.indexOf("entry<Route.CategoryDetail>", indexEntryStart)
        val indexEntrySource = source.substring(indexEntryStart, indexEntryEnd)

        assert(indexEntrySource.contains("Box(modifier = Modifier.fillMaxSize())"))
    }

    @Test
    fun appNavGraphLetsLiveRoomUseSharedMyLiveTransition() {
        val source = readMainSource("com/mylive/app/ui/navigation/AppNavGraph.kt")
        val liveRoomStart = source.indexOf("entry<Route.LiveRoomDetail>")
        val liveRoomEnd = source.indexOf("entry<Route.History>", liveRoomStart)
        val liveRoomRoute = source.substring(liveRoomStart, liveRoomEnd)

        assertTrue(liveRoomRoute.contains("entry<Route.LiveRoomDetail>"))
        assertFalse(liveRoomRoute.contains("LiveRoomTransition"))
        assertFalse(liveRoomRoute.contains("enterTransition ="))
        assertFalse(liveRoomRoute.contains("exitTransition ="))
        assertFalse(liveRoomRoute.contains("popEnterTransition ="))
        assertFalse(liveRoomRoute.contains("popExitTransition ="))
    }

    @Test
    fun liveRoomHasNoRouteTransitionSpecialCaseFile() {
        val candidates = listOf(
            File("src/main/java/com/mylive/app/ui/navigation/LiveRoomTransition.kt"),
            File("My_Live/app/src/main/java/com/mylive/app/ui/navigation/LiveRoomTransition.kt")
        )

        assertFalse(candidates.any { it.isFile })
    }

    @Test
    fun liveRoomStartupIsNotDelayedByRouteAnimation() {
        val source = readMainSource("com/mylive/app/ui/screen/room/LiveRoomScreen.kt")

        assertFalse(source.contains("delay(700)"))
        assertFalse(source.contains("isTransitionFinished"))
        assertFalse(source.contains("uiStateRaw"))
        assertFalse(source.contains("danmakuMessagesRaw"))
    }

    @Test
    fun liveRoomBackKeepsPlayerStableUntilRoutePop() {
        val source = readMainSource("com/mylive/app/ui/screen/room/LiveRoomScreen.kt")
        val handleBackStart = source.indexOf("val handleBack")
        val handleBackEnd = source.indexOf("LaunchedEffect(roomAutoExitDuration", handleBackStart)
        val handleBackSource = source.substring(handleBackStart, handleBackEnd)

        assertTrue(handleBackSource.contains("navigator.goBack()"))
        assertFalse(handleBackSource.contains("playerController?.pause()\n                navigator.goBack()"))
        assertFalse(handleBackSource.contains("playerController?.pause()\r\n                navigator.goBack()"))
    }

    @Test
    fun liveRoomInteractionTimeDoesNotDriveTopLevelRecomposition() {
        val source = readMainSource("com/mylive/app/ui/screen/room/LiveRoomScreen.kt")

        assertFalse(source.contains("mutableLongStateOf(System.currentTimeMillis())"))
        assertFalse(source.contains("LaunchedEffect(lastInteractionTime"))
        assertTrue(source.contains("AtomicLong(System.currentTimeMillis())"))
    }

    @Test
    fun liveRoomFullscreenUsesWindowInsetsInsteadOfDeprecatedFlags() {
        val source = readMainSource("com/mylive/app/ui/screen/room/LiveRoomScreen.kt")

        assertFalse(source.contains("FLAG_FULLSCREEN"))
        assertTrue(source.contains("WindowCompat.setDecorFitsSystemWindows"))
    }

    @Test
    fun mainBottomNavContentUsesDirectAnimatedTransition() {
        val source = readMainSource("com/mylive/app/ui/screen/IndexScreen.kt")

        assertTrue(source.contains("AnimatedContent("))
        assertTrue(source.contains("adaptiveNavigationContentTransform("))
        assertTrue(source.contains("useSideNavigation = useSideNavigation"))
        assertTrue(source.contains("label = \"bottomNavContent\""))
        assertFalse(source.contains("HorizontalPager("))
        assertFalse(source.contains("animateScrollToPage("))
        assertFalse(source.contains("alpha = (1f - kotlin.math.abs(pageOffset))"))
        assertFalse(source.contains("translationX = pageOffset * size.width * 0.1f"))
        assertFalse(source.contains("beyondViewportPageCount = bottomNavItems.lastIndex"))
    }

    @Test
    fun mainSideNavigationUsesVerticalPageTransition() {
        val motionSource = readMainSource("com/mylive/app/ui/motion/AppMotion.kt")

        assertTrue(motionSource.contains("fun <S> AnimatedContentTransitionScope<S>.adaptiveNavigationContentTransform("))
        assertTrue(motionSource.contains("if (useSideNavigation)"))
        assertTrue(motionSource.contains("verticalContentTransform(direction)"))
        assertTrue(motionSource.contains("horizontalContentTransform(direction)"))
        assertTrue(motionSource.contains("AnimatedContentTransitionScope.SlideDirection.Up"))
        assertTrue(motionSource.contains("AnimatedContentTransitionScope.SlideDirection.Down"))
    }

    @Test
    fun mainPagerTabClickDoesNotPreJumpBeforeAnimating() {
        val source = readMainSource("com/mylive/app/ui/screen/IndexScreen.kt")

        assertFalse(source.contains("AppMotion.preJumpPageForTarget"))
        assertFalse(source.contains("scrollToPage(preJumpPage"))
    }

    @Test
    fun routedScreensDoNotGateEntryBehindFixedDelays() {
        val routedScreens = listOf(
            "com/mylive/app/ui/screen/category/CategoryDetailScreen.kt",
            "com/mylive/app/ui/screen/other/HistoryScreen.kt",
            "com/mylive/app/ui/screen/settings/AccountScreen.kt",
            "com/mylive/app/ui/screen/settings/SettingsScreen.kt",
            "com/mylive/app/ui/screen/sync/SyncHubScreen.kt"
        )

        val offenders = routedScreens.flatMap { path ->
            val source = readMainSource(path)
            listOfNotNull(
                path.takeIf { source.contains("isTransitionFinished") },
                path.takeIf { source.contains("delay(500)") },
                path.takeIf { source.contains("kotlinx.coroutines.delay(500)") }
            )
        }.distinct()

        assertFalse(offenders.joinToString(prefix = "Entry delay gates remain in: "), offenders.isNotEmpty())
    }

    private fun readMainSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java", relativePath),
            File("My_Live/app/src/main/java", relativePath)
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Cannot find source file for $relativePath")
        return file.readText()
    }
}
