package com.mylive.app.ui.screen.follow

import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.service.FollowStatusRefreshCoordinator
import com.mylive.app.testing.ReviewRoomFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FollowTagRefreshRaceTest : ReviewRoomFixture() {
    @Test
    fun confirmingAnOldTagDialogPreservesTheLatestPresenceAndCanonicalIdentity() = runBlocking {
        val dialogFollow = FollowUserEntity(id = "douyin_old", roomId = "old", siteId = "douyin",
            userName = "anchor", face = "", addTime = 1L, liveStatus = 2)
        val targetTag = FollowUserTagEntity("target", "new-group", emptyList())
        follows.addFollow(dialogFollow)
        follows.addTag(targetTag)
        val viewModel = own(FollowViewModel(follows, settings,
            FollowStatusRefreshCoordinator(follows, settings, emptySet())))
        val refreshed = dialogFollow.copy(liveStatus = 1, liveStartTime = 123L,
            showTime = "123", canonicalRoomId = "canonical")
        follows.updateLiveStatuses(listOf(refreshed))
        assertEquals(refreshed, follows.getAllFollows().first().single())

        viewModel.setFollowTag(dialogFollow, targetTag)
        val afterConfirmation = withTimeout(10_000) {
            follows.getAllFollows().first { it.singleOrNull()?.tag == "new-group" }.single()
        }

        assertEquals("Tag confirmation must not restore the dialog's stale full row",
            refreshed.copy(tag = "new-group"), afterConfirmation)
        assertEquals(listOf(dialogFollow.id), follows.getAllTags().first().single().userIds)
    }
}
