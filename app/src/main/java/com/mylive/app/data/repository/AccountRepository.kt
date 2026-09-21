package com.mylive.app.data.repository

import com.mylive.app.core.site.LiveSiteAccount
import com.mylive.app.core.site.LiveSiteAccountStore
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val sensitiveCredentialStore: SensitiveCredentialStore
) : LiveSiteAccountStore {
    override suspend fun cookieFor(account: LiveSiteAccount): String = when (account) {
        LiveSiteAccount.BILIBILI -> bilibiliCookie.first()
        LiveSiteAccount.DOUYIN -> douyinCookie.first()
    }
    // BiliBili
    val bilibiliCookie: Flow<String> = sensitiveCredentialStore.bilibiliCookie
    suspend fun setBilibiliCookie(cookie: String) = sensitiveCredentialStore.setBilibiliCookie(cookie)
    val isLoggedInBiliBili: Flow<Boolean> = bilibiliCookie.map { it.isNotEmpty() }

    // Douyin
    val douyinCookie: Flow<String> = sensitiveCredentialStore.douyinCookie
    suspend fun setDouyinCookie(cookie: String) = sensitiveCredentialStore.setDouyinCookie(cookie)
    val isLoggedInDouyin: Flow<Boolean> = douyinCookie.map { it.isNotEmpty() }

    /**
     * Every stored account credential, keyed by the capability a site declares it consumes.
     *
     * The `when` is exhaustive on purpose: adding a [LiveSiteAccount] entry will not compile until
     * the credential behind it is wired up here, which a `mapOf("siteId" to ...)` literal could
     * not enforce.
     */
    val liveSiteAccountCookies: Flow<Map<LiveSiteAccount, String>> = combine(
        bilibiliCookie,
        douyinCookie
    ) { bilibili, douyin ->
        LiveSiteAccount.entries.associateWith { account ->
            when (account) {
                LiveSiteAccount.BILIBILI -> bilibili
                LiveSiteAccount.DOUYIN -> douyin
            }
        }
    }

    // Logout
    suspend fun logoutBiliBili() = setBilibiliCookie("")
    suspend fun logoutDouyin() = setDouyinCookie("")
}
