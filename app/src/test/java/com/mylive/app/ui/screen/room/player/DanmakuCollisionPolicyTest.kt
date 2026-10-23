package com.mylive.app.ui.screen.room.player

import org.junit.Assert.*
import org.junit.Test

class DanmakuCollisionPolicyTest {
    @Test fun aFastLongMessageCannotCatchAnEarlierShortMessage() {
        assertFalse(canFollowDanmaku(1003f, 0.1475f, 1080f, 0.21f, 24f))
    }
    @Test fun aSlowerFollowerCanUseTheAlreadyClearEntrance() {
        assertTrue(canFollowDanmaku(900f, 0.21f, 1080f, 0.1475f, 24f))
    }
    @Test fun aFasterFollowerIsAllowedWhenTheLeaderWillExitBeforeItCatchesUp() {
        assertTrue(canFollowDanmaku(200f, 0.1475f, 1080f, 0.21f, 24f))
    }
    @Test fun insufficientEntrySpacingIsRejectedEvenAtEqualSpeed() {
        assertFalse(canFollowDanmaku(1060f, 0.15f, 1080f, 0.15f, 24f))
    }
}
