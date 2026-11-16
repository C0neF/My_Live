package com.mylive.app.core.site.huya

import com.mylive.app.core.common.HttpClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class HuyaNumericRoomIdTest {
    private val http = mockk<HttpClient>()
    private val site = HuyaSite(http, OkHttpClient())
    @Test fun roomSearchAcceptsNumericIdsWithTheJvmJsonImplementation() = runBlocking {
        coEvery { http.getText(any(), any(), any()) } returns """{"response":{"3":{"numFound":2,"docs":[
            {"room_id":998,"game_screenshot":"https://example.org/image","game_nick":"A"},
            {"room_id":"999","game_screenshot":"https://example.org/image","game_nick":"B"}]}}}"""
        assertEquals(listOf("998", "999"), site.searchRooms("query", 1).items.map { it.roomId })
    }
    @Test fun anchorSearchAcceptsNumericIds() = runBlocking {
        coEvery { http.getText(any(), any(), any()) } returns """{"response":{"1":{"numFound":1,"docs":[{"room_id":998,"game_nick":"A","gameLiveOn":true}]}}}"""
        assertEquals("998", site.searchAnchors("query", 1).items.single().roomId)
    }
    @Test fun recommendationsAcceptNumericProfileRoomIds() = runBlocking {
        coEvery { http.getText(any(), any(), any()) } returns """{"data":{"page":1,"totalPage":1,"datas":[{"profileRoom":998,"screenshot":"https://example.org/image","nick":"A"}]}}"""
        assertEquals("998", site.getRecommendRooms(1).items.single().roomId)
    }
}
