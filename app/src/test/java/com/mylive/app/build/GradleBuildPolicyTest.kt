package com.mylive.app.build

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GradleBuildPolicyTest {

    @Test
    fun releaseBuildShrinksResourcesWhenMinified() {
        val buildFile = File("build.gradle.kts").readText()

        assertTrue(buildFile.contains("isMinifyEnabled = true"))
        assertTrue(buildFile.contains("isShrinkResources = true"))
    }

    @Test
    fun appDependenciesUseVersionCatalogAliases() {
        val buildFile = File("build.gradle.kts").readText()
        val inlineDependency = Regex("""\b\w*Implementation\("[^"]+:[^"]+:[^"]+"\)""")

        assertFalse(
            "Use libs.versions.toml aliases instead of inline dependency coordinates",
            inlineDependency.containsMatchIn(buildFile)
        )
    }

    @Test
    fun gradlePropertiesDoNotOptOutOfAgp10Defaults() {
        val properties = File("../gradle.properties").readText()
        val deprecatedFlags = listOf(
            "android.defaults.buildfeatures.resvalues",
            "android.sdk.defaultTargetSdkToCompileSdkIfUnset",
            "android.enableAppCompileTimeRClass",
            "android.usesSdkInManifest.disallowed",
            "android.builtInKotlin",
            "android.newDsl"
        )

        deprecatedFlags.forEach { flag ->
            assertFalse("$flag should not be pinned to the old AGP behavior", properties.contains("$flag="))
        }
    }

    @Test
    fun appBuildUsesAgpBuiltInKotlin() {
        val buildFile = File("build.gradle.kts").readText()

        assertFalse(buildFile.contains("libs.plugins.kotlin.android"))
        assertTrue(buildFile.contains("compilerOptions"))
    }
}
