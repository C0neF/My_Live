package com.mylive.app.ui.screen.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.model.LiveSubCategory
import com.mylive.app.core.site.LiveSite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val sites: Set<@JvmSuppressWildcards LiveSite>
) : ViewModel() {

    var siteId: String = ""
        private set
    var categoryId: String = ""
        private set
    var categoryName: String = ""
        private set

    private val _rooms = MutableStateFlow<List<LiveRoomItem>>(emptyList())
    val rooms: StateFlow<List<LiveRoomItem>> = _rooms.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentPage = 1
    private var currentSite: LiveSite? = null
    private var initialized = false
    private var loadJob: Job? = null
    private var activeLoadRequestId = 0L

    fun init(siteId: String, categoryId: String, categoryName: String) {
        if (initialized) return
        initialized = true

        this.siteId = siteId
        this.categoryId = categoryId
        this.categoryName = categoryName

        currentSite = sites.find { it.id == siteId }
        if (currentSite != null && categoryId.isNotEmpty()) {
            loadRooms(isRefresh = false)
        } else {
            _error.value = "未找到对应平台或分类"
        }
    }

    fun loadRooms(isRefresh: Boolean) {
        val site = currentSite ?: return

        if (isRefresh) {
            currentPage = 1
            _hasMore.value = true
            _refreshing.value = true
        } else if (_loading.value || _loadingMore.value) {
            return
        }

        if (currentPage == 1) {
            _loading.value = true
        } else {
            _loadingMore.value = true
        }
        _error.value = null

        val pageToLoad = currentPage
        val requestId = nextLoadRequestId()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val subCategory = LiveSubCategory(
                    name = categoryName,
                    id = categoryId
                )
                val result = site.getCategoryRooms(subCategory, page = pageToLoad)
                if (!isActiveLoadRequest(requestId)) return@launch
                if (isRefresh || pageToLoad == 1) {
                    _rooms.value = result.items
                } else {
                    _rooms.value = (_rooms.value + result.items).distinctBy { it.roomId }
                }
                _hasMore.value = result.hasMore
                currentPage = pageToLoad + 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isActiveLoadRequest(requestId)) return@launch
                Timber.e(e, "Failed to load category rooms")
                _error.value = e.message ?: "加载失败"
            } finally {
                if (isActiveLoadRequest(requestId)) {
                    _loading.value = false
                    _loadingMore.value = false
                    _refreshing.value = false
                }
            }
        }
    }

    fun refresh() {
        loadRooms(isRefresh = true)
    }

    fun loadMore() {
        if (_hasMore.value && !_loading.value && !_loadingMore.value) {
            loadRooms(isRefresh = false)
        }
    }

    fun retry() {
        loadRooms(isRefresh = false)
    }

    private fun nextLoadRequestId(): Long {
        activeLoadRequestId += 1
        return activeLoadRequestId
    }

    private fun isActiveLoadRequest(requestId: Long): Boolean {
        return requestId == activeLoadRequestId
    }
}
