package com.mylive.app.ui.screen.sync

import com.mylive.app.core.common.LanSyncAuth
import com.mylive.app.core.common.LanSyncCrypto
import com.mylive.app.core.sync.LanSyncProtocol
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.repository.ProfileBackupManager
import com.mylive.app.data.sync.LanSyncClient
import com.mylive.app.service.decodeFollowsForLanSync
import com.mylive.app.testing.ReviewRoomFixture
import io.mockk.mockk
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncDeviceFollowRoundTripTest : ReviewRoomFixture() {
    @Test
    fun singleFollowSyncPreservesIdentityAndPresenceThroughTheProductionDecoder() = runBlocking {
        val original = FollowUserEntity(
            id = "douyin_old", roomId = "old", siteId = "douyin", userName = "anchor",
            face = "avatar", addTime = 1L, tag = "group", isSpecialFollow = true,
            liveStatus = 1, liveStartTime = 123L, showTime = "123", canonicalRoomId = "canonical"
        )
        follows.addFollow(original)
        val payloads = ConcurrentHashMap<String, String>()
        val token = "test-only-pairing-token"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val body = if (path == LanSyncProtocol.INFO_PATH) {
                LanSyncProtocol.encodePeerInfo(paired = true, port = 23234, deviceId = "peer",
                    supportsFollowBundle = true)
            } else {
                val encrypted = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                val context = LanSyncAuth.verifyProof(token, request.header(LanSyncAuth.HEADER_NAME),
                    request.method, path, false, encrypted)
                payloads[path] = LanSyncCrypto.decrypt(token, encrypted, context)
                """{"status":true}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Test peer")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val viewModel = own(SyncDeviceViewModel(follows, history, shields,
                mockk<ProfileBackupManager>(), LanSyncClient(client)))
            viewModel.syncFollow("127.0.0.1", 23234, token)
            awaitWork(viewModel)
            val array = JSONObject(payloads["/sync/follow"] ?: error("${viewModel.syncResults.value}"))
                .getJSONArray("follows")
            val decoded = decodeFollowsForLanSync(array)
            assertEquals("The real sender must retain the canonical identity and presence fields",
                listOf(original), decoded)

            follows.clearAllFollows()
            follows.importFollows(decoded, overlay = false)
            assertEquals(listOf(original), follows.getAllFollows().first())
            assertTrue(follows.isFollowing("douyin", "canonical"))
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
