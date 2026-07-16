package com.mylive.app.core.site.huya

import com.mylive.app.core.common.WebSocketUtils
import com.mylive.app.core.site.huya.tars.TarsInputStream
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HuyaDanmakuEncodingTest {

    @Test
    fun joinDataMatchesDartReferenceBytes() {
        val danmaku = HuyaDanmaku(WebSocketUtils(OkHttpClient()))

        val bytes = danmaku.encodeJoinData(
            ayyuid = 229_813_522L,
            tid = 294_636_272L,
            sid = 294_636_272L
        )
        val hex = bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

        assertEquals(
            "00011d000017020db2ad1210012600360042118fcaf052118fcaf06c7c",
            hex
        )
    }

    @Test
    fun joinDataPreservesAyyuidAboveSignedIntRange() {
        val largeAyyuid = 2_272_348_727L
        val topSid = 2_272_316_519L
        val danmaku = HuyaDanmaku(WebSocketUtils(OkHttpClient()))

        val bytes = danmaku.encodeJoinData(largeAyyuid, topSid, topSid)
        val outer = TarsInputStream(bytes)
        assertEquals(1L, outer.readInt(0, true))
        val inner = TarsInputStream(outer.readBytes(1, true))

        assertEquals(largeAyyuid, inner.readInt(0, true))
    }
}
