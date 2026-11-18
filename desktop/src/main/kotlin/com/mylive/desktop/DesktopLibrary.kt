package com.mylive.desktop

import com.mylive.app.core.model.LiveRoomDetail
import com.mylive.app.core.site.FollowPresence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.*

@Serializable
data class SavedRoom(
    val siteId: String,
    val roomId: String,
    val userName: String,
    val title: String,
    val cover: String = "",
    val userAvatar: String = "",
    val savedAt: Long,
    val aliases: List<String> = emptyList(),
    val pinned: Boolean = false,
    val tags: List<String> = emptyList(),
    val note: String = "",
    val liveStatus: Int = 0,
    val lastCheckedAt: Long = 0
) {
    val key: String get() = "$siteId:$roomId"
    val address: RoomAddress get() = RoomAddress(siteId, roomId)
    fun matches(address: RoomAddress, detail: LiveRoomDetail? = null): Boolean =
        siteId == address.siteId && listOfNotNull(address.roomId, detail?.roomId)
            .any { it == roomId || it in aliases }
}

@Serializable
internal data class SavedLibrary(
    val version: Int = 1,
    val follows: List<SavedRoom> = emptyList(),
    val history: List<SavedRoom> = emptyList(),
    val player: DesktopPlayerPreferences = DesktopPlayerPreferences(),
    val followOptions: DesktopFollowPreferences = DesktopFollowPreferences(),
    val danmaku: DesktopDanmakuPreferences = DesktopDanmakuPreferences(),
    val appearance: DesktopAppearancePreferences = DesktopAppearancePreferences()
)

@Serializable
data class DesktopFollowPreferences(val autoRefresh: Boolean = true, val intervalSeconds: Int = 60)

@Serializable
data class DesktopPlayerPreferences(
    val volume: Float = 70f,
    val danmakuEnabled: Boolean = true,
    val chatVisible: Boolean = true,
    val chatAutoScroll: Boolean = true,
    val muted: Boolean = false,
    val hardwareDecode: Boolean = true,
    val scale: DesktopVideoScale = DesktopVideoScale.Fit,
    val qualityLevel: Int = 0,
    val rememberQuality: Boolean = true,
    val qualityBySite: Map<String, String> = emptyMap(),
    val superChatVisible: Boolean = true,
    val chatTextSize: Float = 13f,
    val alwaysOnTop: Boolean = false,
    val minimizeToTray: Boolean = false,
    val backgroundPlayback: Boolean = false,
    val autoStopEnabled: Boolean = false,
    val autoStopMinutes: Int = 60
)

data class DesktopLibraryState(
    val loaded: Boolean = false,
    val writable: Boolean = true,
    val follows: List<SavedRoom> = emptyList(),
    val history: List<SavedRoom> = emptyList(),
    val player: DesktopPlayerPreferences = DesktopPlayerPreferences(),
    val followOptions: DesktopFollowPreferences = DesktopFollowPreferences(),
    val danmaku: DesktopDanmakuPreferences = DesktopDanmakuPreferences(),
    val appearance: DesktopAppearancePreferences = DesktopAppearancePreferences(),
    val error: String? = null
)

/** Small desktop library. File I/O is off the EDT; only durable changes are published to the UI. */
class DesktopLibrary(
    private val file: Path = defaultDesktopLibraryPath(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _state = MutableStateFlow(DesktopLibraryState())
    val state = _state.asStateFlow()

    suspend fun load() = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                publish(read())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                readFailed()
            }
        }
    }

    suspend fun recordVisit(address: RoomAddress, detail: LiveRoomDetail) = update { current ->
        val previous = (current.history + current.follows).filter { it.matches(address, detail) }
        val room = savedRoom(address, detail, previous)
        val followed = current.follows.filter { it.matches(address, detail) }
        current.copy(
            history = (listOf(room) + current.history.filterNot { it.matches(address, detail) }).take(MAX_HISTORY),
            follows = if (followed.isEmpty()) current.follows else
                (current.follows.filterNot { it.matches(address, detail) } + room.copy(savedAt = followed.maxOf { it.savedAt },
                    pinned = followed.any { it.pinned }, tags = followed.flatMap { it.tags }.distinct(),
                    note = followed.firstOrNull { it.note.isNotBlank() }?.note.orEmpty(), liveStatus = if (detail.status) 1 else 2,
                    lastCheckedAt = clock()))
                    .sortedByDescending { it.savedAt }
        )
    }

    suspend fun toggleFollow(address: RoomAddress, detail: LiveRoomDetail) = update { current ->
        if (current.follows.any { it.matches(address, detail) }) {
            current.copy(follows = current.follows.filterNot { it.matches(address, detail) })
        } else {
            val previous = current.history.filter { it.matches(address, detail) }
            current.copy(follows = listOf(savedRoom(address, detail, previous)) + current.follows)
        }
    }

    suspend fun removeFollow(key: String) = update { it.copy(follows = it.follows.filterNot { room -> room.key == key }) }
    suspend fun removeHistory(key: String) = update { it.copy(history = it.history.filterNot { room -> room.key == key }) }
    suspend fun clearHistory() = update { it.copy(history = emptyList()) }

    suspend fun updatePlayer(transform: (DesktopPlayerPreferences) -> DesktopPlayerPreferences) =
        update { it.copy(player = transform(it.player)) }

    suspend fun updateFollowOptions(transform: (DesktopFollowPreferences) -> DesktopFollowPreferences) =
        update { it.copy(followOptions = transform(it.followOptions)) }

    suspend fun updateDanmaku(transform: (DesktopDanmakuPreferences) -> DesktopDanmakuPreferences) =
        update { it.copy(danmaku = transform(it.danmaku)) }

    suspend fun updateAppearance(transform: (DesktopAppearancePreferences) -> DesktopAppearancePreferences) =
        update { it.copy(appearance = transform(it.appearance)) }

    suspend fun editFollow(key: String, pinned: Boolean, tags: List<String>, note: String) = update { current ->
        require(tags.all { it.length <= 80 } && note.length <= 1000)
        current.copy(follows = current.follows.map { room ->
            if (room.key == key) room.copy(pinned = pinned, tags = tags.map(String::trim).filter(String::isNotBlank).distinct(), note = note.trim()) else room
        })
    }

    suspend fun applyPresences(presences: Map<String, FollowPresence>) = update { current ->
        current.copy(follows = mergeSavedRooms(current.follows.map { room ->
            val presence = presences[room.key] ?: return@map room
            val canonical = presence.canonicalRoomId?.takeIf { it.isNotBlank() } ?: room.roomId
            room.copy(roomId = canonical, liveStatus = if (presence.isLive) 1 else 2, lastCheckedAt = clock(),
                aliases = (room.aliases + room.roomId).filter { it != canonical }.distinct().take(16))
        }))
    }

    suspend fun importRooms(plan: RoomProfileImport, replaceFollows: Boolean = false, replaceHistory: Boolean = false) = update { current ->
        current.copy(
            follows = mergeSavedRooms((if (replaceFollows) emptyList() else current.follows) + plan.follows),
            history = mergeSavedRooms((if (replaceHistory) emptyList() else current.history) + plan.history).take(MAX_HISTORY),
            player = plan.preferences?.player?.takeIf { "player" in plan.sections } ?: current.player,
            danmaku = plan.preferences?.danmaku?.takeIf { "danmaku" in plan.sections }?.let {
                if (plan.shieldsOnly) current.danmaku.copy(keywords = it.keywords, users = it.users) else it
            } ?: current.danmaku,
            followOptions = plan.preferences?.followOptions?.takeIf { "followOptions" in plan.sections } ?: current.followOptions,
            appearance = plan.preferences?.appearance?.takeIf { "appearance" in plan.sections } ?: current.appearance
        )
    }

    suspend fun importGroups(groups: Map<String, List<String>>, replace: Boolean) = update { current ->
        current.copy(follows = current.follows.map { room ->
            val imported = (listOf(room.roomId) + room.aliases).flatMap { groups["${room.siteId}_$it"].orEmpty() }
            room.copy(tags = ((if (replace) emptyList() else room.tags) + imported).distinct())
        })
    }

    private fun savedRoom(address: RoomAddress, detail: LiveRoomDetail, previous: List<SavedRoom>): SavedRoom = SavedRoom(
        siteId = address.siteId,
        roomId = detail.roomId.ifBlank { address.roomId },
        userName = detail.userName,
        title = detail.title,
        cover = detail.cover,
        userAvatar = detail.userAvatar,
        savedAt = clock(),
        aliases = (listOf(address.roomId) + previous.flatMap { listOf(it.roomId) + it.aliases })
            .filter { it.isNotBlank() && it != detail.roomId }.distinct().take(16)
    )

    private suspend fun update(transform: (SavedLibrary) -> SavedLibrary): Boolean = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                withDesktopFileLock(file) {
                        val current = try { read() } catch (error: Exception) {
                            readFailed()
                            return@withDesktopFileLock false
                        }
                        val updated = transform(current)
                        validate(updated)
                        val bytes = json.encodeToString(updated).toByteArray(Charsets.UTF_8)
                        require(bytes.size <= MAX_FILE_BYTES) { "Library is too large" }
                        writeDesktopFileAtomically(file, bytes)
                        publish(updated)
                        true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(loaded = true, error = "本地资料保存失败，请检查磁盘空间或目录权限后重试。")
                false
            }
        }
    }

    private fun read(): SavedLibrary {
        if (!Files.exists(file)) return SavedLibrary()
        require(Files.size(file) <= MAX_FILE_BYTES) { "Library is too large" }
        return json.decodeFromString<SavedLibrary>(Files.readString(file)).also(::validate)
    }

    private fun validate(data: SavedLibrary) {
        require(data.version == 1) { "Unsupported library version" }
        require(data.history.size <= MAX_HISTORY)
        DesktopProfilePreferences(player = data.player, danmaku = data.danmaku,
            followOptions = data.followOptions, appearance = data.appearance).validate()
        for (rooms in listOf(data.follows, data.history)) {
            require(rooms.map { it.key }.distinct().size == rooms.size) { "Duplicate room" }
            rooms.forEach { room ->
                require(room.siteId in SITE_IDS && room.roomId.matches(ROOM_ID))
                require(room.aliases.size <= 16 && room.aliases.all { it.matches(ROOM_ID) })
                require(room.tags.all { it.isNotBlank() && it.length <= 80 } && room.note.length <= 1000 && room.liveStatus in 0..2 && room.lastCheckedAt >= 0)
            }
        }
    }

    private fun publish(data: SavedLibrary) {
        _state.value = DesktopLibraryState(loaded = true, follows = data.follows, history = data.history,
            player = data.player, followOptions = data.followOptions, danmaku = data.danmaku, appearance = data.appearance)
    }

    private fun readFailed() {
        _state.value = _state.value.copy(loaded = true, writable = false,
            error = "本地资料读取失败或版本不兼容，原文件已保留。修复文件后可重试读取。")
    }

    companion object {
        const val MAX_HISTORY = 200
        private const val MAX_FILE_BYTES = 8 * 1024 * 1024
        private val ROOM_ID = Regex("[a-zA-Z0-9_-]{1,80}")
        private val SITE_IDS = setOf("douyu", "bilibili", "huya", "douyin")
    }
}

fun defaultDesktopLibraryPath(): Path {
    System.getProperty("mylive.dataDir")?.takeIf { it.isNotBlank() }?.let { return Path.of(it, "library.json") }
    val local = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
    return if (local != null) Path.of(local, "MyLive", "library.json")
    else Path.of(System.getProperty("user.home"), ".mylive", "library.json")
}
