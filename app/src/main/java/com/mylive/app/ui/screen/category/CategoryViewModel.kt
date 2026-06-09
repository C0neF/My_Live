package com.mylive.app.ui.screen.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.model.LiveCategory
import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.sortedByDefaultOrder
import com.mylive.app.core.site.sortedByUserOrder
import com.mylive.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class CategoryPageState(
    val categories: List<LiveCategory> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    liveSites: Set<@JvmSuppressWildcards LiveSite>,
    settingsRepository: SettingsRepository
) : ViewModel() {

    // Site list sorted by user preference (reactive)
    val siteList: StateFlow<List<LiveSite>> = settingsRepository.siteSort
        .combine(kotlinx.coroutines.flow.flowOf(liveSites)) { sortStr, sites ->
            sites.sortedByUserOrder(sortStr)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), liveSites.sortedByDefaultOrder())

    private val _selectedSiteIndex = MutableStateFlow(0)
    val selectedSiteIndex: StateFlow<Int> = _selectedSiteIndex.asStateFlow()

    private val _categories = MutableStateFlow<List<LiveCategory>>(emptyList())
    val categories: StateFlow<List<LiveCategory>> = _categories.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loadedSiteId = MutableStateFlow<String?>(null)
    private var loadJob: Job? = null

    // Per-site cache to avoid flicker when swiping between pages
    private data class SiteCache(
        val categories: List<LiveCategory> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null
    )
    private val siteCache = mutableMapOf<String, SiteCache>()

    fun getSiteTabs(): List<LiveSite> = siteList.value

    init {
        viewModelScope.launch {
            siteList.collect { sites ->
                if (sites.isNotEmpty()) {
                    val currentSite = sites.getOrNull(_selectedSiteIndex.value)
                    if (currentSite != null && _loadedSiteId.value != currentSite.id) {
                        selectSite(_selectedSiteIndex.value)
                    }
                }
            }
        }
    }

    fun selectSite(index: Int) {
        _selectedSiteIndex.value = index
        loadCategories()
    }

    private fun loadCategories(forceRefresh: Boolean = false) {
        val sites = siteList.value
        val index = _selectedSiteIndex.value
        if (index !in sites.indices) return

        val site = sites[index]

        // If already cached with data, use cache immediately (no flicker)
        val cached = siteCache[site.id]
        if (cached != null && shouldUseCachedCategories(forceRefresh, cachedHasData = cached.categories.isNotEmpty())) {
            loadJob?.cancel()
            _loadedSiteId.value = site.id
            _categories.value = cached.categories
            _loading.value = false
            _error.value = cached.error
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _loadedSiteId.value = site.id
            _loading.value = true
            _error.value = null
            // Don't clear categories if we have cached data (prevents flash)
            if (cached == null || cached.categories.isEmpty()) {
                _categories.value = emptyList()
            }
            siteCache[site.id] = SiteCache(categories = cached?.categories.orEmpty(), loading = true)
            try {
                val result = site.getCategories()
                _categories.value = result
                siteCache[site.id] = SiteCache(categories = result)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "Failed to load categories for ${site.name}")
                _error.value = e.message ?: "加载分类失败"
                val fallbackCategories = cached?.categories.orEmpty()
                _categories.value = fallbackCategories
                siteCache[site.id] = SiteCache(categories = fallbackCategories, error = _error.value)
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Get cached state for a specific site index (used by HorizontalPager pages).
     * Returns cached data if available, null if never loaded.
     */
    fun getCachedState(siteIndex: Int): CategoryPageState? {
        val sites = siteList.value
        val site = sites.getOrNull(siteIndex) ?: return null
        val cached = siteCache[site.id] ?: return null
        return CategoryPageState(
            categories = cached.categories,
            loading = cached.loading,
            error = cached.error
        )
    }

    fun refresh() {
        loadCategories(forceRefresh = true)
    }

    fun retry() {
        loadCategories(forceRefresh = true)
    }

    fun getSelectedSite(): LiveSite? {
        return siteList.value.getOrNull(_selectedSiteIndex.value)
    }
}
