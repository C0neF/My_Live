package com.mylive.app.ui.screen.sync

import com.mylive.app.data.local.entity.ShieldEntity
import org.junit.Assert.assertEquals
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
}
