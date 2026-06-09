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
        assertTrue(source.contains("isRefreshing = loading"))
        assertTrue(source.contains("onRefresh = { viewModel.refresh() }"))
        assertFalse(source.contains("contentDescription = \"刷新分类\""))
    }

    @Test
    fun categoryViewModelProvidesForceRefreshEntry() {
        val source = File("src/main/java/com/mylive/app/ui/screen/category/CategoryViewModel.kt").readText()

        assertTrue(source.contains("fun refresh()"))
        assertTrue(source.contains("loadCategories(forceRefresh = true)"))
    }
}
