package com.mylive.app.ui.screen.follow

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.local.secure.AndroidKeystoreCredentialCipher
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.service.FollowStatusRefreshCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android Main + Room + ViewModel; use -PisolatedDeviceTests=true on an emulator. */
@RunWith(AndroidJUnit4::class)
class FollowTagMembershipInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var follows: FollowRepository
    private lateinit var settings: SettingsRepository
    private val stores = mutableListOf<ViewModelStore>()
    private val first = follow("a")
    private val second = follow("b")
    private val dialogGroup = FollowUserTagEntity("membership-group", "friends", emptyList())

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        follows = FollowRepository(database.followUserDao(), database.followUserTagDao(), database)
        val store = SettingsDataStore(context)
        settings = SettingsRepository(store, SensitiveCredentialStore(store, AndroidKeystoreCredentialCipher()))
        settings.siteSort.first()
        follows.addFollows(listOf(first, second))
        follows.addTag(dialogGroup)
    }

    @After
    fun tearDown() = runBlocking {
        withContext(Dispatchers.Main.immediate) { stores.forEach(ViewModelStore::clear) }
        database.close()
    }

    @Test
    fun assigningTwoFollowsFromOneOldGroupSnapshotPreservesBothRoomMemberships() = runBlocking {
        val viewModel = newViewModel()

        assign(viewModel, first, dialogGroup)
        assign(viewModel, second, dialogGroup)

        assertEquals("An old group snapshot must not replace another member",
            setOf(first.id, second.id), currentGroup().userIds.toSet())
        assertEquals(2, currentGroup().userIds.size)
        assertEquals(dialogGroup.tag, follows.getFollow(first.siteId, first.roomId)!!.tag)
        assertEquals(dialogGroup.tag, follows.getFollow(second.siteId, second.roomId)!!.tag)
    }

    @Test
    fun tagFilterKeepsBothMembersAfterTheSecondOldDialogConfirmation() = runBlocking {
        val viewModel = newViewModel()
        val collector = launch(Dispatchers.Main.immediate) { viewModel.filteredFollows.collect() }
        try {
            withContext(Dispatchers.Main.immediate) { viewModel.setGroupMode(FollowGroupMode.TAG) }
            withTimeout(10_000) {
                viewModel.groupOptions.first { options -> options.any { it.tagId == dialogGroup.id } }
                viewModel.follows.first { it.size == 2 }
            }
            withContext(Dispatchers.Main.immediate) { viewModel.setGroupOption("tag:${dialogGroup.id}") }
            withTimeout(10_000) { viewModel.filteredFollows.first { it.isEmpty() } }
            assign(viewModel, first, dialogGroup)
            withTimeout(10_000) { viewModel.filteredFollows.first { it.map { row -> row.id } == listOf(first.id) } }

            assign(viewModel, second, dialogGroup)
            val visible = withTimeout(10_000) {
                viewModel.filteredFollows.first { rows -> rows.any { it.id == second.id } }
            }

            assertEquals(setOf(first.id, second.id), visible.map { it.id }.toSet())
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun currentGroupSnapshotAndUngroupingPreserveTheOtherMember() = runBlocking {
        val viewModel = newViewModel()
        assign(viewModel, first, dialogGroup)
        assign(viewModel, second, currentGroup())
        assertEquals(setOf(first.id, second.id), currentGroup().userIds.toSet())

        assign(viewModel, second, null)

        assertEquals(listOf(first.id), currentGroup().userIds)
        assertEquals(dialogGroup.tag, follows.getFollow(first.siteId, first.roomId)!!.tag)
        assertEquals("", follows.getFollow(second.siteId, second.roomId)!!.tag)
    }

    private suspend fun newViewModel(): FollowViewModel = withContext(Dispatchers.Main.immediate) {
        FollowViewModel(follows, settings, FollowStatusRefreshCoordinator(follows, settings, emptySet()))
            .also { viewModel -> stores += ViewModelStore().also { it.put("membership-test", viewModel) } }
    }

    private suspend fun assign(viewModel: FollowViewModel, follow: FollowUserEntity, group: FollowUserTagEntity?) {
        val actions = withContext(Dispatchers.Main.immediate) {
            val parent = requireNotNull(viewModel.viewModelScope.coroutineContext[Job])
            val before = parent.children.toSet()
            viewModel.setFollowTag(follow, group)
            parent.children.filterNot { it in before }.toList()
        }
        withTimeout(10_000) { actions.joinAll() }
        actions.forEach { assertFalse("The assignment must finish successfully", it.isCancelled) }
    }

    private suspend fun currentGroup() = follows.getAllTags().first().single { it.id == dialogGroup.id }

    private fun follow(room: String) = FollowUserEntity(
        id = "huya_membership_$room", roomId = "membership_$room", siteId = "huya",
        userName = room, face = "", addTime = 1L
    )
}
