package com.mylive.app.ui.screen.room.quickaccess

import android.app.Application
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.ui.theme.Icons
import io.mockk.coVerify
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class QuickAccessInteractionTest {
    // Robolectric 4.11.1 does not route injected touches into ModalBottomSheet windows.
    // Accessible UI actions are tested here; physical gestures use an Android target.
    @get:Rule val compose = createComposeRule()
    private val f = QuickAccessTestFixture()
    private var dismissals = 0
    private val navigations = mutableListOf<String>()
    @After fun close() { f.close() }

    private fun show(initialKey: String? = null, extra: List<QuickAccessExtraTab> = emptyList()) {
        compose.setContent {
            MaterialTheme {
                var visible by remember { mutableStateOf(true) }
                Button(onClick = { visible = true }) { Text("打开入口") }
                if (visible) QuickAccessPanel("bilibili", "short", "a", extra,
                    initialSelectedKey = initialKey, currentResolvedRoomId = "999",
                    onNavigateToRoom = { site, room, _ -> navigations.add("$site/$room"); visible = false },
                    onDismiss = { dismissals++; visible = false }, viewModel = f.vm)
            }
        }
        compose.waitForIdle()
    }

    @Test fun selectingCurrentAliasDismissesWithoutRestartingPlayback() {
        f.follows.value = listOf(FollowUserEntity("bilibili_999", "999", "bilibili", "当前主播", "", 0, liveStatus = 1))
        show()
        compose.onNodeWithText("当前观看").assertIsDisplayed()
        compose.onNodeWithTag("quick_access_room_当前主播").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        assertEquals(1, dismissals)
        assertTrue(navigations.isEmpty())
    }

    @Test fun searchMatchesSavedRemarkAndResetRestoresList() {
        f.follows.value = listOf(
            FollowUserEntity("bilibili_1", "1", "bilibili", "Alpha", "", 0, liveStatus = 1),
            FollowUserEntity("douyin_2", "2", "douyin", "Beta", "", 0, liveStatus = 0)
        )
        f.remarks.value = mapOf("bilibili::Alpha" to "Teacher")
        show()
        compose.onNodeWithTag("quick_access_search_follow").performTextInput("Teacher")
        compose.onNodeWithText("1 位主播").assertIsDisplayed()
        compose.onNodeWithTag("quick_access_room_Teacher").assertIsDisplayed()
        compose.onNodeWithText("重置筛选").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("2 位主播").assertIsDisplayed()
        compose.onNodeWithTag("quick_access_status_Unknown").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("quick_access_room_Beta").assertIsDisplayed()
        compose.onNodeWithText("1 位主播").assertIsDisplayed()
    }

    @Test fun historyDeletionRequiresConfirmationAndCancellationKeepsTheEntry() {
        f.histories.value = listOf(HistoryEntity("bilibili_1", "1", "bilibili", "HistoryUser", "", System.currentTimeMillis()))
        show("history")
        compose.onNodeWithContentDescription("管理HistoryUser的观看记录").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("取消").performSemanticsAction(SemanticsActions.OnClick) { it() }
        coVerify(exactly = 0) { f.historyRepository.removeHistory(any()) }
        compose.onNodeWithContentDescription("管理HistoryUser的观看记录").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("删除").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        coVerify(exactly = 1) { f.historyRepository.removeHistory("bilibili_1") }
        compose.onNodeWithText("还没有观看记录").assertIsDisplayed()
    }

    @Test fun reopeningKeepsLastTabAndExplicitExtraTabTakesPrecedence() {
        show()
        compose.onNodeWithTag("quick_access_tab_history").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        compose.onNodeWithTag("quick_access_close").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("打开入口").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        compose.onNodeWithTag("quick_access_tab_history").assertIsSelected()
    }

    @Test fun reopeningRestoresHistoryScrollPosition() {
        val timestamp = java.time.Instant.parse("2026-09-19T12:00:00Z").toEpochMilli()
        f.histories.value = (0 until 30).map { index ->
            HistoryEntity("bilibili_$index", "$index", "bilibili", "History$index", "", timestamp - index * 60_000L)
        }
        show("history")
        compose.onNodeWithTag("quick_access_history_list").performScrollToIndex(14)
        compose.onNodeWithText("History14").assertIsDisplayed()
        compose.onNodeWithTag("quick_access_close").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        assertTrue(f.vm.scrollPosition("history").first > 0)
        compose.onNodeWithText("打开入口").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        compose.onNodeWithText("History14").assertIsDisplayed()
    }
    @Test fun explicitTabWinsOverRememberedTab() {
        f.vm.selectTab("history")
        show("room_settings", listOf(QuickAccessExtraTab("room_settings", "设置", Icons.Default.Settings) { Text("RoomSettingsContent") }))
        compose.onNodeWithTag("quick_access_tab_room_settings").assertIsSelected()
        compose.onNodeWithText("RoomSettingsContent").assertIsDisplayed()
    }
}
