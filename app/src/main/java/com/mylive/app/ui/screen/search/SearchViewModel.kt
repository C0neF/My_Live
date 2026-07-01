package com.mylive.app.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.model.LiveAnchorItem
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.preserveSelectedSiteIndex
import com.mylive.app.core.site.sortedByDefaultOrder
import com.mylive.app.core.site.sortedByUserOrder
import com.mylive.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SearchUiState(
    val isLoading: Boolean = false,
    val rooms: List<LiveRoomItem> = emptyList(),
    val anchors: List<LiveAnchorItem> = emptyList(),
    val error: String? = null,
    val searchType: Int = 0, // 0=rooms, 1=anchors
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val selectedSiteIndex: Int = 0
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val sites: Set<@JvmSuppressWildcards LiveSite>,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val siteTabs: StateFlow<List<LiveSite>> = settingsRepository.siteSort
        .map { sortStr -> sites.sortedByUserOrder(sortStr) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            sites.sortedByDefaultOrder()
        )
    private var currentKeyword = ""
    private var activeSearchRequestId = 0L
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var previousSiteIds = sites.sortedByDefaultOrder().map { it.id }

    init {
        viewModelScope.launch {
            siteTabs.collect { reorderedSites ->
                val reorderedSiteIds = reorderedSites.map { it.id }
                val state = _uiState.value
                val previousSelectedSiteId = previousSiteIds.getOrNull(state.selectedSiteIndex)
                val nextIndex = preserveSelectedSiteIndex(
                    previousSiteIds = previousSiteIds,
                    reorderedSiteIds = reorderedSiteIds,
                    selectedIndex = state.selectedSiteIndex
                )
                val selectedSiteChanged =
                    previousSelectedSiteId != reorderedSiteIds.getOrNull(nextIndex)
                val selectedIndexChanged = nextIndex != state.selectedSiteIndex
                previousSiteIds = reorderedSiteIds
                if (selectedIndexChanged) {
                    _uiState.value = state.copy(selectedSiteIndex = nextIndex)
                }
                if ((selectedSiteChanged || selectedIndexChanged) && currentKeyword.isNotEmpty()) {
                    search(currentKeyword)
                }
            }
        }
    }

    fun selectSite(index: Int) {
        val tabs = siteTabs.value
        val nextIndex = if (tabs.isEmpty()) {
            0
        } else {
            index.coerceIn(0, tabs.lastIndex)
        }
        _uiState.value = _uiState.value.copy(selectedSiteIndex = nextIndex)
        if (currentKeyword.isNotEmpty()) {
            search(currentKeyword)
        }
    }

    fun setSearchType(type: Int) {
        val nextType = if (type == 1) 1 else 0
        _uiState.value = _uiState.value.copy(searchType = nextType)
        if (currentKeyword.isNotEmpty()) {
            search(currentKeyword)
        }
    }

    fun search(keyword: String) {
        val nextKeyword = keyword.trim()
        if (nextKeyword.isEmpty()) {
            clearSearch()
            return
        }

        currentKeyword = nextKeyword
        val state = _uiState.value
        val siteIndex = state.selectedSiteIndex
        val searchType = state.searchType
        val site = siteTabs.value.getOrNull(siteIndex) ?: return
        val requestId = nextSearchRequestId()

        searchJob?.cancel()
        loadMoreJob?.cancel()
        _uiState.value = searchUiStateForNewSearch(state)

        searchJob = viewModelScope.launch {
            try {
                if (searchType == 0) {
                    val result = site.searchRooms(nextKeyword)
                    if (!isCurrentSearchRequest(requestId, siteIndex, searchType, nextKeyword)) return@launch
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        rooms = result.items,
                        anchors = emptyList(),
                        currentPage = 1,
                        hasMore = result.hasMore
                    )
                } else {
                    val result = site.searchAnchors(nextKeyword)
                    if (!isCurrentSearchRequest(requestId, siteIndex, searchType, nextKeyword)) return@launch
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        rooms = emptyList(),
                        anchors = result.items,
                        currentPage = 1,
                        hasMore = result.hasMore
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrentSearchRequest(requestId, siteIndex, searchType, nextKeyword)) return@launch
                Timber.e(e, "Search failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "搜索失败"
                )
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        currentKeyword = ""
        nextSearchRequestId()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            rooms = emptyList(),
            anchors = emptyList(),
            error = null,
            currentPage = 1,
            hasMore = false
        )
    }

    fun loadMore() {
        if (!_uiState.value.hasMore || _uiState.value.isLoading || currentKeyword.isEmpty()) return
        val state = _uiState.value
        val siteIndex = state.selectedSiteIndex
        val searchType = state.searchType
        val requestId = activeSearchRequestId
        val keyword = currentKeyword
        val site = siteTabs.value.getOrNull(siteIndex) ?: return
        val nextPage = state.currentPage + 1

        loadMoreJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                if (searchType == 0) {
                    val result = site.searchRooms(keyword, page = nextPage)
                    if (!isCurrentSearchRequest(requestId, siteIndex, searchType, keyword)) return@launch
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        rooms = _uiState.value.rooms + result.items,
                        hasMore = result.hasMore,
                        currentPage = nextPage
                    )
                } else {
                    val result = site.searchAnchors(keyword, page = nextPage)
                    if (!isCurrentSearchRequest(requestId, siteIndex, searchType, keyword)) return@launch
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        anchors = _uiState.value.anchors + result.items,
                        hasMore = result.hasMore,
                        currentPage = nextPage
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrentSearchRequest(requestId, siteIndex, searchType, keyword)) return@launch
                Timber.e(e, "Load more failed")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun nextSearchRequestId(): Long {
        activeSearchRequestId += 1
        return activeSearchRequestId
    }

    private fun isCurrentSearchRequest(
        requestId: Long,
        siteIndex: Int,
        searchType: Int,
        keyword: String
    ): Boolean {
        val state = _uiState.value
        return requestId == activeSearchRequestId &&
            state.selectedSiteIndex == siteIndex &&
            state.searchType == searchType &&
            currentKeyword == keyword
    }
}

internal fun searchUiStateForNewSearch(state: SearchUiState): SearchUiState {
    return state.copy(
        isLoading = true,
        rooms = emptyList(),
        anchors = emptyList(),
        error = null,
        currentPage = 1,
        hasMore = false
    )
}
