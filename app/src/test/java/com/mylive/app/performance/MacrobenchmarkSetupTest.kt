package com.mylive.app.performance

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class MacrobenchmarkSetupTest {

    @Test
    fun projectIncludesTrackedMacrobenchmarkModuleWithoutDroppingEmulatorAbis() {
        val root = findProjectRoot()

        val settings = root.resolve("settings.gradle.kts").readText()
        val gitignore = root.resolve(".gitignore").readText()
        val ignoredEntries = gitignore.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
        val appBuild = root.resolve("app/build.gradle.kts").readText()
        val benchmarkBuildType = appBuild.substringAfter("create(\"benchmark\")")
            .substringBefore("compileOptions")

        assertTrue(settings.contains("include(\":macrobenchmark\")"))
        assertTrue(root.resolve("macrobenchmark/build.gradle.kts").isFile)
        assertFalse(ignoredEntries.contains("/macrobenchmark/"))
        assertTrue(ignoredEntries.contains("/macrobenchmark/build/"))
        assertTrue(benchmarkBuildType.contains("\"x86\""))
        assertTrue(benchmarkBuildType.contains("\"x86_64\""))
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
