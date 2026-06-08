package com.mylive.app.ui.screen.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SyncScanPortraitPolicyTest {

    @Test
    fun syncScannerUsesPortraitCaptureActivity() {
        val source = File("src/main/java/com/mylive/app/ui/screen/sync/SyncScanScreen.kt").readText()

        assertTrue(
            "LAN sync scanner must launch the portrait-only CaptureActivity",
            source.contains("setCaptureActivity(PortraitCaptureActivity::class.java)")
        )
        assertTrue(
            "LAN sync scanner must not follow sensor landscape orientation",
            source.contains("setOrientationLocked(true)")
        )
    }

    @Test
    fun portraitCaptureActivityIsDeclaredPortraitInManifest() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains(".ui.screen.sync.PortraitCaptureActivity"))
        assertTrue(manifest.contains("android:screenOrientation=\"portrait\""))
    }
}
