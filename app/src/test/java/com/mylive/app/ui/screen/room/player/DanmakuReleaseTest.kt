package com.mylive.app.ui.screen.room.player

import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DanmakuReleaseTest {
    @Test fun releasedControllerRejectsMessages() {
        val controller = DanmakuController(ApplicationProvider.getApplicationContext())
        controller.release()
        repeat(1000) { controller.addDanmaku(message(it)) }
        assertEquals(0, controller.scheduleSnapshot().pendingCount)
    }

    @Test fun absentSurfaceCannotGrowQueueWithoutLimit() {
        val controller = DanmakuController(ApplicationProvider.getApplicationContext())
        try {
            repeat(2000) { controller.addDanmaku(message(it)) }
            assertEquals(DanmakuController.MAX_PENDING_DANMAKU, controller.scheduleSnapshot().pendingCount)
        } finally { controller.release() }
    }

    @Test fun disabledDanmakuDoesNotFeedAnExistingController() {
        assertFalse(shouldFeedLiveDanmakuSurface(true, true, enabled = false))
        assertTrue(shouldFeedLiveDanmakuSurface(true, true, enabled = true))
    }

    private fun message(index: Int) = LiveMessage(LiveMessageType.CHAT, "user", "message-$index")
}
