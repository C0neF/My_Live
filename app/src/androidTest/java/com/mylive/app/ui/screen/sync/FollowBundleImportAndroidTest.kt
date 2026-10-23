package com.mylive.app.ui.screen.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.repository.FollowRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android SQLite/Room import boundary; sender and receiver use separate in-memory databases. */
@RunWith(AndroidJUnit4::class)
class FollowBundleImportAndroidTest {
    private lateinit var sourceDatabase: AppDatabase
    private lateinit var targetDatabase: AppDatabase
    private lateinit var source: FollowRepository
    private lateinit var target: FollowRepository
    private val incoming = FollowUserEntity("huya_A", "A", "huya", "source-A", "", 1L,
        tag = "G", liveStatus = 1, liveStartTime = 123L)
    private val original = incoming.copy(userName = "local-A", tag = "H")
    private val unrelated = FollowUserEntity("huya_C", "C", "huya", "keep-C", "", 2L,
        tag = "H", isSpecialFollow = true, liveStatus = 1, liveStartTime = 456L)
    private val incomingGroup = FollowUserTagEntity("source-G", "G", listOf(incoming.id))
    private val originalGroup = FollowUserTagEntity("local-H", "H", listOf(original.id, unrelated.id))

    @Before fun prepareIsolatedDatabases() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.mylive.app.audit", context.packageName)
        sourceDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        targetDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        source = FollowRepository(sourceDatabase.followUserDao(), sourceDatabase.followUserTagDao(), sourceDatabase)
        target = FollowRepository(targetDatabase.followUserDao(), targetDatabase.followUserTagDao(), targetDatabase)
        source.addFollow(incoming)
        source.addTag(incomingGroup)
        target.addFollows(listOf(original, unrelated))
        target.addTag(originalGroup)
    }

    @After fun closeDatabases() {
        if (::sourceDatabase.isInitialized) sourceDatabase.close()
        if (::targetDatabase.isInitialized) targetDatabase.close()
    }

    @Test fun mergingMovesTheImportedMemberOutOfTheOldGroupAndKeepsTheUnrelatedFollow() = runBlocking {
        target.importFromJson(source.exportToJson(), overlay = false)

        assertEquals(incoming, target.getFollow("huya", "A"))
        assertEquals(unrelated, target.getFollow("huya", "C"))
        val tags = target.getAllTags().first().associateBy { it.id }
        assertEquals(incomingGroup, tags.getValue(incomingGroup.id))
        assertEquals(originalGroup.copy(userIds = listOf(unrelated.id)), tags.getValue(originalGroup.id))
    }

    @Test fun aFailedTagInsertRollsBackBothTheOverlayDeletesAndTheNewFollowRows() = runBlocking {
        targetDatabase.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_source_tag BEFORE INSERT ON follow_user_tags " +
                "WHEN NEW.id = 'source-G' BEGIN SELECT RAISE(ABORT, 'controlled tag failure'); END"
        )

        val failure = runCatching { target.importFromJson(source.exportToJson(), overlay = true) }.exceptionOrNull()

        assertNotNull("The actual SQLite trigger must fail the import", failure)
        assertEquals(listOf(original, unrelated).sortedBy { it.id }, target.getAllFollows().first().sortedBy { it.id })
        assertEquals(listOf(originalGroup), target.getAllTags().first())
    }

    @Test fun overlayPublishesExactlyTheSourceCollections() = runBlocking {
        target.importFromJson(source.exportToJson(), overlay = true)

        assertEquals(listOf(incoming), target.getAllFollows().first())
        assertEquals(listOf(incomingGroup), target.getAllTags().first())
    }
}
