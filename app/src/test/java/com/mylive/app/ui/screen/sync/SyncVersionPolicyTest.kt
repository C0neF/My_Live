package com.mylive.app.ui.screen.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SyncVersionPolicyTest {

    @Test
    fun syncDeviceInfoUsesBuildConfigVersionName() {
        val lanSource = File("src/main/java/com/mylive/app/service/LanSyncService.kt").readText()
        val remoteSource = File(
            "src/main/java/com/mylive/app/ui/screen/sync/RemoteSyncRoomViewModel.kt"
        ).readText()

        assertTrue(lanSource.contains("BuildConfig.VERSION_NAME"))
        assertTrue(remoteSource.contains("BuildConfig.VERSION_NAME"))
        assertFalse(lanSource.contains("""put("version", "1.0")"""))
        assertFalse(remoteSource.contains("""put("version", "1.0")"""))
    }
}
