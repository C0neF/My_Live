package com.mylive.app.core.site.douyu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DouyuSearchPolicyTest {

    @Test
    fun douyuSearchHasMoreRequiresFullPage() {
        val source = File("src/main/java/com/mylive/app/core/site/douyu/DouyuSite.kt").readText()

        assertTrue(source.contains("private fun douyuSearchHasMore(itemCount: Int): Boolean"))
        assertTrue(source.contains("return itemCount >= DOUYU_SEARCH_PAGE_SIZE"))
        assertFalse(source.contains("val hasMore = relateShow.length() > 0"))
        assertFalse(source.contains("val hasMore = relateUser.length() > 0"))
    }
}
