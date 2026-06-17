package com.mylive.app.data.repository

import com.mylive.app.data.local.secure.SensitiveCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val sensitiveCredentialStore: SensitiveCredentialStore
) {
    // BiliBili
    val bilibiliCookie: Flow<String> = sensitiveCredentialStore.bilibiliCookie
    suspend fun setBilibiliCookie(cookie: String) = sensitiveCredentialStore.setBilibiliCookie(cookie)
    val isLoggedInBiliBili: Flow<Boolean> = bilibiliCookie.map { it.isNotEmpty() }

    // Douyin
    val douyinCookie: Flow<String> = sensitiveCredentialStore.douyinCookie
    suspend fun setDouyinCookie(cookie: String) = sensitiveCredentialStore.setDouyinCookie(cookie)
    val isLoggedInDouyin: Flow<Boolean> = douyinCookie.map { it.isNotEmpty() }

    // Logout
    suspend fun logoutBiliBili() = setBilibiliCookie("")
    suspend fun logoutDouyin() = setDouyinCookie("")
}
