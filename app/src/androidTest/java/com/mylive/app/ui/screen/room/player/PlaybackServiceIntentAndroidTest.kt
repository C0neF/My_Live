package com.mylive.app.ui.screen.room.player

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylive.app.service.PlaybackForegroundService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs only in the isolated audit app. Uses Android's actual Service and notification PendingIntent,
 * real Sessions/ExoPlayers, and silent PCM served on loopback. No saved data is read or changed.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class PlaybackServiceIntentAndroidTest {
    @get:Rule val compose = createComposeRule(effectContext = StandardTestDispatcher())

    private lateinit var context: ServiceDeliveryContext
    private val sessions = mutableListOf<LivePlaybackSession>()
    private var server: MockWebServer? = null

    @Before
    fun prepareIsolatedForegroundActivity() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Build with -PisolatedDeviceTests=true before running this service test",
            "com.mylive.app.audit", target.packageName)
        context = ServiceDeliveryContext(target.applicationContext)
        compose.setContent { }
        if (Build.VERSION.SDK_INT >= 33 &&
            target.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            shell("pm grant com.mylive.app.audit android.permission.POST_NOTIFICATIONS")
        }
    }

    @After
    fun releaseOnlyAuditPlayback() {
        if (!::context.isInitialized) return
        compose.runOnIdle { sessions.forEach(LivePlaybackSession::release) }
        PlaybackForegroundService.stop(context)
        waitForResult { !serviceIsForeground() }
        compose.runOnIdle { PlaybackForegroundService.activePlayer = null }
        server?.shutdown()
        // Revoking a runtime permission kills the instrumented process. This isolated audit app
        // is uninstalled by the test workflow, which also removes its temporary permission grant.
    }

    @Test
    fun releasingTheOutgoingSessionKeepsTheIncomingRoomsActualService() = withTwoRooms { old, current ->
        assertIncomingServiceSurvives(current, "release") { old.release() }
    }

    @Test
    fun disposingTheOutgoingObserverKeepsTheIncomingRoomsActualService() = withTwoRooms { old, current ->
        assertIncomingServiceSurvives(current, "observer disposal") { old.stopForegroundService() }
    }

    @Test
    fun resumingTheOutgoingSessionKeepsTheIncomingRoomsActualService() = withTwoRooms { old, current ->
        assertIncomingServiceSurvives(current, "old host resume") { old.onHostResume() }
    }

    @Test
    fun releasingTheCurrentOwnerStillStopsItsActualService() = withTwoRooms { _, current ->
        compose.runOnIdle { current.release() }

        awaitState("The owning room must tear down its service") {
            !serviceIsForeground() && PlaybackForegroundService.activePlayer == null
        }
    }

    @Test
    fun notificationStopPreventsAnOlderDeferredSourceFromRestartingOnReturn() {
        assertNotificationInvalidatesDeferredSource("停止")
    }

    @Test
    fun notificationPausePreventsAnOlderDeferredSourceFromRestartingOnReturn() {
        assertNotificationInvalidatesDeferredSource("暂停")
    }

    @Test
    fun deferredSourceStillStartsOnReturnWithoutANewerNotificationAction() {
        val session = startSilentRoom()
        val replacement = server!!.url("/replacement.wav").toString()
        compose.runOnIdle {
            context.holdNewForegroundStarts = true
            session.play(listOf(replacement), origin = PlaybackRequestOrigin.SourceRecovery)
            session.onHostResume()
            assertTrue(session.playerController.player!!.playWhenReady)
            assertEquals(replacement, session.state.value.currentUrl)
        }
    }

    private fun assertNotificationInvalidatesDeferredSource(actionTitle: String) {
        val session = startSilentRoom()
        val action = notificationAction(actionTitle)
        val first = session.state.value.currentUrl
        val replacement = server!!.url("/replacement.wav").toString()
        compose.runOnIdle {
            // The existing notification can arrive before the new FGS start reaches onStartCommand.
            // Hold that public Android dispatch boundary; Session still creates its real request.
            context.holdNewForegroundStarts = true
            session.play(listOf(replacement), origin = PlaybackRequestOrigin.SourceRecovery)
            assertEquals("The new request must still be deferred", first, session.state.value.currentUrl)
            assertEquals(1, context.heldForegroundStarts)
        }

        action.send()
        awaitState("The actual notification action must pause the actual ExoPlayer") {
            var paused = false
            compose.runOnIdle { paused = session.playerController.player?.playWhenReady == false }
            paused
        }
        if (actionTitle == "停止") awaitState("Stop must destroy the actual foreground service") { !serviceIsForeground() }

        compose.runOnIdle {
            session.onHostResume()
            assertFalse("Returning to the room must preserve the newer notification action: $actionTitle",
                session.playerController.player!!.playWhenReady)
        }
    }

    private fun startSilentRoom(): LivePlaybackSession {
        val pcm = silentWav()
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                    .setHeader("Content-Type", "audio/wav")
                    .setBody(Buffer().write(pcm))
            }
            start(InetAddress.getByName("127.0.0.1"), 0)
        }
        val session = newSession()
        // MockWebServer.url resolves its canonical host name; do that on the test thread.
        val firstUrl = server!!.url("/first.wav").toString()
        compose.runOnIdle {
            session.play(listOf(firstUrl), origin = PlaybackRequestOrigin.UserSelection)
        }
        awaitState("The synthetic PCM fixture must actually be playing before notification delivery") {
            var playing = false
            compose.runOnIdle { playing = session.playerController.player!!.isPlaying }
            playing
        }
        handOff(session, "synthetic-room")
        return session
    }

    private fun withTwoRooms(test: (LivePlaybackSession, LivePlaybackSession) -> Unit) {
        val old = newSession()
        compose.runOnIdle { old.resume() }
        handOff(old, "outgoing-room")
        val current = newSession()
        compose.runOnIdle { current.resume() }
        handOff(current, "incoming-room")
        test(old, current)
    }

    private fun newSession(): LivePlaybackSession = compose.runOnIdle {
        LivePlaybackSession.create(context, true, false, null).also {
            it.playerController.player!!.volume = 0f
            sessions += it
        }
    }

    private fun handOff(session: LivePlaybackSession, title: String) {
        compose.runOnIdle { session.onHostPause(true, false, title, "synthetic-anchor", "test") }
        awaitState("Android must promote the room's actual service") {
            serviceIsForeground() && PlaybackForegroundService.activePlayer === session.playerController.player &&
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).activeNotifications
                    .any { it.id == PlaybackForegroundService.NOTIFICATION_ID && it.notification.extras.getString("android.title") == title }
        }
    }

    private fun assertIncomingServiceSurvives(current: LivePlaybackSession, operation: String, cleanup: () -> Unit) {
        val previousStops = context.playbackStops
        compose.runOnIdle(cleanup)
        val requestedStops = context.playbackStops - previousStops
        // If the old room asked Android to stop the service, wait for real teardown to be observed.
        if (requestedStops > 0) waitForResult { !serviceIsForeground() }
        val running = serviceIsForeground()
        assertTrue("Old room $operation must preserve the incoming FGS; stop requests=$requestedStops, running=$running",
            running)
        assertSame(current.playerController.player, PlaybackForegroundService.activePlayer)
    }

    private fun notificationAction(title: String): PendingIntent {
        var result: PendingIntent? = null
        awaitState("The real foreground notification must expose action $title") {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            result = manager.activeNotifications.firstOrNull { it.id == PlaybackForegroundService.NOTIFICATION_ID }
                ?.notification?.actions?.firstOrNull { it.title.toString() == title }?.actionIntent
            result != null
        }
        return checkNotNull(result)
    }

    @Suppress("DEPRECATION")
    private fun serviceIsForeground(): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == PlaybackForegroundService::class.java.name && it.foreground }

    private fun awaitState(message: String, condition: () -> Boolean) {
        assertTrue(message, waitForResult(condition))
    }

    private fun waitForResult(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(25)
        }
        return condition()
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
    }

    /** Controls only asynchronous FGS dispatch, without changing any Session or Service field. */
    private class ServiceDeliveryContext(base: Context) : ContextWrapper(base) {
        var holdNewForegroundStarts = false
        var heldForegroundStarts = 0
            private set
        var playbackStops = 0
            private set

        override fun getApplicationContext(): Context = this

        override fun startForegroundService(service: Intent): ComponentName? {
            if (holdNewForegroundStarts && service.component?.className == PlaybackForegroundService::class.java.name) {
                heldForegroundStarts++
                return service.component
            }
            return super.startForegroundService(service)
        }

        override fun stopService(service: Intent): Boolean {
            if (service.component?.className == PlaybackForegroundService::class.java.name) playbackStops++
            return super.stopService(service)
        }
    }

    private fun silentWav(): ByteArray {
        val bytes = 8_000 * 2 * 30
        return ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + bytes)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1); putShort(1); putInt(8_000); putInt(16_000)
            putShort(2); putShort(16)
            put("data".toByteArray(Charsets.US_ASCII)); putInt(bytes)
        }.array()
    }
}
