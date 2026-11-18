package com.mylive.app

import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProguardQuickJsPolicyTest {

    @Test
    fun proguardRulesKeepActualQuickJsRuntimeRootPackage() {
        val rules = File("proguard-rules.pro").readText()
        val loaderRoot = QuickJSLoader::class.java.`package`!!.name.substringBeforeLast('.')
        val wrapperRoot = QuickJSContext::class.java.`package`!!.name.substringBeforeLast('.')

        assertTrue(rules.contains("-keep class $loaderRoot.** { *; }"))
        assertTrue(rules.contains("-keep class $wrapperRoot.** { *; }"))
        assertFalse(rules.contains("-keep class com.quickjs.**"))
    }
}
