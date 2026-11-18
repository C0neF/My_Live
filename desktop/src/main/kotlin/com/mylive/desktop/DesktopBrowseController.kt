package com.mylive.desktop

import com.mylive.app.core.model.*
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.mergeAnchorCatalogPage
import com.mylive.app.core.site.mergeRoomCatalogPage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BrowseMode { Recommend, Categories, CategoryRooms, Rooms, Anchors }

data class DesktopBrowseState(
    val siteId: String = "douyu", val mode: BrowseMode = BrowseMode.Recommend,
    val query: String = "", val category: LiveSubCategory? = null,
    val categories: List<LiveCategory> = emptyList(), val rooms: List<LiveRoomItem> = emptyList(),
    val anchors: List<LiveAnchorItem> = emptyList(), val page: Int = 0,
    val loading: Boolean = false, val hasMore: Boolean = false, val error: String? = null
)

/** Request ownership is confined to the UI dispatcher; stale/cancelled pages cannot change a new source. */
class DesktopBrowseController(
    private val sites: List<LiveSite>, dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow(DesktopBrowseState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private data class Source(val siteId: String, val mode: BrowseMode, val query: String, val categoryId: String?, val parentId: String?)
    private val cachedSources = object : LinkedHashMap<Source, DesktopBrowseState>(12, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Source, DesktopBrowseState>) = size > 12
    }
    private fun DesktopBrowseState.source() = Source(siteId, mode, query, category?.id, category?.parentId)

    fun recommend(siteId: String) = begin(DesktopBrowseState(siteId))
    fun categories(siteId: String) = begin(DesktopBrowseState(siteId, BrowseMode.Categories))
    fun category(siteId: String, category: LiveSubCategory) =
        begin(DesktopBrowseState(siteId, BrowseMode.CategoryRooms, category = category))
    fun search(siteId: String, query: String, anchors: Boolean) =
        if (query.isBlank()) recommend(siteId) else begin(DesktopBrowseState(siteId,
            if (anchors) BrowseMode.Anchors else BrowseMode.Rooms, query = query.trim()))

    fun prepareSearch(siteId: String) {
        require(sites.any { it.id == siteId })
        job?.cancel(); generation++
        _state.value = DesktopBrowseState(siteId, BrowseMode.Rooms)
    }

    fun refresh() = begin(_state.value.copy(page = 0, hasMore = false), useCache = false)
    fun retry() {
        val current = _state.value
        if (!current.loading) load(current.page + 1, generation)
    }
    fun more() {
        val current = _state.value
        if (current.loading || !current.hasMore || current.mode == BrowseMode.Categories) return
        load(current.page + 1, generation)
    }

    private fun begin(request: DesktopBrowseState, useCache: Boolean = true) {
        require(sites.any { it.id == request.siteId })
        job?.cancel()
        generation++
        val cached = if (useCache) cachedSources[request.source()] else null
        _state.value = cached ?: request.copy(loading = true, error = null)
        if (cached == null) load(1, generation)
    }

    private fun load(page: Int, token: Long) {
        val request = _state.value
        _state.value = request.copy(loading = true, error = null)
        val site = sites.first { it.id == request.siteId }
        job = scope.launch {
            try {
                val result = withContext(io) {
                    when (request.mode) {
                        BrowseMode.Categories -> request.copy(categories = site.getCategories())
                        BrowseMode.Anchors -> site.searchAnchors(request.query, page).let { request.copy(anchors = it.items, hasMore = it.hasMore) }
                        else -> {
                            val rooms = when (request.mode) {
                                BrowseMode.Recommend -> site.getRecommendRooms(page)
                                BrowseMode.CategoryRooms -> site.getCategoryRooms(requireNotNull(request.category), page)
                                else -> site.searchRooms(request.query, page).let { LiveCategoryResult(it.hasMore, it.items) }
                            }
                            request.copy(rooms = rooms.items, hasMore = rooms.hasMore)
                        }
                    }
                }
                ensureActive()
                if (token != generation) return@launch
                val previous = _state.value
                val rooms = mergeRoomCatalogPage(previous.rooms, result.rooms, page == 1)
                val anchors = mergeAnchorCatalogPage(previous.anchors, result.anchors, page == 1)
                val progressed = page == 1 || if (request.mode == BrowseMode.Anchors) anchors.size > previous.anchors.size else rooms.size > previous.rooms.size
                _state.value = result.copy(rooms = rooms, anchors = anchors, page = page, loading = false,
                    hasMore = result.hasMore && progressed, error = null)
                cachedSources[request.source()] = _state.value
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (token == generation) _state.value = _state.value.copy(loading = false,
                    error = "加载失败，请稍后重试；部分平台需要先登录。")
            }
        }
    }

    fun close() { generation++; scope.cancel(); cachedSources.clear() }
}
