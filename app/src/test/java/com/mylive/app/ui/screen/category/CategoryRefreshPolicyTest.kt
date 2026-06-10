package com.mylive.app.ui.screen.category

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CategoryRefreshPolicyTest {

    @Test
    fun forcedCategoryRefreshSkipsCachedCategories() {
        assertTrue(shouldUseCachedCategories(forceRefresh = false, cachedHasData = true))
        assertFalse(shouldUseCachedCategories(forceRefresh = true, cachedHasData = true))
        assertFalse(shouldUseCachedCategories(forceRefresh = false, cachedHasData = false))
    }

    @Test
    fun categoryScreenKeepsPullRefreshWithoutHeaderRefreshAction() {
        val source = File("src/main/java/com/mylive/app/ui/screen/category/CategoryScreen.kt").readText()

        assertTrue(source.contains("PullToRefreshBox("))
        // The indicator is driven by a dedicated pull flag, not the general loading
        // state, so it doesn't appear on page entry / platform switch — only on a pull.
        assertFalse(source.contains("isRefreshing = loading"))
        assertTrue(source.contains("isRefreshing = isRefreshing"))
        assertTrue(source.contains("onRefresh = { viewModel.refreshFromPull() }"))
        assertFalse(source.contains("contentDescription = \"刷新分类\""))
    }

    @Test
    fun categoryViewModelProvidesForceRefreshEntry() {
        val source = File("src/main/java/com/mylive/app/ui/screen/category/CategoryViewModel.kt").readText()

        assertTrue(source.contains("fun refresh()"))
        assertTrue(source.contains("loadCategories(forceRefresh = true)"))
    }
}
