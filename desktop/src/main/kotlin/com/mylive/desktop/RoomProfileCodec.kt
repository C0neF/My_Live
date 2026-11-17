package com.mylive.desktop

import com.mylive.app.core.common.MAX_IMPORTED_JSON_BYTES
import com.mylive.app.core.sync.ProfileBackupFormat
import com.mylive.app.core.common.readUtf8TextWithinLimit
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.time.Instant

data class RoomProfileImport(val follows: List<SavedRoom>, val history: List<SavedRoom>,
    val preferences: DesktopProfilePreferences? = null,
    val sections: Set<String> = setOf("player", "danmaku", "appearance", "followOptions"),
    val shieldsOnly: Boolean = false)

/** Android ProfileBackupManager v1–v3 room sections and its legacy follow exports. */
object RoomProfileCodec {
    fun read(file: Path, now: Long = System.currentTimeMillis()): RoomProfileImport =
        Files.newInputStream(file).use { parse(it.readUtf8TextWithinLimit(), now) }

    fun parse(text: String, now: Long = System.currentTimeMillis()): RoomProfileImport {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_IMPORTED_JSON_BYTES) { "备份文件不能超过 5 MB" }
        val tokener = JSONTokener(text.removePrefix("\uFEFF").trim())
        val root = tokener.nextValue()
        require(tokener.nextClean() == '\u0000') { "备份内容不是有效的 JSON" }
        if (root is JSONArray) return RoomProfileImport(mergeSavedRooms(rows(root, false, now)), emptyList())
        require(root is JSONObject) { "请选择 Android 导出的 JSON 备份" }
        if (root.has("type")) require(root.get("type") == ProfileBackupFormat.TYPE) { "备份类型不支持" }
        if (root.has("type") || root.has("schemaVersion")) {
            val version = root.opt("schemaVersion")
            require(version is Number && version.toDouble() in
                ProfileBackupFormat.MIN_SCHEMA_VERSION.toDouble()..ProfileBackupFormat.SCHEMA_VERSION.toDouble() &&
                version.toDouble() % 1.0 == 0.0) {
                "备份版本不支持，请使用版本 1–3 的资料备份"
            }
        }
        val boxes = if (root.has("boxes")) root.getJSONObject("boxes") else null
        for (container in listOfNotNull(root, boxes)) {
            for (key in listOf("desktop", "settings", "config")) if (container.has(key)) require(container.get(key) is JSONObject) { "$key 格式不正确" }
            if (container.has("followUserTags")) require(container.get("followUserTags") is JSONArray) { "分组格式不正确" }
        }
        val follows = mutableListOf<SavedRoom>()
        val history = mutableListOf<SavedRoom>()
        var found = false
        for (container in listOfNotNull(root, boxes)) {
            for (key in listOf("followUsers", "follows")) if (container.has(key)) {
                follows += rows(container.getJSONArray(key), false, now)
                found = true
            }
            if (container.has("histories")) {
                history += rows(container.getJSONArray("histories"), true, now)
                found = true
            }
        }
        require(found || root.has("desktop") || root.has("settings") || root.has("danmuShield")) { "备份中没有可导入的资料" }
        return DesktopProfileCodec.decode(root, mergeSavedRooms(follows), mergeSavedRooms(history))
    }

    fun export(state: DesktopLibraryState): String {
        require(state.loaded && state.writable) { "本地资料尚未成功读取" }
        fun row(room: SavedRoom, history: Boolean): JSONObject = JSONObject()
            .put("id", "${room.siteId}_${room.roomId}")
            .put("siteId", room.siteId).put("roomId", room.roomId)
            .put("userName", room.userName).put("face", room.userAvatar)
            .put("title", room.title).put("cover", room.cover)
            .put(if (history) "updateTime" else "addTime", room.savedAt)
            .apply {
                if (!history && room.siteId == "douyin") put("canonicalRoomId", room.roomId)
                if (room.pinned) put("isSpecialFollow", true)
                if (room.note.isNotEmpty()) put("note", room.note)
                if (room.aliases.isNotEmpty()) put("aliases", JSONArray(room.aliases))
                if (room.liveStatus != 0) put("liveStatus", room.liveStatus)
                if (room.lastCheckedAt != 0L) put("lastCheckedAt", room.lastCheckedAt)
            }
        val root = JSONObject().put("type", ProfileBackupFormat.TYPE).put("schemaVersion", ProfileBackupFormat.SCHEMA_VERSION)
            .put("exportTime", Instant.now().toString())
            .put("followUsers", JSONArray().apply { state.follows.forEach { put(row(it, false)) } })
            .put("histories", JSONArray().apply { state.history.forEach { put(row(it, true)) } })
        DesktopProfileCodec.encode(root, state)
        val text = root.toString(2)
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_IMPORTED_JSON_BYTES) { "备份超过 5 MB，请减少记录后重试" }
        return text
    }

    fun writeNew(file: Path, state: DesktopLibraryState) {
        val bytes = export(state).toByteArray(Charsets.UTF_8)
        var created = false
        try {
            Files.newOutputStream(file, CREATE_NEW, WRITE).use { output ->
                created = true
                output.write(bytes)
            }
        } catch (error: Exception) {
            if (created) Files.deleteIfExists(file)
            throw error
        }
    }

    private fun rows(array: JSONArray, history: Boolean, now: Long): List<SavedRoom> = List(array.length()) { index ->
        val row = array.getJSONObject(index)
        val site = string(row, "siteId", "site")
        require(site in setOf("bilibili", "douyu", "huya", "douyin")) { "记录 ${index + 1} 的平台不支持" }
        val original = roomId(row.opt("roomId") ?: row.opt("room"))
        val canonicalText = if (site == "douyin") string(row, "canonicalRoomId") else ""
        val canonical = if (canonicalText.isNotBlank()) roomId(canonicalText) else original
        SavedRoom(siteId = site, roomId = canonical, userName = string(row, "userName", "name"),
            title = string(row, "title"),
            cover = string(row, "cover"), userAvatar = string(row, "face", "avatar"),
            savedAt = timestamp(row.opt(if (history) "updateTime" else "addTime"), now),
            aliases = ((if (original != canonical) listOf(original) else emptyList()) +
                (if (row.has("aliases")) row.getJSONArray("aliases").let { values -> List(values.length()) { roomId(values.get(it)) } } else emptyList())).distinct().take(16),
            pinned = if (row.has("isSpecialFollow")) row.get("isSpecialFollow").let {
                require(it is Boolean) { "置顶关注格式不正确" }; it
            } else false,
            tags = string(row, "tag").takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(), note = string(row, "note"),
            liveStatus = if (row.has("liveStatus")) row.get("liveStatus").let { require(it is Number && it.toDouble() % 1 == 0.0 && it.toInt() in 0..2); it.toInt() } else 0,
            lastCheckedAt = timestamp(row.opt("lastCheckedAt"), 0).also { require(it >= 0) })
    }

    private fun string(row: JSONObject, vararg keys: String): String {
        val key = keys.firstOrNull { row.has(it) && !row.isNull(it) } ?: return ""
        return (row.get(key) as? String) ?: throw IllegalArgumentException("记录的 $key 必须是文本")
    }

    private fun roomId(value: Any?): String {
        val text = when (value) {
            is String -> value
            is Number -> value.toLong().toString().also {
                require(value.toDouble().isFinite() && value.toDouble() == value.toLong().toDouble()) { "房间号格式不正确" }
            }
            else -> throw IllegalArgumentException("记录缺少房间号")
        }
        require(text.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "房间号格式不正确" }
        return text
    }

    private fun timestamp(value: Any?, fallback: Long): Long = when (value) {
        null, JSONObject.NULL -> fallback
        is Number -> value.toLong().also { require(value.toDouble().isFinite() && value.toDouble() == it.toDouble()) { "时间格式不正确" } }
        is String -> value.toLongOrNull() ?: runCatching { Instant.parse(value).toEpochMilli() }
            .getOrElse { throw IllegalArgumentException("时间格式不正确") }
        else -> throw IllegalArgumentException("时间格式不正确")
    }
}

/** Merge aliases transitively, preserve richer local metadata, and keep the newest visit time. */
internal fun mergeSavedRooms(rooms: List<SavedRoom>): List<SavedRoom> {
    val merged = mutableListOf<SavedRoom>()
    rooms.sortedByDescending { it.savedAt }.forEach { candidate ->
        var room = candidate
        var index = 0
        while (index < merged.size) {
            val previous = merged[index]
            val identities = (listOf(room.roomId) + room.aliases).toSet()
            if (previous.siteId == room.siteId && (listOf(previous.roomId) + previous.aliases).any { it in identities }) {
                merged.removeAt(index)
                val canonical = if (room.roomId in previous.aliases) previous.roomId else room.roomId
                val newer = if (previous.savedAt >= room.savedAt) previous else room
                val older = if (newer === previous) room else previous
                room = newer.copy(roomId = canonical,
                    userName = newer.userName.ifBlank { older.userName }, title = newer.title.ifBlank { older.title },
                    cover = newer.cover.ifBlank { older.cover }, userAvatar = newer.userAvatar.ifBlank { older.userAvatar },
                    pinned = newer.pinned || older.pinned, tags = (newer.tags + older.tags).distinct(), note = newer.note.ifBlank { older.note },
                    aliases = (listOf(previous.roomId, room.roomId) + previous.aliases + room.aliases)
                        .filter { it != canonical }.distinct().take(16))
                index = 0
            } else index++
        }
        merged.add(room)
    }
    return merged.sortedByDescending { it.savedAt }
}
