package com.mylive.app.ui.screen.room.quickaccess

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.model.LiveAnchorItem
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.model.LiveSubCategory
import com.mylive.app.core.site.LatestCatalogRequestGate
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.mergeRoomCatalogPage
import com.mylive.app.core.site.sortedByDefaultOrder
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.data.repository.UserRemarkRepository
import com.mylive.app.service.FollowStatusRefreshCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

data class QuickAccessGlobalRoomHit(
    val siteId: String,
    val room: LiveRoomItem
)

data class QuickAccessGlobalAnchorHit(
    val siteId: String,
    val anchor: LiveAnchorItem
)

data class QuickAccessGlobalSearchState(
    val query: String = "",
    val siteId: String? = null,
    val loading: Boolean = false,
    val rooms: List<QuickAccessGlobalRoomHit> = emptyList(),
    val anchors: List<QuickAccessGlobalAnchorHit> = emptyList()
)

data class QuickAccessLibrary(
    val follows: List<FollowUserEntity> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val tags: List<FollowUserTagEntity> = emptyList(),
    val remarks: Map<String, String> = emptyMap(),
    val loading: Boolean = true
)

data class QuickAccessRecommendationSource(val siteId: String, val categoryId: String?)

data class QuickAccessRecommendations(
    val source: QuickAccessRecommendationSource? = null,
    val rooms: List<LiveRoomItem> = emptyList(),
    val loading: Boolean = false,
    val hasLoaded: Boolean = false,
    val hasMore: Boolean = true,
    val nextPage: Int = 1,
    val error: String? = null
)

@HiltViewModel
class QuickAccessViewModel @Inject constructor(
    private val followRepository: FollowRepository,
    private val historyRepository: HistoryRepository,
    settingsRepository: SettingsRepository,
    private val sites: Set<@JvmSuppressWildcards LiveSite>,
    private val followStatusRefreshCoordinator: FollowStatusRefreshCoordinator,
    remarkRepository: UserRemarkRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    val library = combine(followRepository.getAllFollows(), historyRepository.getAllHistory(),
        followRepository.getAllTags(), remarkRepository.observeAllRemarks()) { follows, history, tags, remarks ->
        QuickAccessLibrary(follows, history, tags, remarks, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QuickAccessLibrary())

    val quickAccessSort: StateFlow<String?> = settingsRepository.liveRoomQuickAccessSort
        .map<String, String?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val quickAccessEnabled = settingsRepository.liveRoomQuickAccessEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val selectedTabKey: StateFlow<String?> = savedState.getStateFlow("quickAccessTab", null)
    fun selectTab(key: String) { savedState["quickAccessTab"] = key }

    private val _followFilters = MutableStateFlow(QuickAccessFilters())
    val followFilters = _followFilters.asStateFlow()
    fun setFollowFilters(filters: QuickAccessFilters) {
        _followFilters.value = filters
        triggerGlobalSearch(filters.query, filters.siteId)
    }
    private val _historyFilters = MutableStateFlow(QuickAccessFilters())
    val historyFilters = _historyFilters.asStateFlow()
    fun setHistoryFilters(filters: QuickAccessFilters) {
        _historyFilters.value = filters
        triggerGlobalSearch(filters.query, filters.siteId)
    }

    val availableSiteIds: List<String> =
        sites.sortedByDefaultOrder().map { it.id }.ifEmpty { listOf("bilibili", "douyu", "huya", "douyin") }

    private val _globalSearch = MutableStateFlow(QuickAccessGlobalSearchState())
    val globalSearch = _globalSearch.asStateFlow()
    private var globalSearchJob: Job? = null
    private val globalSearchGate = LatestCatalogRequestGate()

    fun triggerGlobalSearch(query: String, siteId: String?) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            globalSearchJob?.cancel()
            globalSearchGate.invalidate()
            _globalSearch.value = QuickAccessGlobalSearchState(siteId = siteId)
            return
        }
        val current = _globalSearch.value
        if (current.query == keyword && current.siteId == siteId && (current.loading || current.rooms.isNotEmpty() || current.anchors.isNotEmpty())) {
            return
        }
        val token = globalSearchGate.begin()
        globalSearchJob?.cancel()
        _globalSearch.value = QuickAccessGlobalSearchState(query = keyword, siteId = siteId, loading = true)
        globalSearchJob = viewModelScope.launch {
            val targetSites = if (siteId != null) {
                sites.filter { it.id == siteId }
            } else {
                sites.sortedByDefaultOrder()
            }
            val (anchors, rooms) = supervisorScope {
                val anchorTasks = targetSites.map { site ->
                    async {
                        try {
                            site.searchAnchors(keyword, 1).items
                                .filter { it.roomId.isNotBlank() }
                                .map { QuickAccessGlobalAnchorHit(site.id, it) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            emptyList()
                        }
                    }
                }
                val roomTasks = targetSites.map { site ->
                    async {
                        try {
                            site.searchRooms(keyword, 1).items
                                .filter { it.roomId.isNotBlank() }
                                .map { QuickAccessGlobalRoomHit(site.id, it) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            emptyList()
                        }
                    }
                }
                anchorTasks.flatMap { it.await() } to roomTasks.flatMap { it.await() }
            }
            if (!globalSearchGate.isCurrent(token)) return@launch
            _globalSearch.value = QuickAccessGlobalSearchState(
                query = keyword,
                siteId = siteId,
                loading = false,
                anchors = anchors.distinctBy { it.siteId to it.anchor.roomId },
                rooms = rooms.distinctBy { it.siteId to it.room.roomId }
            )
        }
    }

    fun scrollPosition(key: String): Pair<Int, Int> =
        (savedState.get<Int>("$key.index") ?: 0) to (savedState.get<Int>("$key.offset") ?: 0)
    fun saveScrollPosition(key: String, index: Int, offset: Int) {
        savedState["$key.index"] = index
        savedState["$key.offset"] = offset
    }

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()
    fun clearActionError() { _actionError.value = null }
    private var followRefreshJob: Job? = null
    val updatingFollowStatus = followStatusRefreshCoordinator.isRefreshing
    val followRefreshEntries = followStatusRefreshCoordinator.entries
    val followRefreshProgress = followStatusRefreshCoordinator.progress

    fun updateFollowStatus() {
        if (followRefreshJob?.isActive == true || updatingFollowStatus.value) return
        followRefreshJob = viewModelScope.launch {
            _actionError.value = null
            try {
                followStatusRefreshCoordinator.refreshAll()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _actionError.value = "关注状态刷新失败，请稍后重试"
            }
        }
    }

    fun removeHistory(id: String) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                historyRepository.removeHistory(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _actionError.value = "删除失败，请重试"
            }
        }
    }

    private val _recommendations = MutableStateFlow(QuickAccessRecommendations())
    val recommendations = _recommendations.asStateFlow()
    private var recommendationJob: Job? = null
    private var failedReset = true
    private val recommendationGate = LatestCatalogRequestGate()

    /** Called only when the recommendation tab becomes active; reopening reuses its loaded page. */
    fun ensureRecommendations(siteId: String, categoryId: String?) {
        val source = QuickAccessRecommendationSource(siteId, categoryId?.takeIf(String::isNotBlank))
        if (_recommendations.value.source == source &&
            (_recommendations.value.hasLoaded || _recommendations.value.loading || _recommendations.value.error != null)) return
        requestRecommendations(source, reset = true)
    }

    fun refreshRecommendations() {
        _recommendations.value.source?.let { requestRecommendations(it, reset = true) }
    }

    fun loadMoreRecommendations() {
        val state = _recommendations.value
        if (state.loading || !state.hasMore) return
        state.source?.let { requestRecommendations(it, reset = !state.hasLoaded) }
    }

    fun retryRecommendations() {
        val state = _recommendations.value
        if (state.loading) return
        state.source?.let { requestRecommendations(it, reset = failedReset) }
    }

    private fun requestRecommendations(source: QuickAccessRecommendationSource, reset: Boolean) {
        val previous = _recommendations.value
        if (previous.loading && !reset) return
        val token = recommendationGate.begin()
        recommendationJob?.cancel()
        val base = if (previous.source == source) previous else QuickAccessRecommendations(source = source)
        val page = if (reset) 1 else base.nextPage
        _recommendations.value = base.copy(loading = true, error = null)
        recommendationJob = viewModelScope.launch {
            try {
                val site = sites.firstOrNull { it.id == source.siteId } ?: error("平台不可用")
                val result = if (source.categoryId == null) site.getRecommendRooms(page) else
                    site.getCategoryRooms(LiveSubCategory(id = source.categoryId, name = ""), page)
                if (!recommendationGate.isCurrent(token)) return@launch
                val rooms = mergeRoomCatalogPage(base.rooms, result.items.filter { it.roomId.isNotBlank() }, reset)
                _recommendations.value = QuickAccessRecommendations(
                    source = source, rooms = rooms, hasLoaded = true,
                    hasMore = result.hasMore && result.items.isNotEmpty() && (reset || rooms.size > base.rooms.size),
                    nextPage = page + 1
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!recommendationGate.isCurrent(token)) return@launch
                failedReset = reset
                _recommendations.value = base.copy(loading = false, error = "推荐加载失败，请重试")
            }
        }
    }

    override fun onCleared() {
        recommendationGate.invalidate()
        super.onCleared()
    }
}
