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
class DouyinLiveStatusPolicyTest {

    @Test
    fun parsesKnownStatusFieldsWithoutTreatingMissingDataAsOffline() {
        assertEquals(true, parseDouyinLiveStatus(JSONObject("""{"status":2}""")))
        assertEquals(false, parseDouyinLiveStatus(JSONObject("""{"live_status":"4"}""")))
        assertEquals(true, parseDouyinLiveStatus(JSONObject("""{"status_str":{"room_status":2}}""")))
        assertNull(parseDouyinLiveStatus(JSONObject("""{"title":"unknown"}""")))
    }

    @Test
    fun lookupOrderPrefersTheIdentifierShapeButKeepsOneFallback() {
        assertEquals(
            listOf(DouyinStatusLookupRoute.WebRid, DouyinStatusLookupRoute.RoomId),
            douyinStatusLookupOrder("416144012050")
        )
        assertEquals(
            listOf(DouyinStatusLookupRoute.RoomId, DouyinStatusLookupRoute.WebRid),
            douyinStatusLookupOrder("7376429659866598196")
        )
    }

    @Test
    fun unrecognizedStatusesRemainUnknownInsteadOfOffline() {
        listOf(0, 1, 3, -1, 999).forEach { status ->
            assertNull(parseDouyinLiveStatus(JSONObject().put("status", status)))
        }
        assertEquals(true, parseDouyinLiveStatus(JSONObject("""{"status":0,"live_status":2}""")))
        assertEquals(false, parseDouyinLiveStatus(JSONObject("""{"status_str":{"status":0,"room_status":4}}""")))
        assertNull(parseDouyinLiveStatus(JSONObject("""{"status":2.9}""")))
        assertNull(parseDouyinLiveStatus(JSONObject().put("status", 4_294_967_298L)))
    }
}
