package com.mylive.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.repository.HistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryImportTransactionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: HistoryRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = HistoryRepository(db.historyDao())
    }

    @After fun close() { db.close() }

    @Test fun malformedRowsNeverDeleteLocalHistory() = runBlocking {
        val existing = HistoryEntity("bilibili_1", "1", "bilibili", "existing", "", 100L)
        repository.addHistory(existing)
        for (payload in listOf("[1]", "[{}]", "[{\"siteId\":\"bilibili\",\"roomId\":\"2\"},1]")) {
            assertTrue(runCatching { repository.importFromJson(payload, overlay = true) }.isFailure)
            assertEquals(listOf(existing), repository.getAllHistory().first())
        }
    }

    @Test fun failedInsertRollsBackTheDelete() = runBlocking {
        val existing = HistoryEntity("bilibili_1", "1", "bilibili", "existing", "", 100L)
        repository.addHistory(existing)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_history BEFORE INSERT ON histories " +
                "WHEN NEW.roomId = '2' BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        assertTrue(runCatching {
            repository.importFromJson("""[{"siteId":"bilibili","roomId":"2"}]""", true)
        }.isFailure)
        assertEquals(listOf(existing), repository.getAllHistory().first())
    }

    @Test fun mergeKeepsNewestVersionAndSuccessfulOverlayReplaces() = runBlocking {
        repository.addHistory(HistoryEntity("bilibili_1", "1", "bilibili", "newer", "", 100L))
        repository.importFromJson("""[{"site":"bilibili","room":"1","updateTime":10}]""", false)
        assertEquals("newer", repository.getHistoryById("bilibili_1")?.userName)
        repository.importFromJson("""[{"siteId":"douyin","roomId":"2","userName":"replacement"}]""", true)
        assertEquals(listOf("douyin_2"), repository.getAllHistory().first().map { it.id })
        repository.importFromJson("[]", true)
        assertTrue(repository.getAllHistory().first().isEmpty())
    }
}
