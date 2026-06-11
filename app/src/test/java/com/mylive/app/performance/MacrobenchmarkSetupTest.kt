package com.mylive.app.performance

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class MacrobenchmarkSetupTest {

    @Test
    fun projectDoesNotRequireIgnoredLocalMacrobenchmarkModule() {
        val root = findProjectRoot()

        val settings = root.resolve("settings.gradle.kts").readText()
        val gitignore = root.resolve(".gitignore").readText()

        assertFalse(settings.contains("include(\":macrobenchmark\")"))
        assertTrue(gitignore.contains("/macrobenchmark/"))
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
