package com.mylive.app.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyLiveMotionBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupFrames() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 8,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait()
        waitForTag(INDEX_HOME_SCREEN_TAG)
    }

    @Test
    fun bottomTabSwitchFrames() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForAppForeground()
        }
    ) {
        ensureAppForeground()
        tapTagAndWait(NAV_CATEGORY_TAG, INDEX_CATEGORY_SCREEN_TAG)
        tapTagAndWait(NAV_MINE_TAG, INDEX_MINE_SCREEN_TAG)
        tapTagAndWait(NAV_FOLLOW_TAG, INDEX_FOLLOW_SCREEN_TAG)
        tapTagAndWait(NAV_HOME_TAG, INDEX_HOME_SCREEN_TAG)
    }

    @Test
    fun searchAndCategorySwitchFrames() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 8,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForAppForeground()
        }
    ) {
        ensureAppForeground()
        tapTagAndWait(NAV_CATEGORY_TAG, INDEX_CATEGORY_SCREEN_TAG)
        selectPlatform("category", "bilibili")
        selectPlatform("category", "douyu")
        tapTagAndWait(NAV_HOME_TAG, INDEX_HOME_SCREEN_TAG)
        selectPlatform("home", "bilibili")
        selectPlatform("home", "douyu")
        tapTagAndWait(HOME_SEARCH_TAG, SEARCH_SCREEN_TAG)
        selectPlatform("search", "bilibili")
        selectPlatform("search", "douyu")
        selectSearchType(currentType = 0, targetType = 1)
        selectSearchType(currentType = 1, targetType = 0)
        device.pressBack()
        waitForTag(INDEX_HOME_SCREEN_TAG)
    }

    @Test
    fun liveRoomEnterExitFrames() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 8,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait(liveRoomBenchmarkIntent())
        waitForAppForeground()
        waitForTag(LIVE_ROOM_SCREEN_TAG)
        device.pressBack()
        waitForTag(INDEX_HOME_SCREEN_TAG)
    }

    private fun liveRoomBenchmarkIntent(): Intent {
        val packageManager = InstrumentationRegistry.getInstrumentation().context.packageManager
        val launcherIntent = packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            ?: Intent(Intent.ACTION_MAIN).apply {
                setPackage(PACKAGE_NAME)
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

        return launcherIntent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_INITIAL_ROUTE, LIVE_ROOM_BENCHMARK_ROUTE)
        }
    }

    private fun MacrobenchmarkScope.ensureAppForeground() {
        if (device.currentPackageName != PACKAGE_NAME) {
            startActivityAndWait()
        }
        waitForAppForeground()
    }

    private fun MacrobenchmarkScope.waitForAppForeground() {
        val appVisible = device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), WAIT_TIMEOUT_MS)
        check(appVisible) { "Could not bring $PACKAGE_NAME to foreground; current=${device.currentPackageName}" }
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.tapTag(tag: String) {
        val target = device.wait(Until.findObject(By.res(tag)), WAIT_TIMEOUT_MS)
        checkNotNull(target) { "Could not find semantic target $tag" }
        target.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.tapTagAndWait(tag: String, expectedTag: String) {
        tapTag(tag)
        waitForTag(expectedTag)
    }

    private fun MacrobenchmarkScope.waitForTag(tag: String) {
        val visible = device.wait(Until.hasObject(By.res(tag)), WAIT_TIMEOUT_MS)
        check(visible) { "Timed out waiting for semantic target $tag" }
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.waitForSelectedTag(tag: String) {
        val selected = device.wait(
            Until.hasObject(By.res(tag).selected(true)),
            WAIT_TIMEOUT_MS
        )
        check(selected) { "Timed out waiting for selected semantic target $tag" }
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.selectPlatform(screen: String, platformId: String) {
        val tag = "${screen}_platform_$platformId"
        tapTag(tag)
        waitForSelectedTag(tag)
    }

    private fun MacrobenchmarkScope.selectSearchType(currentType: Int, targetType: Int) {
        tapTag("search_type_menu_$currentType")
        tapTag("search_type_option_$targetType")
        waitForTag("search_type_menu_$targetType")
    }

    private companion object {
        const val PACKAGE_NAME = "com.mylive.app"
        const val EXTRA_INITIAL_ROUTE = "com.mylive.app.extra.INITIAL_ROUTE"
        const val LIVE_ROOM_BENCHMARK_ROUTE = "/room/detail/6?siteId=bilibili"
        const val WAIT_TIMEOUT_MS = 5_000L

        const val NAV_HOME_TAG = "index_nav_recommend"
        const val NAV_FOLLOW_TAG = "index_nav_follow"
        const val NAV_CATEGORY_TAG = "index_nav_category"
        const val NAV_MINE_TAG = "index_nav_user"
        const val INDEX_HOME_SCREEN_TAG = "index_screen_recommend"
        const val INDEX_FOLLOW_SCREEN_TAG = "index_screen_follow"
        const val INDEX_CATEGORY_SCREEN_TAG = "index_screen_category"
        const val INDEX_MINE_SCREEN_TAG = "index_screen_user"
        const val HOME_SEARCH_TAG = "home_search"
        const val SEARCH_SCREEN_TAG = "screen_search"
        const val LIVE_ROOM_SCREEN_TAG = "screen_live_room"
    }
}
