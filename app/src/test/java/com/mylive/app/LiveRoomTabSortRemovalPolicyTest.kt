package com.mylive.app

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class LiveRoomTabSortRemovalPolicyTest {

    @Test
    fun liveRoomTabSortIsRemovedFromSettingsStorageAndBackup() {
        val files = listOf(
            "src/main/java/com/mylive/app/data/local/datastore/SettingsDataStore.kt",
            "src/main/java/com/mylive/app/data/repository/ProfileBackupManager.kt",
            "src/main/java/com/mylive/app/data/repository/SettingsRepository.kt",
            "src/main/java/com/mylive/app/ui/screen/settings/SettingsViewModel.kt",
            "src/main/java/com/mylive/app/ui/screen/settings/PlaybackPageSettingsScreen.kt"
        )

        files.forEach { path ->
            val source = File(path).readText()
            assertFalse("$path still contains LiveRoomTabSort", source.contains("LiveRoomTabSort"))
            assertFalse("$path still contains liveRoomTabSort", source.contains("liveRoomTabSort"))
            assertFalse("$path still contains setLiveRoomTabSort", source.contains("setLiveRoomTabSort"))
            assertFalse("$path still contains the obsolete UI", source.contains("播放页标签顺序"))
        }
    }
}
