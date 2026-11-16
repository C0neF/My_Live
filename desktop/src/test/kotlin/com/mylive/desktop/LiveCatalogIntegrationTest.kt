package com.mylive.desktop

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real anonymous endpoints, opt-in so a third-party outage cannot break ordinary unit tests. */
class LiveCatalogIntegrationTest {
    private fun probe(id: String) = runBlocking {
        assumeTrue("Pass -PdesktopCatalogLive=true for real platform endpoints", System.getProperty("mylive.catalogLive") == "true")
        DesktopServices().use { services -> withTimeout(50_000) {
            val site = services.sites.first { it.id == id }
            val recommended = site.getRecommendRooms(1)
            assertTrue("$id recommendation must return rooms", recommended.items.isNotEmpty())
            val categories = site.getCategories()
            assertTrue("$id categories must return content", categories.any { it.children.isNotEmpty() })
            val item = recommended.items.first()
            val detail = site.getRoomDetail(item.roomId)
            assertTrue(detail.roomId.isNotBlank())
            val category = categories.first { it.children.isNotEmpty() }.children.first()
            val categorized = site.getCategoryRooms(category, 1)
            if (id == "douyin") {
                try { site.searchRooms(item.userName.take(10), 1) }
                catch (failure: Exception) {
                    check(failure.message.orEmpty().contains("需要登录")) { "Unexpected Douyin search failure" }
                    println("douyin: recommended=${recommended.items.size}, categories=${categories.sumOf { it.children.size }}, categoryRooms=${categorized.items.size}, sampleRoom=${detail.roomId}, anonymous search correctly requires login")
                    return@withTimeout
                }
            }
            val search = site.searchRooms(item.userName.take(10), 1)
            val anchors = site.searchAnchors(item.userName.take(10), 1)
            println("$id: recommended=${recommended.items.size}, categories=${categories.sumOf { it.children.size }}, categoryRooms=${categorized.items.size}, searchRooms=${search.items.size}, anchors=${anchors.items.size}, sampleRoom=${detail.roomId}")
        } }
    }
    @Test fun bilibili() = probe("bilibili")
    @Test fun douyu() = probe("douyu")
    @Test fun huya() = probe("huya")
    @Test fun douyin() = probe("douyin")
}
