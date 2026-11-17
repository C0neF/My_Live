package com.mylive.app.core.site.douyin

import com.mylive.app.core.common.CoreError
import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.model.LiveSubCategory
import com.mylive.app.core.script.JsExecutor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import javax.inject.Provider

class DouyinAnonymousCookieRefreshTest {
    @Test
    fun recommendRetriesWithANewlyIssuedTtwidAfter401() = runBlocking {
        val issuer = CountingTtwid()
        val cookies = mutableListOf<String>()
        val http = mockk<HttpClient>()
        coEvery { http.getJson(any(), any(), any()) } coAnswers {
            val cookie = cookieOf(thirdArg())
            cookies += cookie
            if (cookie == "ttwid=gen-0") throw CoreError("rejected", statusCode = 401)
            JSONObject("""{"data":{"data":[]}}""")
        }
        val result = site(http, issuer).getRecommendRooms(1)
        assertTrue(result.items.isEmpty())
        assertEquals(listOf("ttwid=gen-0", "ttwid=gen-1"), cookies)
        assertEquals(2, issuer.issues)
    }

    @Test
    fun secondRejectedRecommendDoesNotKeepTheFirstTtwid() = runBlocking {
        val issuer = CountingTtwid()
        val cookies = mutableListOf<String>()
        val http = mockk<HttpClient>()
        coEvery { http.getJson(any(), any(), any()) } coAnswers {
            cookies += cookieOf(thirdArg())
            throw CoreError("rejected", statusCode = 401)
        }
        val douyin = site(http, issuer)
        runCatching { douyin.getRecommendRooms(1) }
        val afterFirst = issuer.issues
        runCatching { douyin.getRecommendRooms(1) }
        assertTrue(issuer.issues > afterFirst)
        assertEquals("ttwid=gen-0", cookies.first())
        assertNotEquals(cookies.first(), cookies[afterFirst])
    }

    @Test
    fun categoryPageRefreshesAfter403() = runBlocking {
        val issuer = CountingTtwid()
        val cookies = mutableListOf<String>()
        val http = mockk<HttpClient>()
        coEvery { http.getJson(any(), any(), any()) } coAnswers {
            val cookie = cookieOf(thirdArg())
            cookies += cookie
            if (cookie == "ttwid=gen-0") throw CoreError("rejected", statusCode = 403)
            JSONObject("""{"data":{"data":[]}}""")
        }
        site(http, issuer).getCategoryRooms(LiveSubCategory(name = "分区", id = "720,1"), 1)
        assertEquals(listOf("ttwid=gen-0", "ttwid=gen-1"), cookies)
    }

    @Test
    fun categoryListRefreshesAfter401() = runBlocking {
        val issuer = CountingTtwid()
        val pageCookies = mutableListOf<String>()
        val http = mockk<HttpClient>()
        coEvery { http.getText(any(), any(), any()) } coAnswers {
            val cookie = cookieOf(thirdArg())
            pageCookies += cookie
            if (cookie == "ttwid=gen-0") throw CoreError("rejected", statusCode = 401)
            "<html></html>"
        }
        runCatching { site(http, issuer).getCategories() }
        assertEquals(listOf("ttwid=gen-0", "ttwid=gen-1"), pageCookies)
    }

    @Test
    fun searchRefreshesAnonymousCookieAfter401() = runBlocking {
        val issuer = CountingTtwid()
        val seen = mutableListOf<String>()
        val http = mockk<HttpClient>()
        coEvery { http.head(any(), any(), any()) } coAnswers {
            val cookie = cookieOf(thirdArg())
            seen += "head:$cookie"
            response(if (cookie == "ttwid=gen-0") 401 else 200)
        }
        coEvery { http.getJson(any(), any(), any()) } coAnswers {
            val cookie = cookieOf(thirdArg())
            seen += "get:$cookie"
            if (!cookie.contains("gen-1")) throw CoreError("rejected", statusCode = 401)
            JSONObject("""{"data":[]}""")
        }
        site(http, issuer).searchRooms("游戏", 1)
        assertTrue(seen.contains("head:ttwid=gen-0"))
        assertTrue(seen.any { it.startsWith("get:") && it.contains("gen-1") })
        assertTrue(issuer.issues >= 2)
    }

    @Test
    fun searchKeepsLoginCookieWhenHeadPrefetchFails() = runBlocking {
        val issuer = CountingTtwid()
        val login = "sessionid=saved;ttwid=saved-ttwid"
        val searchCookies = mutableListOf<String>()
        val http = mockk<HttpClient>()
        coEvery { http.head(any(), any(), any()) } throws CoreError("timeout", statusCode = 0)
        coEvery { http.getJson(any(), any(), any()) } coAnswers {
            searchCookies += cookieOf(thirdArg())
            JSONObject("""{"data":[]}""")
        }
        site(http, issuer, login).searchRooms("游戏", 1)
        assertEquals(listOf(login), searchCookies.map { it.trimEnd(';') })
        assertEquals(0, issuer.issues)
        assertEquals(0, issuer.generation)
    }

    @Test
    fun htmlRoomDetailKeepsLoginCookieWhenHeadPrefetchFails() = runBlocking {
        val issuer = CountingTtwid()
        val login = "sessionid=saved;ttwid=saved-ttwid"
        val pageCookies = mutableListOf<String>()
        val http = mockk<HttpClient>()
        coEvery { http.getJson(any(), any(), any()) } throws CoreError("api down", statusCode = 500)
        coEvery { http.head(any(), any(), any()) } throws CoreError("timeout", statusCode = 0)
        coEvery { http.getText(any(), any(), any()) } coAnswers {
            pageCookies += cookieOf(thirdArg())
            douyinRoomHtml("kept-room")
        }
        val detail = site(http, issuer, login).getRoomDetail("123456")
        assertEquals("kept-room", detail.title)
        assertTrue(detail.status)
        assertEquals(listOf(login), pageCookies.map { it.trimEnd(';') })
        assertEquals(0, issuer.issues)
        assertEquals(0, issuer.generation)
    }

    @Test
    fun htmlRoomDetailRefreshesAnonymousCookieWhenHeadIsRejected() = runBlocking {
        val issuer = CountingTtwid()
        val pageCookies = mutableListOf<String>()
        val http = mockk<HttpClient>()
        coEvery { http.getJson(any(), any(), any()) } throws CoreError("api down", statusCode = 500)
        coEvery { http.head(any(), any(), any()) } coAnswers {
            val cookie = cookieOf(thirdArg())
            if (cookie == "ttwid=gen-0") throw CoreError("rejected", statusCode = 401)
            response(200)
        }
        coEvery { http.getText(any(), any(), any()) } coAnswers {
            pageCookies += cookieOf(thirdArg())
            douyinRoomHtml("refreshed-room")
        }
        val detail = site(http, issuer).getRoomDetail("123456")
        assertEquals("refreshed-room", detail.title)
        assertEquals(listOf("ttwid=gen-1"), pageCookies.map { it.trimEnd(';') })
        assertTrue(issuer.generation >= 1)
    }

    private fun douyinRoomHtml(title: String): String {
        val json = """
            {"state":{"appStore":{},"roomStore":{"roomInfo":{"room":{"id_str":"99","status":2,"title":"$title","owner":{"nickname":"anchor","signature":"hi","avatar_thumb":{"url_list":["https://img.example/a"]}},"cover":{"url_list":["https://img.example/c"]},"room_view_stats":{"display_value":7}},"anchor":{}}},"userStore":{"odin":{"user_unique_id":"42"}}}}
        """.trim()
        return json.replace("\"", "\\\"") + "]\\n"
    }

    private fun cookieOf(headers: Any): String {
        val map = headers as Map<*, *>
        return map.entries.first { it.key.toString().equals("cookie", ignoreCase = true) }.value.toString()
    }

    private fun response(code: Int): Response = Response.Builder()
        .request(Request.Builder().url("https://live.douyin.com/").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("status")
        .body("".toResponseBody(null))
        .build()

    private fun site(http: HttpClient, ttwid: DouyinTtwidSource, accountCookie: String = ""): DouyinSite {
        val js = object : JsExecutor {
            override suspend fun execute(scriptName: String, functionName: String, vararg args: String) = "sig"
            override suspend fun executeRaw(script: String, functionName: String, vararg args: String) = "sig"
            override suspend fun executeWithRetry(
                scriptName: String,
                functionName: String,
                args: Array<String>,
                maxAttempts: Int,
                isValid: (String) -> Boolean
            ) = "sig"
            override fun loadAssetScript(scriptName: String) = ""
        }
        return DouyinSite(http, Provider { js }, OkHttpClient(), { accountCookie }, ttwid)
    }

    private class CountingTtwid : DouyinTtwidSource {
        var generation = 0
        var issues = 0
        override suspend fun current(forceRefresh: Boolean): String {
            if (forceRefresh) generation++
            issues++
            return "ttwid=gen-$generation"
        }
        override fun invalidate() {
            generation++
        }
    }
}
