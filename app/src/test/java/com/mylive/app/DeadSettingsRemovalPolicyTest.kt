package com.mylive.app

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class DeadSettingsRemovalPolicyTest {

    @Test
    fun unownedLegacySettingsAreRemovedFromStorageAndBackupMetadata() {
        val sources = listOf(
            File("src/main/java/com/mylive/app/data/local/datastore/SettingsDataStore.kt").readText(),
            File("src/main/java/com/mylive/app/data/repository/ProfileBackupManager.kt").readText()
        ).joinToString("\n")
        val deadKeys = listOf(
            "AudioOutputDriver",
            "BilibiliLoginTip",
            "CustomPlayerOutput",
            "FirstRun",
            "LastLiveRoom",
            "LastLiveRoomResumePending",
            "LiveSubtitleBackgroundEnable",
            "LiveSubtitleColor",
            "LiveSubtitleEnable",
            "LiveSubtitleFontSize",
            "LiveSubtitleFontWeight",
            "LiveSubtitleLanguage",
            "LiveSubtitleModelPath",
            "LiveSubtitleOffsetX",
            "LiveSubtitleOffsetY",
            "LiveSubtitlePosition",
            "LiveSubtitlePositionLocked",
            "LiveSubtitleStartupGuard",
            "PIPHideDanmuDefaultMigrated",
            "PlayerBufferSize",
            "PlayerShowSuperChat",
            "PlayerVolume",
            "VideoHardwareDecoder",
            "VideoOutputDriver"
        )

        deadKeys.forEach { key ->
            assertFalse("$key is still declared or exported", sources.contains(key))
        }
    }
}
