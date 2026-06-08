package com.mylive.app.core.site.douyin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.script.JsEngine
import okhttp3.OkHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Provider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DouyinSiteTest {

    @Test
    fun testDouyinSiteDetail() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val jsEngine = JsEngine(context)
        val jsEngineProvider = Provider { jsEngine }
        val okHttpClient = OkHttpClient()
        val httpClient = HttpClient(okHttpClient)
        val douyinSite = DouyinSite(httpClient, jsEngineProvider, okHttpClient)

        // Using a known room webRid or search for one
        val searchResult = try {
            douyinSite.searchRooms("游戏", 1)
        } catch (e: Exception) {
            println("Search failed: $e")
            e.printStackTrace()
            null
        }

        val webRid = searchResult?.items?.firstOrNull()?.roomId ?: "416144012050"
        println("Testing Douyin webRid: $webRid")

        try {
            val detail = douyinSite.getRoomDetail(webRid)
            println("Room Detail Title: ${detail.title}")
            println("Room Status: ${detail.status}")
            if (detail.status) {
                val qualities = douyinSite.getPlayQualites(detail)
                println("Qualities: $qualities")
                if (qualities.isNotEmpty()) {
                    val playUrl = douyinSite.getPlayUrls(detail, qualities.first())
                    println("Play URLs: ${playUrl.urls}")
                }
            }
        } catch (e: Exception) {
            println("Error during getRoomDetail/getPlayUrls: $e")
            e.printStackTrace()
        } finally {
            jsEngine.destroy()
        }
    }
}
