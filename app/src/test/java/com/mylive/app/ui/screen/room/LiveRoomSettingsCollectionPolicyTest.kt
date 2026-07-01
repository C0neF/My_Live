package com.mylive.app.ui.screen.room

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveRoomSettingsCollectionPolicyTest {

    @Test
    fun liveRoomCollectsOneLifecycleAwareSettingsSnapshot() {
        val screen = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val viewModel = File("src/main/java/com/mylive/app/ui/screen/settings/SettingsViewModel.kt").readText()

        assertTrue(viewModel.contains("val liveRoomPreferences: StateFlow<LiveRoomPreferences>"))
        assertTrue(screen.contains("liveRoomPreferences.collectAsStateWithLifecycle()"))
        assertFalse(screen.contains(".collectAsState("))
        assertFalse(
            Regex("settingsViewModel\\.(?!liveRoomPreferences)\\w+\\.collectAsStateWithLifecycle")
                .containsMatchIn(screen)
        )
    }
}
