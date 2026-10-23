package com.mylive.app.ui.screen.settings

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.RoomDatabase
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.local.entity.ShieldPresetEntity
import com.mylive.app.testing.ReviewRoomFixture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShieldImportAtomicityTest : ReviewRoomFixture() {
    private val pauseFirstInsert = AtomicBoolean(false)
    private val inserting = CountDownLatch(1)
    private val resumeInsert = CountDownLatch(1)
    private val rejectedPresetInsertReached = AtomicBoolean(false)

    override fun configureDatabase(builder: RoomDatabase.Builder<AppDatabase>) = builder.setQueryCallback(
        object : RoomDatabase.QueryCallback {
            override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                if (sqlQuery.trimStart().startsWith("INSERT", ignoreCase = true) &&
                    "shield_presets" in sqlQuery && "rejected" in bindArgs) {
                    rejectedPresetInsertReached.set(true)
                }
                if (sqlQuery.trimStart().startsWith("INSERT", ignoreCase = true) &&
                    "keyword:first" in bindArgs && pauseFirstInsert.compareAndSet(true, false)) {
                    inserting.countDown()
                    check(resumeInsert.await(10, TimeUnit.SECONDS)) { "Timed out releasing test DB insert" }
                }
            }
        },
        Executor { it.run() }
    )

    @Test
    fun wrongTypeCurrentKeywordsRejectsTheEntireImportAndPreservesExistingData() = runBlocking {
        addOriginalRules()
        shields.addPreset(ShieldPresetEntity("existing", """{"keywords":["original"]}"""))
        val originalRules = shields.getAllShields().first()
        val originalPresets = shields.getAllPresets().first()
        val viewModel = own(ShieldSettingsViewModel(shields, settings))

        val accepted = viewModel.importPresetJson(
            """{"presets":[{"name":"must-not-import","keywords":["new"]}],"current":{"keywords":"wrong-type"}}"""
        )
        awaitWork(viewModel)

        assertEquals("Invalid types must not clear current keyword/user rules", originalRules,
            shields.getAllShields().first())
        assertEquals("Rejecting the import must also preserve presets", originalPresets,
            shields.getAllPresets().first())
        assertFalse("The UI must receive an import failure", accepted)
    }

    @Test
    fun explicitlyEmptyValidArraysCanClearCurrentRules() = runBlocking {
        addOriginalRules()
        val viewModel = own(ShieldSettingsViewModel(shields, settings))
        val accepted = viewModel.importPresetJson(
            """{"current":{"keywords":[],"users":[],"userGroups":{}}}"""
        )
        awaitWork(viewModel)
        assertTrue(accepted)
        assertTrue(shields.getAllShields().first().isEmpty())
    }

    @Test
    fun invalidNestedRulesOrPresetsRejectAllWritesBeforeTheImportStarts() = runBlocking {
        addOriginalRules()
        shields.addPreset(ShieldPresetEntity("existing", """{"keywords":["original"]}"""))
        val originalRules = shields.getAllShields().first()
        val originalPresets = shields.getAllPresets().first()
        val viewModel = own(ShieldSettingsViewModel(shields, settings))
        val invalidPayloads = listOf(
            """{"current":{"keywords":["replacement",42]}}""",
            """{"current":{"users":[null]}}""",
            """{"current":{"userGroups":[]}}""",
            """{"current":null}""",
            """{"presets":{"name":"wrong-container"}}""",
            """{"presets":[{"name":123,"keywords":[]}]}""",
            """{"current":{"keywords":["replacement"]},"presets":[{"name":"valid-first","keywords":["new"]},{"name":"invalid-later","userGroups":{"douyu":"wrong-type"}}]}"""
        )

        for (payload in invalidPayloads) {
            assertFalse("Invalid configuration must be rejected: $payload", viewModel.importPresetJson(payload))
            awaitWork(viewModel)
            assertEquals("Rules changed for invalid payload: $payload", originalRules,
                shields.getAllShields().first())
            assertEquals("Presets changed for invalid payload: $payload", originalPresets,
                shields.getAllPresets().first())
        }
    }

    @Test
    fun failureInAPresetInsertRollsBackCurrentRulesAndEveryPreset() = runBlocking {
        addOriginalRules()
        shields.addPreset(ShieldPresetEntity("existing", """{"keywords":["original"]}"""))
        val originalRules = shields.getAllShields().first()
        val originalPresets = shields.getAllPresets().first()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_shield_preset BEFORE INSERT ON shield_presets " +
                "WHEN NEW.name = 'rejected' BEGIN SELECT RAISE(ABORT, 'test preset failure'); END"
        )

        val result = runCatching {
            shields.importConfiguration(
                listOf(ShieldEntity(value = "keyword:new"), ShieldEntity(value = "user:douyu:new-user")),
                listOf(ShieldPresetEntity("accepted-first", "{}"), ShieldPresetEntity("rejected", "{}"))
            )
        }

        assertTrue("The failing SQLite insert must be reached", result.isFailure)
        val failure = result.exceptionOrNull()!!
        assertTrue("Expected the rejected preset insert, actual=${failure.stackTraceToString()}",
            rejectedPresetInsertReached.get())
        assertTrue("Expected a SQLite failure, actual=${failure.stackTraceToString()}",
            generateSequence(failure) { it.cause }.any { it is SQLiteException })
        assertEquals("The failed transaction must restore the original row IDs and values", originalRules,
            shields.getAllShields().first())
        assertEquals("Earlier preset inserts must be rolled back too", originalPresets,
            shields.getAllPresets().first())
    }

    @Test
    fun validImportReplacesCurrentRulesAndKeepsExistingPresetsAlongsideNewOnes() = runBlocking {
        addOriginalRules()
        val originalPreset = ShieldPresetEntity("existing", """{"keywords":["original"]}""")
        shields.addPreset(originalPreset)
        val viewModel = own(ShieldSettingsViewModel(shields, settings))

        assertTrue(viewModel.importPresetJson(
            """{"current":{"keywords":["new"],"users":["global"],"userGroups":{"douyu":["anchor"]}},"presets":[{"name":"new-preset","keywords":["saved"]}]}"""
        ))
        awaitWork(viewModel)

        assertEquals(setOf("keyword:new", "user:__all__:global", "user:douyu:anchor"),
            shields.getAllShields().first().map { it.value }.toSet())
        val presets = shields.getAllPresets().first()
        assertEquals(setOf("existing", "new-preset"), presets.map { it.name }.toSet())
        assertEquals(originalPreset, presets.single { it.name == "existing" })
    }

    @Test
    fun cancellingImportCannotCommitOnlyPartOfTheReplacement() = runBlocking {
        assertCancellationIsAtomic(usePreset = false)
    }

    @Test
    fun cancellingPresetCannotCommitOnlyPartOfTheReplacement() = runBlocking {
        assertCancellationIsAtomic(usePreset = true)
    }

    private suspend fun addOriginalRules() {
        shields.addShield(ShieldEntity(value = "keyword:original"))
        shields.addShield(ShieldEntity(value = "user:__all__:original-user"))
    }

    private suspend fun assertCancellationIsAtomic(usePreset: Boolean) {
        addOriginalRules()
        val original = shields.getAllShields().first().map { it.value }.toSet()
        val completeReplacement = setOf("keyword:first", "keyword:second", "user:__all__:new-user")
        val viewModel = ShieldSettingsViewModel(shields, settings)
        val store = ViewModelStore().also { it.put("screen", viewModel) }
        val scopeJob = viewModel.viewModelScope.coroutineContext[Job]!!
        pauseFirstInsert.set(true)
        try {
            val current = """{"keywords":["first","second"],"users":["new-user"]}"""
            if (usePreset) viewModel.applyPreset(ShieldPresetEntity("replacement", current))
            else viewModel.importPresetJson("""{"current":$current}""")

            assertTrue("The replacement must reach a real SQLite insert", withContext(Dispatchers.IO) {
                inserting.await(5, TimeUnit.SECONDS)
            })
            // A real lifecycle cancellation at a controlled SQLite execution boundary.
            store.clear()
        } finally {
            resumeInsert.countDown()
            store.clear()
        }
        withTimeout(10_000) { scopeJob.join() }
        val actual = shields.getAllShields().first().map { it.value }.toSet()
        assertTrue("After cancellation, rules must be wholly old or wholly new; actual=$actual",
            actual == original || actual == completeReplacement)
    }
}
