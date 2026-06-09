package com.mylive.app.ui.screen

internal enum class BottomTabRepeatAction {
    None,
    Refresh
}

internal fun shouldRefreshBottomTab(currentKey: String, clickedKey: String): Boolean {
    return bottomTabRepeatAction(
        currentKey = currentKey,
        clickedKey = clickedKey,
        isCurrentPageAtTop = true
    ) == BottomTabRepeatAction.Refresh
}

internal fun bottomTabRepeatAction(
    currentKey: String,
    clickedKey: String,
    isCurrentPageAtTop: Boolean
): BottomTabRepeatAction {
    if (currentKey != clickedKey || clickedKey !in refreshableBottomTabKeys()) {
        return BottomTabRepeatAction.None
    }
    return BottomTabRepeatAction.Refresh
}

internal fun refreshableBottomTabKeys(): Set<String> {
    return setOf("recommend", "follow", "category")
}

internal fun isScrollableContentAtTop(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int
): Boolean {
    return firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
}

internal fun backToTopButtonVisible(isAtTop: Boolean, hasItems: Boolean): Boolean {
    return hasItems && !isAtTop
}
