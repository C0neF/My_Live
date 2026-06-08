package com.mylive.app.core.script

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JsEngineStartupTest {

    @Test
    fun applicationDoesNotInjectJsEngineAtProcessStartup() {
        val source = readMainSource("com/mylive/app/MyLiveApp.kt")

        assertFalse(source.contains("Inject lateinit var jsEngine"))
        assertFalse(source.contains("jsEngine.destroy()"))
    }

    @Test
    fun quickJsContextIsCreatedLazily() {
        val source = readMainSource("com/mylive/app/core/script/JsEngine.kt")

        assertFalse(source.contains("private val context = QuickJSContext.create()"))
        assertTrue(source.contains("private fun createContext()"))
        assertTrue(source.contains("QuickJSLoader.init()"))
        assertTrue(source.contains("QuickJSContext.create()"))
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
