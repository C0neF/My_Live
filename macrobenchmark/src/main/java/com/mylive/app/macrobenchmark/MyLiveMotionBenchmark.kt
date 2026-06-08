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
        device.waitForIdle()
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
        tapBottomTab(index = 2)
        tapBottomTab(index = 3)
        tapBottomTab(index = 1)
        tapBottomTab(index = 0)
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
        tapBottomTab(index = 2)
        swipePlatformChips()
        tapBottomTab(index = 0)
        tapHomeSearchPill()
        swipePlatformChips()
        tapSearchType(index = 1)
        tapSearchType(index = 0)
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
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
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

    private fun MacrobenchmarkScope.tapBottomTab(index: Int) {
        val tabWidth = device.displayWidth / 4
        val x = tabWidth * index + tabWidth / 2
        val y = (device.displayHeight * 0.935f).toInt()
        device.click(x, y)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.tapHomeSearchPill() {
        val x = (device.displayWidth * 0.58f).toInt()
        val y = (device.displayHeight * 0.055f).toInt()
        device.click(x, y)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.tapSearchType(index: Int) {
        val x = (device.displayWidth * (0.18f + index * 0.18f)).toInt()
        val y = (device.displayHeight * 0.155f).toInt()
        device.click(x, y)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.swipePlatformChips() {
        val y = (device.displayHeight * 0.12f).toInt()
        device.swipe(
            (device.displayWidth * 0.82f).toInt(),
            y,
            (device.displayWidth * 0.18f).toInt(),
            y,
            12
        )
        device.waitForIdle()
        device.swipe(
            (device.displayWidth * 0.18f).toInt(),
            y,
            (device.displayWidth * 0.82f).toInt(),
            y,
            12
        )
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "com.mylive.app"
        const val EXTRA_INITIAL_ROUTE = "com.mylive.app.extra.INITIAL_ROUTE"
        const val LIVE_ROOM_BENCHMARK_ROUTE = "/room/detail/6?siteId=bilibili"
        const val WAIT_TIMEOUT_MS = 5_000L
    }
}
