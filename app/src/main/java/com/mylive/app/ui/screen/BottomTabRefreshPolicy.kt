package com.mylive.app.ui.screen

internal fun shouldRefreshBottomTab(currentKey: String, clickedKey: String): Boolean {
    return currentKey == clickedKey && clickedKey in refreshableBottomTabKeys()
}

internal fun refreshableBottomTabKeys(): Set<String> {
    return setOf("recommend", "follow", "category")
}
