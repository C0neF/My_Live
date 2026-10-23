package com.mylive.app.ui.screen.settings

import android.app.Application
import androidx.room.RoomDatabase
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.local.entity.ShieldPresetEntity
import com.mylive.app.testing.ReviewRoomFixture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual ViewModel/Room completion and cancellation; independent of the host's dialog renderer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ShieldImportCompletionTest : ReviewRoomFixture() {
    private val pauseInsert = AtomicBoolean(false)
    private val inserting = CountDownLatch(1)
    private val resumeInsert = CountDownLatch(1)
    private val rejectedPresetInsertReached = AtomicBoolean(false)
    private val ruleWrites = AtomicInteger()

    override fun configureDatabase(builder: RoomDatabase.Builder<AppDatabase>) = builder.setQueryCallback(
        object : RoomDatabase.QueryCallback {
            override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                val sql = sqlQuery.trimStart()
                if ((sql.startsWith("INSERT", ignoreCase = true) ||
                    sql.startsWith("UPDATE", ignoreCase = true) ||
                    sql.startsWith("DELETE", ignoreCase = true)) &&
                    ("shields" in sql || "shield_presets" in sql)) {
                    ruleWrites.incrementAndGet()
                }
                if (sql.startsWith("INSERT", ignoreCase = true) &&
                    "shield_presets" in sql && "rejected" in bindArgs) {
                    rejectedPresetInsertReached.set(true)
                }
                if (sql.startsWith("INSERT", ignoreCase = true) &&
                    "keyword:first" in bindArgs && pauseInsert.compareAndSet(true, false)) {
                    inserting.countDown()
                    check(resumeInsert.await(10, TimeUnit.SECONDS)) { "Timed out releasing the test insert" }
                }
            }
        },
        Executor { it.run() }
    )

    @Test fun awaitedImportCannotReportSuccessBeforeRulesAndPresetsCommit() = runBlocking {
        addOriginalConfiguration()
        val viewModel = own(ShieldSettingsViewModel(shields, settings))
        pauseInsert.set(true)
        val result = async(Dispatchers.Default) { viewModel.importPresetJsonAndAwait(REPLACEMENT) }
        try {
            assertTrue("A real transaction must reach the controlled insert", withContext(Dispatchers.IO) {
                inserting.await(5, TimeUnit.SECONDS)
            })
            assertFalse("Persistence is still blocked; success must not be returned", result.isCompleted)
        } finally {
            resumeInsert.countDown()
        }

        assertTrue(withTimeout(10_000) { result.await() })
        assertEquals(REPLACEMENT_VALUES, shields.getAllShields().first().map { it.value }.toSet())
        assertEquals(setOf("existing", "new-preset"), shields.getAllPresets().first().map { it.name }.toSet())
    }

    @Test fun awaitedImportReportsDatabaseFailureAndRollsBackBothCollections() = runBlocking {
        addOriginalConfiguration()
        val originalRules = shields.getAllShields().first()
        val originalPresets = shields.getAllPresets().first()
        val viewModel = own(ShieldSettingsViewModel(shields, settings))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_shield_completion BEFORE INSERT ON shield_presets " +
                "WHEN NEW.name = 'rejected' BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )

        val success = viewModel.importPresetJsonAndAwait(
            """{"current":{"keywords":["new"]},"presets":[{"name":"accepted-first","keywords":[]},{"name":"rejected","keywords":[]}]}"""
        )

        assertTrue("The failing SQLite insert must actually execute", rejectedPresetInsertReached.get())
        assertFalse("A failed database transaction must not report a successful import", success)
        assertEquals(originalRules, shields.getAllShields().first())
        assertEquals(originalPresets, shields.getAllPresets().first())
    }

    @Test fun invalidAwaitedImportReturnsFailureWithoutWritingAnyRulesOrPresets() = runBlocking {
        addOriginalConfiguration()
        val originalRules = shields.getAllShields().first()
        val originalPresets = shields.getAllPresets().first()
        val writesBeforeImport = ruleWrites.get()
        val viewModel = own(ShieldSettingsViewModel(shields, settings))

        assertFalse(viewModel.importPresetJsonAndAwait(
            """{"current":{"keywords":["must-not-replace"]},"presets":[{"name":"valid-first","keywords":[]},{"name":"invalid-later","users":false}]}"""
        ))

        assertEquals("Validation must finish before any mutation starts", writesBeforeImport, ruleWrites.get())
        assertEquals(originalRules, shields.getAllShields().first())
        assertEquals(originalPresets, shields.getAllPresets().first())
    }

    @Test fun cancellingAnAwaitedImportPropagatesCancellationWithoutAPartialConfiguration() = runBlocking {
        addOriginalConfiguration()
        val originalValues = shields.getAllShields().first().map { it.value }.toSet()
        val originalPresets = shields.getAllPresets().first()
        val viewModel = own(ShieldSettingsViewModel(shields, settings))
        pauseInsert.set(true)
        val result = async(Dispatchers.Default) { viewModel.importPresetJsonAndAwait(REPLACEMENT) }
        try {
            assertTrue("The import must be cancelled during its real transaction", withContext(Dispatchers.IO) {
                inserting.await(5, TimeUnit.SECONDS)
            })
            result.cancel(CancellationException("Screen disposed"))
        } finally {
            resumeInsert.countDown()
        }
        withTimeout(10_000) { result.join() }
        assertTrue("The caller must receive cancellation rather than success or failure", result.isCancelled)
        assertTrue(runCatching { result.await() }.exceptionOrNull() is CancellationException)

        val actualValues = shields.getAllShields().first().map { it.value }.toSet()
        val actualPresets = shields.getAllPresets().first()
        assertTrue("Cancellation may leave a whole old or whole new transaction", actualValues == originalValues ||
            actualValues == REPLACEMENT_VALUES)
        if (actualValues == originalValues) {
            assertEquals(originalPresets, actualPresets)
        } else {
            assertEquals(setOf("existing", "new-preset"), actualPresets.map { it.name }.toSet())
        }
    }

    private suspend fun addOriginalConfiguration() {
        shields.addShield(ShieldEntity(value = "keyword:original"))
        shields.addShield(ShieldEntity(value = "user:__all__:original-user"))
        shields.addPreset(ShieldPresetEntity("existing", """{"keywords":["original"]}"""))
    }

    private companion object {
        const val REPLACEMENT = """{"current":{"keywords":["first","second"],"users":["new-user"]},"presets":[{"name":"new-preset","keywords":["saved"]}]}"""
        val REPLACEMENT_VALUES = setOf("keyword:first", "keyword:second", "user:__all__:new-user")
    }
}
