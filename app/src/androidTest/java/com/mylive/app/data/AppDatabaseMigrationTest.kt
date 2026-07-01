package com.mylive.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylive.app.data.local.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Exercises the real Room schema migrations against the exported schema JSON in app/schemas/.
 *
 * The existing [AppDatabaseTest] only builds an in-memory database (which never runs migrations),
 * so an incorrect migration would ship undetected. This is the CI gate for every schema bump: if a
 * future version changes the schema without a matching migration, [runMigrationsAndValidate] fails.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        // Create the database at version 1 using the exported 1.json schema.
        helper.createDatabase(TEST_DB, 1).close()

        // Apply the real migration and validate the resulting schema matches 2.json.
        helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2).close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        helper.createDatabase(TEST_DB_2_3, 2).close()

        helper.runMigrationsAndValidate(TEST_DB_2_3, 3, true, AppDatabase.MIGRATION_2_3).close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To3() {
        helper.createDatabase(TEST_DB_1_3, 1).close()

        helper.runMigrationsAndValidate(
            TEST_DB_1_3,
            3,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3
        ).close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4() {
        helper.createDatabase(TEST_DB_3_4, 3).close()

        helper.runMigrationsAndValidate(TEST_DB_3_4, 4, true, AppDatabase.MIGRATION_3_4).close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To4() {
        helper.createDatabase(TEST_DB_1_4, 1).close()

        helper.runMigrationsAndValidate(
            TEST_DB_1_4,
            4,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4
        ).close()
    }

    private companion object {
        const val TEST_DB = "migration-test-1-2"
        const val TEST_DB_2_3 = "migration-test-2-3"
        const val TEST_DB_1_3 = "migration-test-1-3"
        const val TEST_DB_3_4 = "migration-test-3-4"
        const val TEST_DB_1_4 = "migration-test-1-4"
    }
}
