package com.mylive.app.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.sortedByDefaultOrder
import com.mylive.app.core.site.sortedByUserOrder
import com.mylive.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val siteId: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val rooms: List<LiveRoomItem> = emptyList(),
    val error: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sites: Set<@JvmSuppressWildcards LiveSite>,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Platform tabs: sorted by user preference
    val siteTabs: StateFlow<List<LiveSite>> = settingsRepository.siteSort
        .combine(kotlinx.coroutines.flow.flowOf(sites)) { sortStr, siteSet ->
            siteSet.sortedByUserOrder(sortStr)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sites.sortedByDefaultOrder())

    private var currentSiteIndex = 0
    private var loadJob: Job? = null

    fun selectSite(index: Int) {
        currentSiteIndex = index
        val site = siteTabs.value.getOrNull(currentSiteIndex) ?: return
        val cachedState = HomeStateCache.get(site.id)
        _uiState.value = cachedState?.copy(
            siteId = site.id,
            isLoading = true,
            currentPage = 1,
            error = null
        ) ?: HomeUiState(siteId = site.id, isLoading = true)
        loadRecommendRooms()
    }

    fun loadMore() {
        if (!_uiState.value.hasMore || _uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(currentPage = _uiState.value.currentPage + 1)
        loadRecommendRooms()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(
            isRefreshing = true,
            currentPage = 1,
            error = null
        )
        loadRecommendRooms(isRefresh = true)
    }

    private fun loadRecommendRooms(isRefresh: Boolean = false) {
        val site = siteTabs.value.getOrNull(currentSiteIndex) ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                siteId = site.id,
                isLoading = !isRefresh,
                error = null
            )
            try {
                val result = site.getRecommendRooms(page = _uiState.value.currentPage)
                _uiState.value = _uiState.value.copy(
                    siteId = site.id,
                    isLoading = false,
                    isRefreshing = false,
                    // Recommend feeds frequently repeat the same room across pages. De-dupe by
                    // roomId when appending so the LazyVerticalGrid never receives a duplicate
                    // key (which throws IllegalArgumentException and crashes the home screen).
                    rooms = if (_uiState.value.currentPage == 1) {
                        result.items
                    } else {
                        (_uiState.value.rooms + result.items).distinctBy { it.roomId }
                    },
                    hasMore = result.hasMore
                )
                HomeStateCache.put(site.id, _uiState.value)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    siteId = site.id,
                    isLoading = false,
                    isRefreshing = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    init {
        viewModelScope.launch {
            siteTabs.collect { tabs ->
                if (tabs.isNotEmpty()) {
                    val currentSite = tabs.getOrNull(currentSiteIndex)
                    if (currentSite != null && _uiState.value.siteId != currentSite.id) {
                        val restoredState = HomeStateCache.get(currentSite.id)
                        if (restoredState != null) {
                            _uiState.value = restoredState
                        } else {
                            selectSite(currentSiteIndex)
                        }
                    }
                }
            }
        }
    }
}

internal object HomeStateCache {
    private val states = mutableMapOf<String, HomeUiState>()

    fun get(siteId: String): HomeUiState? {
        return states[siteId]?.copy(
            isLoading = false,
            isRefreshing = false,
            error = null
        )
    }

    fun put(siteId: String, state: HomeUiState) {
        if (state.rooms.isNotEmpty()) {
            states[siteId] = state.copy(
                siteId = siteId,
                isLoading = false,
                isRefreshing = false,
                error = null
            )
        }
    }

    fun clear() {
        states.clear()
    }
}
