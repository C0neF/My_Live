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
        assertTrue(source.contains("isRefreshing = categoryPullRefreshIndicatorVisible("))
        assertTrue(source.contains("isPullRefreshing = true"))
        assertTrue(source.contains("viewModel.refresh()"))
        assertFalse(source.contains("isRefreshing = loading"))
        assertFalse(source.contains("contentDescription = \"刷新分类\""))
    }

    @Test
    fun categoryPullRefreshIndicatorShowsOnlyForPullRefreshLoading() {
        assertTrue(
            categoryPullRefreshIndicatorVisible(
                isLoading = true,
                isPullRefreshing = true
            )
        )
        assertFalse(
            categoryPullRefreshIndicatorVisible(
                isLoading = true,
                isPullRefreshing = false
            )
        )
        assertFalse(
            categoryPullRefreshIndicatorVisible(
                isLoading = false,
                isPullRefreshing = true
            )
        )
    }

    @Test
    fun categoryInitialLoadingUsesSkeletonInsteadOfCircularLoading() {
        val source = File("src/main/java/com/mylive/app/ui/screen/category/CategoryScreen.kt").readText()

        assertTrue(source.contains("pageLoading -> CategorySkeleton("))
        assertTrue(source.contains("SkeletonLine("))
        assertFalse(source.contains("pageLoading -> LoadingState("))
    }

    @Test
    fun categoryViewModelProvidesForceRefreshEntry() {
        val source = File("src/main/java/com/mylive/app/ui/screen/category/CategoryViewModel.kt").readText()

        assertTrue(source.contains("fun refresh()"))
        assertTrue(source.contains("loadCategories(forceRefresh = true)"))
    }
}
