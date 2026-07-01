package com.mylive.app.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiLifecycleCollectionPolicyTest {

    @Test
    fun mainSourceDoesNotUsePlainFlowCollectAsState() {
        File("src/main/java/com/mylive/app")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val source = file.readText()

                assertFalse(
                    "${file.path} should not import plain collectAsState for Flow/StateFlow",
                    source.contains("import androidx.compose.runtime.collectAsState")
                )
                assertFalse(
                    "${file.path} should not use plain collectAsState for Flow/StateFlow",
                    source.contains(".collectAsState(")
                )
            }
    }

    @Test
    fun criticalScreensUseLifecycleAwareFlowCollection() {
        listOf(
            "src/main/java/com/mylive/app/MainActivity.kt",
            "src/main/java/com/mylive/app/ui/screen/home/HomeScreen.kt",
            "src/main/java/com/mylive/app/ui/screen/search/SearchScreen.kt",
            "src/main/java/com/mylive/app/ui/screen/category/CategoryScreen.kt",
            "src/main/java/com/mylive/app/ui/screen/category/CategoryDetailScreen.kt",
            "src/main/java/com/mylive/app/ui/screen/follow/FollowScreen.kt",
            "src/main/java/com/mylive/app/ui/screen/settings/ShieldSettingsScreen.kt",
            "src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt"
        ).forEach { path ->
            val source = File(path).readText()

            assertTrue(
                "$path must import collectAsStateWithLifecycle",
                source.contains("import androidx.lifecycle.compose.collectAsStateWithLifecycle")
            )
            assertTrue(
                "$path must use collectAsStateWithLifecycle",
                source.contains(".collectAsStateWithLifecycle(")
            )
            assertFalse(
                "$path should not use plain collectAsState for Flow/StateFlow",
                source.contains(".collectAsState(")
            )
        }
    }
}
