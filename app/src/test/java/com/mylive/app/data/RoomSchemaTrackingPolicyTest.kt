package com.mylive.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoomSchemaTrackingPolicyTest {

    @Test
    fun roomSchemasAreNotIgnoredByGitignore() {
        val gitignore = File("../.gitignore").readLines()

        assertFalse(
            "Room migration tests need app/schemas JSON files to be versioned",
            gitignore.any { it.trim() == "/app/schemas/" || it.trim() == "app/schemas/" }
        )
    }

    @Test
    fun exportedAppDatabaseSchemasExistForHistoricalVersions() {
        val schemaDir = File("schemas/com.mylive.app.data.local.AppDatabase")

        assertTrue(File(schemaDir, "1.json").isFile)
        assertTrue(File(schemaDir, "2.json").isFile)
        assertTrue(File(schemaDir, "3.json").isFile)
    }
}
