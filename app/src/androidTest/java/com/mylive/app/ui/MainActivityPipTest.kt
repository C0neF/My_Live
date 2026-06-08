package com.mylive.app.ui

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.mylive.app.MainActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class MainActivityPipTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        // Only run on Android O (API 26) and above since PiP is not supported below it
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        grantNotificationPermissionIfNeeded()
        assumeTrue(device.hasPictureInPictureEnabled())
    }

    @After
    fun tearDown() {
        // Reset state
        MainActivity.isPipSupportedAndActive = false
        // Wake up device and bring back home if needed
        device.pressHome()
    }

    @Test
    fun testEnteringPipOnHomePress() {
        // Start MainActivity
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario.onActivity { activity ->
            // Enable PiP support state manually to trigger transition on user leave
            MainActivity.isPipSupportedAndActive = true
        }

        // Simulate Home press to trigger onUserLeaveHint()
        device.pressHome()
        
        // Verify that the activity successfully entered PiP mode
        assertTrue(scenario.waitUntilInPictureInPictureMode(timeoutMs = 3_000))

        // Close scenario
        scenario.close()
    }

    private fun grantNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${instrumentation.targetContext.packageName} android.permission.POST_NOTIFICATIONS"
        ).close()
    }

    private fun UiDevice.hasPictureInPictureEnabled(): Boolean {
        val dumpsys = executeShellCommand("dumpsys activity activities")
        return !dumpsys.contains("mDisablePip=true")
    }

    private fun ActivityScenario<MainActivity>.waitUntilInPictureInPictureMode(timeoutMs: Long): Boolean {
        val inPip = AtomicBoolean(false)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !inPip.get()) {
            onActivity { activity ->
                inPip.set(activity.isInPictureInPictureMode)
            }
            if (!inPip.get()) {
                Thread.sleep(100)
            }
        }
        return inPip.get()
    }
}
