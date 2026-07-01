package com.mylive.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProfileBackupPerformancePolicyTest {

    @Test
    fun profileSettingsUseOnePreferencesSnapshotAndOneEditTransaction() {
        val source = File("src/main/java/com/mylive/app/data/repository/ProfileBackupManager.kt")
            .readText()

        assertTrue(source.contains("val preferences = settingsDataStore.preferencesFlow().first()"))
        assertTrue(source.contains("settingsDataStore.edit { preferences ->"))
        assertFalse(source.contains("settingsDataStore.getFlow(key"))
        assertFalse(source.contains("settingsDataStore.setValue(key"))
    }

    @Test
    fun profileCollectionsAreInsertedInBatches() {
        val source = File("src/main/java/com/mylive/app/data/repository/ProfileBackupManager.kt")
            .readText()

        assertTrue(source.contains("followRepository.addFollows(follows)"))
        assertTrue(source.contains("followRepository.addTags(tags)"))
        assertTrue(source.contains("historyRepository.addHistories(histories)"))
        assertTrue(source.contains("shieldRepository.addShields(shields)"))
        assertTrue(source.contains("shieldRepository.addPresets(presets)"))
    }
}
