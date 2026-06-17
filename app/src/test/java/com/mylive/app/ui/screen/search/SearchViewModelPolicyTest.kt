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
}
