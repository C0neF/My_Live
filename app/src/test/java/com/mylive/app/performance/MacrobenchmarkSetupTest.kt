package com.mylive.app.performance

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class MacrobenchmarkSetupTest {

    @Test
    fun projectHasMacrobenchmarkModuleConfigured() {
        val root = findProjectRoot()

        val settings = root.resolve("settings.gradle.kts").readText()
        val rootBuild = root.resolve("build.gradle.kts").readText()
        val catalog = root.resolve("gradle/libs.versions.toml").readText()
        val appBuild = root.resolve("app/build.gradle.kts").readText()
        val appManifest = root.resolve("app/src/main/AndroidManifest.xml").readText()
        val mainActivity = root.resolve("app/src/main/java/com/mylive/app/MainActivity.kt").readText()
        val appNavGraph = root.resolve("app/src/main/java/com/mylive/app/ui/navigation/AppNavGraph.kt").readText()
        val macroBuild = root.resolve("macrobenchmark/build.gradle.kts").readText()
        val benchmarkSource = root.resolve(
            "macrobenchmark/src/main/java/com/mylive/app/macrobenchmark/MyLiveMotionBenchmark.kt"
        ).readText()

        assertTrue(settings.contains("include(\":macrobenchmark\")"))
        assertTrue(rootBuild.contains("alias(libs.plugins.android.test) apply false"))
        assertFalse(rootBuild.contains("alias(libs.plugins.androidx.benchmark) apply false"))

        assertTrue(catalog.contains("benchmark = \"1.4.1\""))
        assertTrue(catalog.contains("profileInstaller = \"1.4.1\""))
        assertTrue(catalog.contains("androidx-benchmark-macro-junit4"))
        assertTrue(catalog.contains("androidx-profileinstaller"))
        assertTrue(catalog.contains("androidx-uiautomator"))
        assertTrue(catalog.contains("android-test = { id = \"com.android.test\""))
        assertFalse(catalog.contains("androidx-benchmark = { id = \"androidx.benchmark\""))

        assertTrue(appBuild.contains("create(\"benchmark\")"))
        assertTrue(appBuild.contains("initWith(getByName(\"release\"))"))
        assertTrue(appBuild.contains("signingConfig = signingConfigs.getByName(\"debug\")"))
        assertTrue(appBuild.contains("matchingFallbacks += listOf(\"release\")"))
        assertTrue(appBuild.contains("isDebuggable = false"))
        assertTrue(appBuild.contains("isMinifyEnabled = false"))
        assertTrue(appBuild.contains("implementation(libs.androidx.profileinstaller)"))

        assertTrue(appManifest.contains("<profileable android:shell=\"true\" />"))

        assertTrue(macroBuild.contains("alias(libs.plugins.android.test)"))
        assertFalse(macroBuild.contains("alias(libs.plugins.androidx.benchmark)"))
        assertTrue(macroBuild.contains("targetProjectPath = \":app\""))
        assertTrue(macroBuild.contains("experimentalProperties[\"android.experimental.self-instrumenting\"] = true"))
        assertTrue(macroBuild.contains("create(\"benchmark\")"))
        assertTrue(macroBuild.contains("signingConfig = signingConfigs.getByName(\"debug\")"))
        assertTrue(macroBuild.contains("dependsOn(\":app:installBenchmark\")"))
        assertTrue(macroBuild.contains("implementation(libs.androidx.benchmark.macro.junit4)"))
        assertTrue(macroBuild.contains("implementation(libs.androidx.uiautomator)"))
        assertTrue(macroBuild.contains("sourceCompatibility = JavaVersion.VERSION_17"))
        assertTrue(macroBuild.contains("targetCompatibility = JavaVersion.VERSION_17"))
        assertTrue(macroBuild.contains("jvmTarget = \"17\""))

        assertTrue(benchmarkSource.contains("FrameTimingMetric()"))
        assertTrue(benchmarkSource.contains("StartupTimingMetric()"))
        assertTrue(benchmarkSource.contains("bottomTabSwitchFrames"))
        assertTrue(benchmarkSource.contains("startupFrames"))
        assertTrue(benchmarkSource.contains("searchAndCategorySwitchFrames"))
        assertTrue(benchmarkSource.contains("liveRoomEnterExitFrames"))
        assertTrue(benchmarkSource.contains("getLaunchIntentForPackage(PACKAGE_NAME)"))
        assertTrue(benchmarkSource.contains("EXTRA_INITIAL_ROUTE"))
        assertTrue(benchmarkSource.contains("LIVE_ROOM_BENCHMARK_ROUTE"))
        assertFalse(benchmarkSource.contains(".depth(0)"))

        assertTrue(mainActivity.contains("EXTRA_INITIAL_ROUTE"))
        assertTrue(mainActivity.contains("intent.getStringExtra(EXTRA_INITIAL_ROUTE)"))
        assertTrue(mainActivity.contains("AppNavGraph(navigator = navigator, initialRoute = initialRoute)"))

        assertTrue(appNavGraph.contains("initialRoute: String? = null"))
        assertTrue(appNavGraph.contains("navigator.navigate"))
    }

    private fun findProjectRoot(): File {
        return generateSequence(File(".").canonicalFile) { it.parentFile }
            .firstOrNull { candidate ->
                candidate.resolve("settings.gradle.kts").isFile &&
                    candidate.resolve("app/build.gradle.kts").isFile
            }
            ?: error("Cannot locate My_Live project root")
    }
}
