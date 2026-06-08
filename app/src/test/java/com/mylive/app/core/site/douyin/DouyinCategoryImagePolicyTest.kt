package com.mylive.app.core.site.douyin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DouyinCategoryImagePolicyTest {

    @Test
    fun partitionImageResolverUsesUrlListInsteadOfOpaqueUri() {
        val partition = JSONObject(
            """
            {
              "id_str": "720",
              "title": "热门",
              "icon": {
                "uri": "webcast/category/hot",
                "url_list": ["//p3-webcast.douyinpic.com/category-hot.webp"]
              }
            }
            """.trimIndent()
        )

        assertEquals(
            "https://p3-webcast.douyinpic.com/category-hot.webp",
            resolveDouyinPartitionImageUrl(partition)
        )
    }

    @Test
    fun partitionImageResolverIgnoresNonUrlStrings() {
        val partition = JSONObject(
            """
            {
              "id_str": "720",
              "title": "热门",
              "icon": {
                "uri": "webcast/category/hot"
              }
            }
            """.trimIndent()
        )

        assertNull(resolveDouyinPartitionImageUrl(partition))
    }
}
