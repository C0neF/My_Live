package com.mylive.app.ui.screen.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SearchViewModelPolicyTest {

    @Test
    fun searchViewModelCancelsStaleSearchesAndGuardsResultWrites() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchViewModel.kt").readText()

        assertTrue(source.contains("private var searchJob: Job?"))
        assertTrue(source.contains("private var loadMoreJob: Job?"))
        assertTrue(source.contains("searchJob?.cancel()"))
        assertTrue(source.contains("loadMoreJob?.cancel()"))
        assertTrue(source.contains("nextSearchRequestId()"))
        assertTrue(source.contains("isCurrentSearchRequest("))
    }

    @Test
    fun searchViewModelClearsStaleResultsWhenStartingNewSearch() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchViewModel.kt").readText()

        assertTrue(source.contains("searchUiStateForNewSearch("))
        assertTrue(source.contains("rooms = emptyList()"))
        assertTrue(source.contains("anchors = emptyList()"))
        assertTrue(source.contains("hasMore = false"))
    }

    @Test
    fun selectedSearchSiteLivesInUiState() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchViewModel.kt").readText()

        assertTrue(source.contains("val selectedSiteIndex: Int = 0"))
        assertTrue(source.contains("selectedSiteIndex = nextIndex"))
        assertFalse(source.contains("private var currentSiteIndex"))
    }

    @Test
    fun searchPlatformsFollowThePersistedUserOrder() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchViewModel.kt").readText()

        assertTrue(source.contains("private val settingsRepository: SettingsRepository"))
        assertTrue(source.contains("settingsRepository.siteSort"))
        assertTrue(source.contains("sortedByUserOrder(sortStr)"))
        assertTrue(source.contains("val siteTabs: StateFlow<List<LiveSite>>"))
        assertFalse(source.contains("val siteTabs: List<LiveSite> = sites.sortedByDefaultOrder()"))
    }

    @Test
    fun searchSelectionTracksTheSameSiteWhenPlatformOrderChanges() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchViewModel.kt").readText()

        assertTrue(source.contains("siteTabs.collect { reorderedSites ->"))
        assertTrue(source.contains("preserveSelectedSiteIndex("))
        assertTrue(source.contains("previousSiteIds = previousSiteIds"))
        assertTrue(source.contains("reorderedSiteIds = reorderedSiteIds"))
        assertTrue(source.contains("search(currentKeyword)"))
    }
}
