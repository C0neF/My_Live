package com.mylive.app.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedModelInvariantsTest {
    private val json = Json { encodeDefaults = false }

    @Test
    fun roomItemOmitsDefaultsAndReadsThemBack() {
        val item = LiveRoomItem(roomId = "1", title = "标题", cover = "https://img", userName = "主播")
        val encoded = json.encodeToString(LiveRoomItem.serializer(), item)
        assertFalse(encoded.contains("faceUrl"))
        assertFalse(encoded.contains("online"))
        val decoded = json.decodeFromString(LiveRoomItem.serializer(), encoded)
        assertEquals("", decoded.faceUrl)
        assertEquals(0, decoded.online)
        assertEquals(item, decoded)
    }

    @Test
    fun roomItemKeepsExplicitFaceAndOnline() {
        val wire = """{"roomId":"9","title":"t","cover":"c","userName":"u","faceUrl":"https://face","online":12}"""
        val decoded = json.decodeFromString(LiveRoomItem.serializer(), wire)
        assertEquals("https://face", decoded.faceUrl)
        assertEquals(12, decoded.online)
    }

    @Test
    fun roomDetailRoundTripPreservesNullableCategoryFields() {
        val detail = LiveRoomDetail(
            roomId = "416144012050",
            title = "直播",
            cover = "https://cover",
            userName = "主播",
            status = true,
            online = 3,
            categoryId = "720",
            categoryName = "游戏",
            categoryParentId = null,
            notice = "公告"
        )
        val decoded = json.decodeFromString(
            LiveRoomDetail.serializer(),
            json.encodeToString(LiveRoomDetail.serializer(), detail)
        )
        assertEquals(detail, decoded)
        assertTrue(decoded.status)
        assertNull(decoded.categoryParentId)
        assertEquals("公告", decoded.notice)
    }

    @Test
    fun partialRoomDetailJsonUsesModelDefaults() {
        val decoded = json.decodeFromString(
            LiveRoomDetail.serializer(),
            """{"roomId":"1","title":"t","cover":"c","userName":"u"}"""
        )
        assertEquals("", decoded.userAvatar)
        assertFalse(decoded.status)
        assertFalse(decoded.isRecord)
        assertNull(decoded.introduction)
        assertNull(decoded.categoryPic)
    }

    @Test
    fun categoryTreeRoundTripsChildren() {
        val category = LiveCategory(
            name = "游戏",
            id = "1",
            children = listOf(
                LiveSubCategory(name = "主机", id = "1-2", parentId = "1", pic = "https://pic"),
                LiveSubCategory(name = "无图", id = "1-3")
            )
        )
        val decoded = json.decodeFromString(
            LiveCategory.serializer(),
            json.encodeToString(LiveCategory.serializer(), category)
        )
        assertEquals(category, decoded)
        assertEquals("1", decoded.children[0].parentId)
        assertEquals("https://pic", decoded.children[0].pic)
        assertEquals("", decoded.children[1].parentId)
        assertNull(decoded.children[1].pic)
    }

    @Test
    fun searchAndCategoryPagesDefaultToEmpty() {
        assertEquals(
            LiveSearchRoomResult(),
            json.decodeFromString(LiveSearchRoomResult.serializer(), "{}")
        )
        assertEquals(
            LiveSearchAnchorResult(),
            json.decodeFromString(LiveSearchAnchorResult.serializer(), "{}")
        )
        assertEquals(
            LiveCategoryResult(),
            json.decodeFromString(LiveCategoryResult.serializer(), "{}")
        )
        val anchor = json.decodeFromString(
            LiveAnchorItem.serializer(),
            """{"roomId":"7","userName":"主播"}"""
        )
        assertEquals("", anchor.avatar)
        assertFalse(anchor.liveStatus)
    }

    @Test
    fun messageColorReadsRgbBytesAndIgnoresTheAlphaByte() {
        assertEquals(LiveMessageColor(0x12, 0x34, 0x56), LiveMessageColor.numberToColor(0x123456))
        assertEquals(LiveMessageColor.WHITE, LiveMessageColor.numberToColor(0xFFFFFF))
        assertEquals(LiveMessageColor(0x00, 0x00, 0x00), LiveMessageColor.numberToColor(0))
        // 0xFFABCDEF is negative as a signed Int. The shift must still yield the RGB bytes.
        assertEquals(LiveMessageColor(0xAB, 0xCD, 0xEF), LiveMessageColor.numberToColor(0xFFABCDEF.toInt()))
    }
}
