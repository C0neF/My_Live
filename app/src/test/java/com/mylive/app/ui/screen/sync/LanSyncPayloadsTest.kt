package com.mylive.app.ui.screen.sync

import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.local.entity.ShieldEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LanSyncPayloadsTest {

    @Test
    fun encodeShieldKeywordsForLanSyncSendsOnlyKeywordStrings() {
        val payload = encodeShieldKeywordsForLanSync(
            listOf(
                ShieldEntity(id = 1, value = "keyword:广告"),
                ShieldEntity(id = 2, value = "user:bilibili:someone"),
                ShieldEntity(id = 3, value = "keyword:测试")
            )
        )

        assertEquals("""["广告","测试"]""", payload)
    }

    @Test
    fun decodeLanSyncShieldKeywordsReadsStringPayload() {
        val keywords = decodeLanSyncShieldKeywords("""["广告","测试"]""")

        assertEquals(listOf("广告", "测试"), keywords)
    }

    @Test
    fun decodeLanSyncShieldKeywordsReadsLegacyObjectPayload() {
        val keywords = decodeLanSyncShieldKeywords(
            """[{"id":1,"value":"keyword:广告"},{"id":2,"value":"user:bilibili:someone"}]"""
        )

        assertEquals(listOf("广告"), keywords)
    }

    @Test
    fun encodeFollowTagsForLanSyncUsesTagAndUserIdsKeys() {
        val payload = encodeFollowTagsForLanSync(
            listOf(
                FollowUserTagEntity(
                    id = "tag-1",
                    tag = "Favorites",
                    userIds = listOf("bilibili_1", "douyin_2")
                )
            )
        )

        assertEquals("""[{"id":"tag-1","tag":"Favorites","userIds":["bilibili_1","douyin_2"]}]""", payload)
    }

    @Test
    fun decodeFollowTagsForLanSyncReadsCurrentPayload() {
        val tags = decodeFollowTagsForLanSync(
            """[{"id":"tag-1","tag":"Favorites","userIds":["bilibili_1","douyin_2"]}]"""
        )

        assertEquals(
            listOf(FollowUserTagEntity(id = "tag-1", tag = "Favorites", userIds = listOf("bilibili_1", "douyin_2"))),
            tags
        )
    }

    @Test
    fun decodeFollowTagsForLanSyncReadsLegacyNameAndUserIdPayload() {
        val tags = decodeFollowTagsForLanSync(
            """[{"id":"tag-1","name":"Favorites","userId":["bilibili_1"]}]"""
        )

        assertEquals(
            listOf(FollowUserTagEntity(id = "tag-1", tag = "Favorites", userIds = listOf("bilibili_1"))),
            tags
        )
    }

    @Test
    fun decodeFollowTagsForLanSyncRejectsMissingTagName() {
        assertThrows(IllegalArgumentException::class.java) {
            decodeFollowTagsForLanSync("""[{"id":"tag-1","userIds":["bilibili_1"]}]""")
        }
    }

    @Test
    fun validateLanSyncResponseThrowsWhenJsonStatusIsFalse() {
        assertThrows(Exception::class.java) {
            validateLanSyncResponse(
                statusCode = 200,
                isSuccessful = true,
                body = """{"status":false,"message":"tag failed"}"""
            )
        }
    }

    @Test
    fun validateLanSyncResponseReturnsAcceptedJobForAsyncImport() {
        val acceptedJob = validateLanSyncResponse(
            statusCode = 202,
            isSuccessful = true,
            body = """{"status":true,"jobUrl":"/sync/job/job-1"}"""
        )

        assertEquals(LanSyncAcceptedJob(jobUrl = "/sync/job/job-1"), acceptedJob)
    }
}
