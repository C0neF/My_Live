package com.mylive.app.ui.screen.follow

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.dao.FollowUserDao
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.local.secure.AndroidKeystoreCredentialCipher
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.service.FollowStatusRefreshCoordinator
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses the real Android main dispatcher, FollowViewModel, repositories and in-memory Room DAOs.
 * Run with -PisolatedDeviceTests=true so the real settings DataStore belongs to the audit app.
 * No settings are changed and no network refresh is started.
 */
@RunWith(AndroidJUnit4::class)
class FollowGroupMutationInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var follows: FollowRepository
    private lateinit var settings: SettingsRepository
    private val stores = mutableListOf<ViewModelStore>()

    private val original = FollowUserEntity(
        id = "douyin_group-race", roomId = "group-race", siteId = "douyin",
        userName = "original-name", face = "original-face", addTime = 1L,
        tag = "original-group", liveStatus = 2
    )
    private val group = FollowUserTagEntity("group-id", "original-group", listOf(original.id))

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        follows = FollowRepository(database.followUserDao(), database.followUserTagDao(), database)
        val settingsDataStore = SettingsDataStore(context)
        settings = SettingsRepository(
            settingsDataStore,
            SensitiveCredentialStore(settingsDataStore, AndroidKeystoreCredentialCipher())
        )
        // Read the actual isolated DataStore, rather than substituting mocked SettingsRepository flows.
        settings.siteSort.first()
        follows.addFollow(original)
        follows.addTag(group)
    }

    @After
    fun tearDown() = runBlocking {
        withContext(Dispatchers.Main.immediate) { stores.forEach(ViewModelStore::clear) }
        database.close()
    }

    @Test
    fun renamingGroupDoesNotRecreateAConcurrentlyDeletedFollow() = runBlocking {
        assertDeletionSurvivesGroupMutation(rename = true)
    }

    @Test
    fun removingGroupDoesNotRecreateAConcurrentlyDeletedFollow() = runBlocking {
        assertDeletionSurvivesGroupMutation(rename = false)
    }

    @Test
    fun renamingGroupPreservesConcurrentlyRefreshedFollowFields() = runBlocking {
        assertRefreshSurvivesGroupMutation(rename = true)
    }

    @Test
    fun removingGroupPreservesConcurrentlyRefreshedFollowFields() = runBlocking {
        assertRefreshSurvivesGroupMutation(rename = false)
    }

    @Test
    fun renamingGroupUpdatesOnlyItsMembersTagAndItsOwnName() = runBlocking {
        assertNormalGroupMutation(rename = true)
    }

    @Test
    fun removingGroupClearsOnlyItsMembersTagAndRemovesItsDefinition() = runBlocking {
        assertNormalGroupMutation(rename = false)
    }

    @Test
    fun assigningOneFollowFromAStaleDialogPreservesLatestFields() = runBlocking {
        val latest = refreshExistingFollow()
        val target = FollowUserTagEntity("new-group-id", "new-group", emptyList())
        follows.addTag(target)
        val viewModel = newViewModel(follows)

        launchOperation(viewModel) { setFollowTag(original, target) }.joinAndCheck()

        assertEquals(latest.copy(tag = target.tag), follows.getFollow(original.siteId, original.roomId))
        val tags = follows.getAllTags().first().associateBy { it.id }
        assertEquals(emptyList<String>(), tags.getValue(group.id).userIds)
        assertEquals(listOf(original.id), tags.getValue(target.id).userIds)
    }

    @Test
    fun assigningOneDeletedFollowFromAStaleDialogDoesNotRecreateIt() = runBlocking {
        val viewModel = newViewModel(follows)
        follows.removeFollow(original.id)
        assertNull(follows.getFollow(original.siteId, original.roomId))

        launchOperation(viewModel) { setFollowTag(original, null) }.joinAndCheck()

        assertNull("An UPDATE of a deleted follow must not insert it", follows.getFollow(original.siteId, original.roomId))
    }

    private suspend fun assertDeletionSurvivesGroupMutation(rename: Boolean) {
        withGroupMutationRace(rename) {
            follows.removeFollow(original.id)
            assertNull("The competing deletion must have reached Room", follows.getFollow(original.siteId, original.roomId))
        }

        assertNull("Changing a group must never restore an unfollowed room", follows.getFollow(original.siteId, original.roomId))
        assertGroupResult(rename)
    }

    private suspend fun assertRefreshSurvivesGroupMutation(rename: Boolean) {
        var refreshed: FollowUserEntity? = null
        withGroupMutationRace(rename) { refreshed = refreshExistingFollow() }

        assertEquals(
            "Changing a group may update only tag; it must preserve the latest status and profile fields",
            refreshed!!.copy(tag = if (rename) "renamed-group" else ""),
            follows.getFollow(original.siteId, original.roomId)
        )
        assertGroupResult(rename)
    }

    private suspend fun assertNormalGroupMutation(rename: Boolean) {
        val other = original.copy(id = "huya_other", siteId = "huya", roomId = "other", tag = "other-group")
        follows.addFollow(other)
        val viewModel = newViewModel(follows)

        launchOperation(viewModel) { mutateGroup(rename) }.joinAndCheck()

        assertEquals(original.copy(tag = if (rename) "renamed-group" else ""), follows.getFollow(original.siteId, original.roomId))
        assertEquals(other, follows.getFollow(other.siteId, other.roomId))
        assertGroupResult(rename)
    }

    private suspend fun assertGroupResult(rename: Boolean) {
        val after = follows.getAllTags().first().find { it.id == group.id }
        if (rename) assertEquals(group.copy(tag = "renamed-group"), after) else assertNull(after)
    }

    private suspend fun refreshExistingFollow(): FollowUserEntity {
        val current = requireNotNull(follows.getFollow(original.siteId, original.roomId))
        val latest = current.copy(
            userName = "refreshed-name", face = "refreshed-face", addTime = 123L,
            isSpecialFollow = true, liveStatus = 1, liveStartTime = 1_720_000_000L,
            showTime = "1720000000", canonicalRoomId = "confirmed-canonical-room"
        )
        follows.addFollow(latest)
        assertEquals(latest, follows.getFollow(original.siteId, original.roomId))
        return latest
    }

    /**
     * Freeze a full-row snapshot only if the operation reads one. A repaired implementation may
     * update tags without such a read, in which case it completes before the competing mutation.
     * Both serializations must preserve the competitor's result. The select avoids waiting for a
     * deleted implementation detail, and the gate holds no Room transaction or SQLite lock.
     */
    private suspend fun withGroupMutationRace(rename: Boolean, competingMutation: suspend () -> Unit) {
        val gate = FullRowSnapshotGate()
        val actual = database.followUserDao()
        val gated = object : FollowUserDao by actual {
            override fun getAll(): Flow<List<FollowUserEntity>> = flow {
                val snapshot = actual.getAll().first()
                if (gate.armed.compareAndSet(true, false)) {
                    gate.snapshot.complete(snapshot)
                    gate.release.await()
                }
                emit(snapshot)
            }
        }
        val observed = FollowRepository(gated, database.followUserTagDao(), database)
        val viewModel = newViewModel(observed)
        val operation = launchOperation(viewModel) { mutateGroup(rename) }
        withTimeout(10_000) {
            coroutineScope {
                val finished = async { operation.joinAndCheck() }
                try {
                    val captured = select {
                        gate.snapshot.onAwait { snapshot ->
                            assertEquals(original.id, snapshot.single().id)
                            true
                        }
                        finished.onAwait { false }
                    }
                    println("Group mutation full-row snapshot captured: $captured")
                    competingMutation()
                } finally {
                    gate.release.complete(Unit)
                }
                finished.await()
            }
        }
    }

    private fun FollowViewModel.mutateGroup(rename: Boolean) {
        if (rename) renameTag(group.id, "renamed-group") else removeTag(group.id)
    }

    private suspend fun newViewModel(repository: FollowRepository): FollowViewModel =
        withContext(Dispatchers.Main.immediate) {
            FollowViewModel(repository, settings, FollowStatusRefreshCoordinator(follows, settings, emptySet()))
                .also { vm -> stores += ViewModelStore().also { it.put("follow-group-test", vm) } }
        }

    /** Capture only this action's launch, never the permanent stateIn children of viewModelScope. */
    private suspend fun launchOperation(viewModel: FollowViewModel, operation: FollowViewModel.() -> Unit): List<Job> =
        withContext(Dispatchers.Main.immediate) {
            val parent = requireNotNull(viewModel.viewModelScope.coroutineContext[Job])
            val before = parent.children.toSet()
            viewModel.operation()
            parent.children.filterNot { it in before }.toList()
        }

    private suspend fun List<Job>.joinAndCheck() = withTimeout(10_000) {
        joinAll()
        forEach { assertFalse("The group action must complete without cancellation", it.isCancelled) }
    }

    private class FullRowSnapshotGate {
        val armed = AtomicBoolean(true)
        val snapshot = CompletableDeferred<List<FollowUserEntity>>()
        val release = CompletableDeferred<Unit>()
    }
}
