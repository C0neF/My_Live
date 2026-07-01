package com.mylive.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.repository.FollowRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FollowRepositoryImportTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FollowRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FollowRepository(db.followUserDao(), db.followUserTagDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun overlayImportKeepsExistingDataWhenPayloadIsInvalid() = runBlocking {
        repository.addFollow(
            FollowUserEntity(
                id = "bilibili_1",
                roomId = "1",
                siteId = "bilibili",
                userName = "existing",
                face = "",
                addTime = 1L
            )
        )
        repository.addTag(
            FollowUserTagEntity(
                id = "tag-1",
                tag = "favorite",
                userIds = listOf("bilibili_1")
            )
        )

        assertThrows(Exception::class.java) {
            runBlocking {
                repository.importFromJson("""{"follows":[{"siteId":"bilibili"}]}""", overlay = true)
            }
        }

        assertEquals(1, repository.getAllFollows().first().size)
        assertEquals(1, repository.getAllTags().first().size)
    }

    @Test
    fun overlayImportKeepsExistingDataWhenPayloadHasNoKnownCollections() = runBlocking {
        repository.addFollow(
            FollowUserEntity(
                id = "douyu_2",
                roomId = "2",
                siteId = "douyu",
                userName = "existing",
                face = "",
                addTime = 1L
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.importFromJson("{}", overlay = true)
            }
        }

        assertEquals(1, repository.getAllFollows().first().size)
    }

    @Test
    fun exportImportRoundTripPreservesLiveMetadata() = runBlocking {
        repository.addFollow(
            FollowUserEntity(
                id = "bilibili_100",
                roomId = "100",
                siteId = "bilibili",
                userName = "streamer",
                face = "https://example.com/avatar.jpg",
                addTime = 123L,
                liveStatus = 1,
                liveStartTime = 456L,
                showTime = "789"
            )
        )

        val backup = repository.exportToJson()
        repository.clearAllFollows()
        repository.importFromJson(backup)

        val restored = repository.getAllFollows().first().single()
        assertEquals(1, restored.liveStatus)
        assertEquals(456L, restored.liveStartTime)
        assertEquals("789", restored.showTime)
    }
}
