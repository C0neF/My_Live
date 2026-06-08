package com.mylive.app.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.model.LiveAnchorItem
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.sortedByDefaultOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val hasMore: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val sites: Set<@JvmSuppressWildcards LiveSite>
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val siteTabs: List<LiveSite> = sites.sortedByDefaultOrder()
    private var currentSiteIndex = 0
    private var currentKeyword = ""

    fun selectSite(index: Int) {
        currentSiteIndex = index
        if (currentKeyword.isNotEmpty()) {
            search(currentKeyword)
        }
    }

    fun setSearchType(type: Int) {
        _uiState.value = _uiState.value.copy(searchType = type)
        if (currentKeyword.isNotEmpty()) {
            search(currentKeyword)
        }
    }

    fun search(keyword: String) {
        currentKeyword = keyword
        val site = siteTabs.getOrNull(currentSiteIndex) ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, currentPage = 1)
            try {
                if (_uiState.value.searchType == 0) {
                    val result = site.searchRooms(keyword)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        rooms = result.items,
                        hasMore = result.hasMore
                    )
                } else {
                    val result = site.searchAnchors(keyword)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        anchors = result.items,
                        hasMore = result.hasMore
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Search failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "搜索失败"
                )
            }
        }
    }

    fun loadMore() {
        if (!_uiState.value.hasMore || _uiState.value.isLoading || currentKeyword.isEmpty()) return
        val site = siteTabs.getOrNull(currentSiteIndex) ?: return
        val nextPage = _uiState.value.currentPage + 1

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                if (_uiState.value.searchType == 0) {
                    val result = site.searchRooms(currentKeyword, page = nextPage)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        rooms = _uiState.value.rooms + result.items,
                        hasMore = result.hasMore,
                        currentPage = nextPage
                    )
                } else {
                    val result = site.searchAnchors(currentKeyword, page = nextPage)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        anchors = _uiState.value.anchors + result.items,
                        hasMore = result.hasMore,
                        currentPage = nextPage
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Load more failed")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
