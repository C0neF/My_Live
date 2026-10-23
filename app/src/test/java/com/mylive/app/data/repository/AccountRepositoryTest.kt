package com.mylive.app.data.repository

import com.mylive.app.core.site.LiveSiteAccount
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountRepositoryTest {

    private val bilibili = MutableStateFlow("")
    private val douyin = MutableStateFlow("")
    private val douyu = MutableStateFlow("")

    private val repository = AccountRepository(
        mockk<SensitiveCredentialStore>().also { store ->
            every { store.bilibiliCookie } returns bilibili
            every { store.douyinCookie } returns douyin
            every { store.douyuCookie } returns douyu
        }
    )

    @Test
    fun everyAccountCapabilityIsPublished() = runBlocking {
        // The map is what routes credentials to site adapters. A capability missing from it is a
        // site that silently never sees its cookie, which is why the producer builds the map from
        // LiveSiteAccount.entries rather than from hand-written keys.
        val cookies = repository.liveSiteAccountCookies.first()

        assertEquals(LiveSiteAccount.entries.toSet(), cookies.keys)
    }

    @Test
    fun eachCredentialLandsUnderItsOwnCapability() = runBlocking {
        bilibili.value = "SESSDATA=bili"
        douyin.value = "ttwid=douyin"
        douyu.value = "dy_auth=douyu"

        val cookies = repository.liveSiteAccountCookies.first()

        assertEquals("SESSDATA=bili", cookies[LiveSiteAccount.BILIBILI])
        assertEquals("ttwid=douyin", cookies[LiveSiteAccount.DOUYIN])
        assertEquals("dy_auth=douyu", cookies[LiveSiteAccount.DOUYU])
    }

    @Test
    fun signedOutAccountsArePublishedAsEmptyRatherThanOmitted() = runBlocking {
        bilibili.value = "SESSDATA=bili"
        douyin.value = ""

        val cookies = repository.liveSiteAccountCookies.first()

        // An omitted key would leave the previous cookie installed on the site adapter after a
        // logout; an explicit empty value clears it.
        assertEquals(LiveSiteAccount.entries.toSet(), cookies.keys)
        assertEquals("", cookies[LiveSiteAccount.DOUYIN])
    }
}
