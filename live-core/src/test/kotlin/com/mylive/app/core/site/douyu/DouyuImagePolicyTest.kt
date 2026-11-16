package com.mylive.app.core.site.douyu

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DouyuImagePolicyTest {

    @Test
    fun roomFaceUrlPrefersAvatarFieldsAndNormalizesProtocolRelativeUrl() {
        val item = JSONObject(
            """
            {
              "avatar_mid": "//apic.douyucdn.cn/upload/avatar_mid.jpg",
              "rs16": "https://rpic.douyucdn.cn/live-cover.jpg"
            }
            """.trimIndent()
        )

        assertEquals(
            "https://apic.douyucdn.cn/upload/avatar_mid.jpg",
            resolveDouyuRoomFaceUrl(item)
        )
    }

    @Test
    fun roomFaceUrlUsesDouyuListAvatarField() {
        val item = JSONObject(
            """
            {
              "av": "https://apic.douyucdn.cn/upload/avatar_v3/202606/avatar_middle.jpg",
              "rs16": "https://rpic.douyucdn.cn/live-cover.jpg"
            }
            """.trimIndent()
        )

        assertEquals(
            "https://apic.douyucdn.cn/upload/avatar_v3/202606/avatar_middle.jpg",
            resolveDouyuRoomFaceUrl(item)
        )
    }

    @Test
    fun roomFaceUrlDoesNotFallbackToCoverWhenListResponseHasNoAvatar() {
        val item = JSONObject(
            """
            {
              "rid": 123,
              "nn": "anchor",
              "rs16": "//rpic.douyucdn.cn/live-cover.jpg"
            }
            """.trimIndent()
        )

        assertEquals(
            "",
            resolveDouyuRoomFaceUrl(item)
        )
    }
}
