package com.mylive.app.core.site

import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.script.JsEngine
import com.mylive.app.core.site.bilibili.BiliBiliSite
import com.mylive.app.core.site.douyin.DouyinSite
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import com.mylive.app.data.repository.AccountRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Provider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountRequestCredentialsTest {
    private val bilibili = MutableStateFlow("DedeUserID=3546387306719500;SESSDATA=saved;buvid3=b3;buvid4=b4")
    private val douyin = MutableStateFlow("sessionid=saved;ttwid=saved-ttwid")
    private val accounts = AccountRepository(mockk<SensitiveCredentialStore> {
        every { bilibiliCookie } returns bilibili
        every { douyinCookie } returns douyin
        every { douyuCookie } returns MutableStateFlow("")
    })

    @Test fun bilibiliUsesPersistedCookieWithoutAnyRoomViewModelAndReflectsLogout() = runBlocking {
        val http = mockk<HttpClient>()
        val sent = mutableListOf<Map<String, String>>()
        coEvery { http.getJson(any(), any(), any()) } coAnswers {
            if (firstArg<String>().contains("getRoomList")) {
                sent.add(thirdArg())
                JSONObject("""{"data":[]}""")
            } else JSONObject("""{"data":{"b_3":"guest-b3","b_4":"guest-b4"}}""")
        }
        val site = BiliBiliSite(http, OkHttpClient(), accounts)
        site.getRecommendRooms(1)
        assertTrue(sent.last().getValue("cookie").contains("SESSDATA=saved"))
        assertEquals(3546387306719500L, site.userId)
        bilibili.value = ""
        site.getRecommendRooms(1)
        assertFalse(sent.last().getValue("cookie").contains("SESSDATA"))
        assertEquals(0L, site.userId)
    }

    @Test fun douyinReadsSavedAndUpdatedCredentialsAtRequestTime() = runBlocking {
        val http = mockk<HttpClient>()
        val sent = mutableListOf<Map<String, String>>()
        coEvery { http.getJson(any(), any(), any()) } coAnswers {
            sent.add(thirdArg())
            JSONObject("""{"data":{"data":[{"status":2}]}}""")
        }
        val js = mockk<JsEngine>()
        coEvery { js.execute(any(), any(), *anyVararg()) } returns "signature"
        val site = DouyinSite(http, Provider { js }, OkHttpClient(), accounts)
        site.getFollowPresence("12345")
        assertTrue(sent.last().getValue("cookie").contains("sessionid=saved"))
        douyin.value = "sessionid=updated;ttwid=updated-ttwid"
        site.getFollowPresence("12345")
        assertTrue(sent.last().getValue("cookie").contains("sessionid=updated"))
    }
}
