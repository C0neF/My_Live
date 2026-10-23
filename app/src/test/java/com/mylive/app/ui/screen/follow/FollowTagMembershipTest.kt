package com.mylive.app.ui.screen.follow

import androidx.lifecycle.viewModelScope
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.service.FollowStatusRefreshCoordinator
import com.mylive.app.testing.ReviewRoomFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real ViewModel and Room: a dialog's immutable group snapshot may outlive another assignment. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FollowTagMembershipTest : ReviewRoomFixture() {
    private val first = follow("a")
    private val second = follow("b")
    private val outsider = follow("c").copy(tag = "other")
    private val dialogGroup = FollowUserTagEntity("group", "friends", emptyList())
    private val otherGroup = FollowUserTagEntity("other-group", "other", listOf(outsider.id))

    @Before
    fun seedMemberships() = runBlocking {
        follows.addFollows(listOf(first, second, outsider))
        follows.addTags(listOf(dialogGroup, otherGroup))
    }

    @Test
    fun assigningTwoFollowsWithTheSameOldGroupSnapshotPreservesBothMembers() = runBlocking {
        val viewModel = newViewModel()

        assign(viewModel, first, dialogGroup)
        assign(viewModel, second, dialogGroup)

        assertEquals("A later assignment must merge with the current persisted membership",
            setOf(first.id, second.id), currentGroup(dialogGroup.id).userIds.toSet())
        assertEquals(2, currentGroup(dialogGroup.id).userIds.size)
        assertEquals(dialogGroup.tag, follows.getFollow(first.siteId, first.roomId)!!.tag)
        assertEquals(dialogGroup.tag, follows.getFollow(second.siteId, second.roomId)!!.tag)
        assertEquals(otherGroup, currentGroup(otherGroup.id))
    }

    @Test
    fun tagFilterKeepsTheFirstMemberAfterAssigningAnotherFromAnOldDialog() = runBlocking {
        val viewModel = newViewModel()
        val collector = launch(Dispatchers.Unconfined) { viewModel.filteredFollows.collect() }
        try {
            viewModel.setGroupMode(FollowGroupMode.TAG)
            withTimeout(10_000) {
                viewModel.groupOptions.first { options -> options.any { it.tagId == dialogGroup.id } }
                viewModel.follows.first { it.size == 3 }
            }
            viewModel.setGroupOption("tag:${dialogGroup.id}")
            withTimeout(10_000) { viewModel.filteredFollows.first { it.isEmpty() } }
            assign(viewModel, first, dialogGroup)
            withTimeout(10_000) {
                viewModel.filteredFollows.first { it.map { follow -> follow.id }.toSet() == setOf(first.id) }
            }

            assign(viewModel, second, dialogGroup)
            val visible = withTimeout(10_000) {
                viewModel.filteredFollows.first { rows -> rows.any { it.id == second.id } }
            }

            assertEquals("TAG filtering must retain earlier members when another follow joins",
                setOf(first.id, second.id), visible.map { it.id }.toSet())
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun currentSnapshotsSupportJoiningMovingAndClearingWithoutChangingOtherMembers() = runBlocking {
        val viewModel = newViewModel()
        assign(viewModel, first, dialogGroup)
        assign(viewModel, second, currentGroup(dialogGroup.id))
        assertEquals(setOf(first.id, second.id), currentGroup(dialogGroup.id).userIds.toSet())

        assign(viewModel, second, currentGroup(otherGroup.id))
        assertEquals(listOf(first.id), currentGroup(dialogGroup.id).userIds)
        assertEquals(setOf(outsider.id, second.id), currentGroup(otherGroup.id).userIds.toSet())
        assertEquals(otherGroup.tag, follows.getFollow(second.siteId, second.roomId)!!.tag)

        assign(viewModel, second, null)
        assertEquals(listOf(first.id), currentGroup(dialogGroup.id).userIds)
        assertEquals(listOf(outsider.id), currentGroup(otherGroup.id).userIds)
        assertEquals("", follows.getFollow(second.siteId, second.roomId)!!.tag)
        assertEquals(outsider, follows.getFollow(outsider.siteId, outsider.roomId))
    }

    @Test
    fun oldDialogUsesTheCurrentTargetNameAndRetainsItsCurrentMembers() = runBlocking {
        val viewModel = newViewModel()
        assign(viewModel, first, dialogGroup)
        follows.renameTag(dialogGroup.id, "renamed-friends")

        assign(viewModel, second, dialogGroup)

        val current = currentGroup(dialogGroup.id)
        assertEquals("renamed-friends", current.tag)
        assertEquals(setOf(first.id, second.id), current.userIds.toSet())
        assertEquals("renamed-friends", follows.getFollow(first.siteId, first.roomId)!!.tag)
        assertEquals("renamed-friends", follows.getFollow(second.siteId, second.roomId)!!.tag)
    }

    @Test
    fun deletedTargetFromAnOldDialogLeavesTheOriginalAssignmentIntact() = runBlocking {
        val viewModel = newViewModel()
        assign(viewModel, first, otherGroup)
        val before = follows.getAllTags().first().toSet()
        follows.removeTag(dialogGroup.id)

        assign(viewModel, first, dialogGroup)

        assertEquals(before.filterNot { it.id == dialogGroup.id }.toSet(), follows.getAllTags().first().toSet())
        assertEquals(setOf(outsider.id, first.id), currentGroup(otherGroup.id).userIds.toSet())
        assertEquals(otherGroup.tag, follows.getFollow(first.siteId, first.roomId)!!.tag)
    }

    @Test
    fun deletedFollowFromAnOldDialogIsNotAddedToTheTargetMembership() = runBlocking {
        val viewModel = newViewModel()
        follows.removeFollow(first.id)

        assign(viewModel, first, dialogGroup)

        assertEquals(emptyList<String>(), currentGroup(dialogGroup.id).userIds)
        assertNull(follows.getFollow(first.siteId, first.roomId))
        assertEquals(otherGroup, currentGroup(otherGroup.id))
    }

    private fun newViewModel() = own(FollowViewModel(follows, settings,
        FollowStatusRefreshCoordinator(follows, settings, emptySet())))

    private suspend fun assign(viewModel: FollowViewModel, follow: FollowUserEntity, tag: FollowUserTagEntity?) {
        val parent = requireNotNull(viewModel.viewModelScope.coroutineContext[Job])
        val before = parent.children.toSet()
        viewModel.setFollowTag(follow, tag)
        val actions = parent.children.filterNot { it in before }.toList()
        withTimeout(10_000) { actions.joinAll() }
        actions.forEach { assertFalse("The assignment must finish successfully", it.isCancelled) }
    }

    private suspend fun currentGroup(id: String) = follows.getAllTags().first().single { it.id == id }

    private fun follow(room: String) = FollowUserEntity(
        id = "huya_$room", roomId = room, siteId = "huya", userName = room, face = "", addTime = 1L
    )
}
