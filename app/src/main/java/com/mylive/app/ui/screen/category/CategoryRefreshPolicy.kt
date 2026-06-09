package com.mylive.app.ui.screen.category

internal fun shouldUseCachedCategories(
    forceRefresh: Boolean,
    cachedHasData: Boolean
): Boolean {
    return !forceRefresh && cachedHasData
}
