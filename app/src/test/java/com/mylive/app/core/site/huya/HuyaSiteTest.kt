package com.mylive.app.core.site.huya

import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.model.LivePlayQuality
import okhttp3.OkHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HuyaSiteTest {

    @Test
    fun testHuyaSitePlayUrls() = runBlocking {
        val okHttpClient = OkHttpClient()
        val httpClient = HttpClient(okHttpClient)
        val huyaSite = HuyaSite(httpClient, okHttpClient)

        // Using a known room ID. A popular category room like a gaming room or a default room ID.
        // Let's first search for rooms, or just try a standard room.
        val searchResult = try {
            huyaSite.searchRooms("楚河", 1)
        } catch (e: Exception) {
            println("Search failed: $e")
            e.printStackTrace()
            null
        }

        val roomId = searchResult?.items?.firstOrNull()?.roomId ?: "996" // 996 is a common room
        println("Testing roomId: $roomId")

        try {
            val detail = huyaSite.getRoomDetail(roomId)
            println("Room Detail Title: ${detail.title}")
            println("Room Status: ${detail.status}")
            
            if (detail.status) {
                val qualities = huyaSite.getPlayQualites(detail)
                println("Qualities: $qualities")
                if (qualities.isNotEmpty()) {
                    val playUrl = huyaSite.getPlayUrls(detail, qualities.first())
                    println("Play URLs: ${playUrl.urls}")
                }
            } else {
                println("Room is offline, trying to get play URLs anyway...")
                val dummyQuality = LivePlayQuality("原画", com.mylive.app.core.model.PlayQualityData.Huya(0, 0))
                val playUrl = huyaSite.getPlayUrls(detail, dummyQuality)
                println("Play URLs: ${playUrl.urls}")
            }
        } catch (e: Exception) {
            println("Error during getRoomDetail/getPlayUrls: $e")
            e.printStackTrace()
        }
    }
}
