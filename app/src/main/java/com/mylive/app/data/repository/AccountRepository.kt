package com.mylive.app.data.repository

import com.mylive.app.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    // BiliBili
    val bilibiliCookie: Flow<String> = settingsDataStore.getFlow(SettingsDataStore.BilibiliCookie, "")
    suspend fun setBilibiliCookie(cookie: String) = settingsDataStore.setValue(SettingsDataStore.BilibiliCookie, cookie)
    val isLoggedInBiliBili: Flow<Boolean> = bilibiliCookie.map { it.isNotEmpty() }

    // Douyin
    val douyinCookie: Flow<String> = settingsDataStore.getFlow(SettingsDataStore.DouyinCookie, "")
    suspend fun setDouyinCookie(cookie: String) = settingsDataStore.setValue(SettingsDataStore.DouyinCookie, cookie)
    val isLoggedInDouyin: Flow<Boolean> = douyinCookie.map { it.isNotEmpty() }

    // Logout
    suspend fun logoutBiliBili() = setBilibiliCookie("")
    suspend fun logoutDouyin() = setDouyinCookie("")
}
