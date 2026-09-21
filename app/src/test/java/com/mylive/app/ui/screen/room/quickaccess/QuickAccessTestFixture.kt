package com.mylive.app.ui.screen.room.quickaccess

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.mylive.app.core.model.LiveCategoryResult
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.site.LiveSite
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.repository.*
import com.mylive.app.service.FollowStatusRefreshCoordinator
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal class QuickAccessTestFixture {
    val follows = MutableStateFlow<List<FollowUserEntity>>(emptyList())
    val histories = MutableStateFlow<List<HistoryEntity>>(emptyList())
    val tags = MutableStateFlow<List<FollowUserTagEntity>>(emptyList())
    val remarks = MutableStateFlow<Map<String, String>>(emptyMap())
    val sort = MutableStateFlow("follow,history,recommendation")
    val followRepository = mockk<FollowRepository> {
        every { getAllFollows() } returns follows
        every { getAllTags() } returns tags
    }
    val historyRepository = mockk<HistoryRepository> {
        every { getAllHistory() } returns histories
        coEvery { removeHistory(any()) } coAnswers {
            histories.value = histories.value.filterNot { it.id == firstArg<String>() }
        }
    }
    val site = mockk<LiveSite> {
        every { id } returns "bilibili"
        coEvery { getCategoryRooms(any(), any()) } returns LiveCategoryResult(false, listOf(room("1")))
        coEvery { getRecommendRooms(any()) } returns LiveCategoryResult(false, listOf(room("1")))
    }
    val coordinator = mockk<FollowStatusRefreshCoordinator> {
        every { isRefreshing } returns MutableStateFlow(false)
        coEvery { refreshAll() } returns emptyList()
    }
    val saved = SavedStateHandle()
    val vm = QuickAccessViewModel(followRepository, historyRepository, mockk<SettingsRepository> {
        every { liveRoomQuickAccessSort } returns sort
        every { liveRoomQuickAccessEnabled } returns flowOf(true)
    }, setOf(site), coordinator, mockk<UserRemarkRepository> {
        every { observeAllRemarks() } returns remarks
    }, saved)
    private val store = ViewModelStore().apply { put("quick", vm) }
    fun close() { store.clear() }
    companion object {
        fun room(id: String) = LiveRoomItem(id, "room $id", "", "anchor $id")
    }
}
