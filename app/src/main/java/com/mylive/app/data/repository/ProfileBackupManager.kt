package com.mylive.app.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.room.withTransaction
import com.mylive.app.core.common.MAX_IMPORTED_JSON_BYTES
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.local.entity.ShieldPresetEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileBackupManager @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val followRepository: FollowRepository,
    private val historyRepository: HistoryRepository,
    private val shieldRepository: ShieldRepository,
    private val database: AppDatabase
) {
    private val importMutex = Mutex()

    private val excludedKeys = setOf(
        SettingsDataStore.BilibiliCookie.name,
        SettingsDataStore.DouyinCookie.name,
        SettingsDataStore.EncryptedBilibiliCookie.name,
        SettingsDataStore.EncryptedDouyinCookie.name,
        SettingsDataStore.DouyuCookie.name,
        SettingsDataStore.EncryptedDouyuCookie.name,
        SettingsDataStore.WebDAVUri.name,
        SettingsDataStore.WebDAVUser.name,
        SettingsDataStore.kWebDAVPassword.name,
        SettingsDataStore.EncryptedWebDAVPassword.name,
        SettingsDataStore.kWebDAVLastUploadTime.name,
        SettingsDataStore.kWebDAVLastRecoverTime.name
    )

    /**
     * Keys that must additionally NOT be writable by an UNTRUSTED import (e.g. a JSON pushed by
     * a LAN peer). Letting a remote party set these would let them redirect where this device
     * syncs and which proxy it routes through — turning a one-shot push into a persistent
     * credential-exfiltration channel.
     */
    private val untrustedExcludedKeys = excludedKeys + setOf(
        SettingsDataStore.SyncServerUrl.name,
        SettingsDataStore.SyncProxyUrl.name
    )

    private val settingEntries = listOf(
        int(SettingsDataStore.ThemeMode, 0),
        bool(SettingsDataStore.DebugMode, false),
        bool(SettingsDataStore.LogEnable, false),
        int(SettingsDataStore.kStyleColor, 0xff3498db.toInt()),
        bool(SettingsDataStore.kIsDynamic, false),
        string(SettingsDataStore.SiteSort, "bilibili,douyu,huya,douyin"),
        string(SettingsDataStore.HomeSort, "recommend,follow,category,user"),
        string(SettingsDataStore.LiveRoomQuickAccessSort, "follow,history,recommendation"),
        bool(SettingsDataStore.LiveRoomQuickAccessEnabled, true),
        bool(SettingsDataStore.DanmuEnable, true),
        double(SettingsDataStore.DanmuSize, 16.0),
        double(SettingsDataStore.DanmuSpeed, 10.0),
        double(SettingsDataStore.DanmuArea, 0.8),
        int(SettingsDataStore.DanmuLineCount, 8),
        double(SettingsDataStore.DanmuDelay, 0.0),
        string(SettingsDataStore.DanmuDelayBySite, "{}"),
        double(SettingsDataStore.DanmuOpacity, 1.0),
        double(SettingsDataStore.DanmuStrokeWidth, 2.0),
        int(SettingsDataStore.DanmuFontWeight, 4),
        bool(SettingsDataStore.DanmuHideScroll, false),
        bool(SettingsDataStore.DanmuHideBottom, false),
        bool(SettingsDataStore.DanmuHideTop, false),
        double(SettingsDataStore.DanmuTopMargin, 0.0),
        double(SettingsDataStore.DanmuBottomMargin, 0.0),
        bool(SettingsDataStore.DanmuRenderEmoji, true),
        bool(SettingsDataStore.DanmuShieldEnable, true),
        bool(SettingsDataStore.DanmuKeywordShieldEnable, true),
        bool(SettingsDataStore.DanmuUserShieldEnable, true),
        bool(SettingsDataStore.DanmuDedupeEnable, false),
        int(SettingsDataStore.DanmuDedupeWindow, 10),
        int(SettingsDataStore.DanmuDedupeStep, 2),
        bool(SettingsDataStore.DanmuDedupeStrictMode, false),
        int(SettingsDataStore.ScaleMode, 0),
        bool(SettingsDataStore.HardwareDecode, true),
        int(SettingsDataStore.QualityLevel, 1),
        int(SettingsDataStore.QualityLevelCellular, 1),
        bool(SettingsDataStore.PlayerCompatMode, false),
        bool(SettingsDataStore.PlayerAutoPause, false),
        bool(SettingsDataStore.AllowBackgroundPlayback, false),
        bool(SettingsDataStore.PlayerForceHttps, false),
        bool(SettingsDataStore.AutoFullScreen, false),
        bool(SettingsDataStore.AutoPipOnExit, false),
        bool(SettingsDataStore.AutoExitEnable, false),
        int(SettingsDataStore.AutoExitDuration, 60),
        int(SettingsDataStore.RoomAutoExitDuration, 0),
        double(SettingsDataStore.ChatTextSize, 14.0),
        double(SettingsDataStore.ChatTextGap, 4.0),
        bool(SettingsDataStore.ChatBubbleStyle, false),
        bool(SettingsDataStore.PIPHideDanmu, false),
        bool(SettingsDataStore.SuperChatSortDesc, false),
        bool(SettingsDataStore.AutoUpdateFollowEnable, true),
        int(SettingsDataStore.AutoUpdateFollowDuration, 60),
        int(SettingsDataStore.UpdateFollowThreadCount, 8),
        string(SettingsDataStore.UserRemarks, ""),
        string(SettingsDataStore.SyncServerUrl, ""),
        string(SettingsDataStore.SyncProxyUrl, "")
    ).filterNot { it.name in excludedKeys }

    private val settingEntriesByName = settingEntries.associateBy { it.name }

    suspend fun exportProfileJson(): String = withContext(Dispatchers.IO) {
        importMutex.withLock { database.withTransaction { exportProfileJsonInternal() } }
    }

    private suspend fun exportProfileJsonInternal(): String {
        val preferences = settingsDataStore.preferencesFlow().first()
        val settings = JSONObject()
        settingEntries.forEach { entry ->
            settings.put(entry.name, entry.read(preferences))
        }

        val follows = followRepository.getAllFollows().first()
        val tags = followRepository.getAllTags().first()
        val histories = historyRepository.getAllHistory().first()
        val shields = shieldRepository.getAllShields().first()
        val presets = shieldRepository.getAllPresets().first()

        val root = JSONObject()
            .put("type", PROFILE_TYPE)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("exportTime", timestamp())
            .put("settings", settings)
            .put("danmuShield", JSONArray().apply { shields.forEach { put(it.toJson()) } })
            .put("shieldPresets", JSONArray().apply { presets.forEach { put(it.toJson()) } })
            .put("followUsers", JSONArray().apply { follows.forEach { put(it.toJson()) } })
            .put("followUserTags", JSONArray().apply { tags.forEach { put(it.toJson()) } })
            .put("histories", JSONArray().apply { histories.forEach { put(it.toJson()) } })
            .put(
                "summary",
                JSONObject()
                    .put("settings", settings.length())
                    .put("danmuShield", shields.size)
                    .put("shieldPresets", presets.size)
                    .put("followUsers", follows.size)
                    .put("followUserTags", tags.size)
                    .put("histories", histories.size)
            )

        val json = root.toString(2)
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_IMPORTED_JSON_BYTES) {
            "导出内容超过可恢复的 ${MAX_IMPORTED_JSON_BYTES / 1024 / 1024} MB 上限，请清理历史记录或减少备份数据后重试"
        }
        return json
    }

    /**
     * @param trusted whether the JSON comes from a user-initiated restore (local file / the
     * user's own WebDAV). Untrusted sources (a LAN peer push) cannot overwrite the sync endpoint
     * settings — see [untrustedExcludedKeys].
     */
    suspend fun importProfileJson(jsonString: String, trusted: Boolean = true) {
        try {
            val plan = withContext(Dispatchers.IO) { parseImportPlan(jsonString, trusted) }
            importMutex.withLock { applyImportPlan(plan) }
        } catch (e: JSONException) {
            throw IllegalArgumentException("invalid_json", e)
        }
    }

    /** Decode every section before touching either storage. Legacy field aliases remain supported. */
    private fun parseImportPlan(jsonString: String, trusted: Boolean): ImportPlan {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("invalid_json")

        val tokener = JSONTokener(trimmed)
        val payload = tokener.nextValue()
        require(tokener.nextClean() == '\u0000') { "invalid_json" }
        if (payload is JSONArray) {
            return ImportPlan(follows = parseFollowUsers(payload))
        }

        val root = payload as? JSONObject ?: throw IllegalArgumentException("invalid_json")
        if (root.has("type") && root.optString("type") != PROFILE_TYPE) {
            throw IllegalArgumentException("invalid_profile_type")
        }
        if (root.has("schemaVersion") || root.has("type")) {
            val version = root.opt("schemaVersion")
            require(version is Number && version.toDouble() == version.toInt().toDouble() &&
                version.toInt() in 1..SCHEMA_VERSION) { "unsupported_profile_version" }
        }
        val boxes = root.optionalObject("boxes")
        listOfNotNull(root, boxes).forEach { container ->
            listOf("settings", "config").forEach { container.optionalObject(it) }
            listOf("followUsers", "followUserTags", "histories", "shieldPresets", "danmuShieldPreset")
                .forEach { container.optionalArray(it) }
        }
        val plan = ImportPlan()
        var imported = false

        val settings = root.optionalObject("settings")
            ?: root.optionalObject("config")
            ?: boxes?.optionalObject("settings")
        if (settings != null) {
            parseSettings(settings, trusted, plan.settings)
            imported = true
        } else if (root.containsKnownSettings()) {
            parseSettings(root, trusted, plan.settings)
            imported = true
        }

        root.opt("danmuShield")?.let {
            plan.shields += parseShieldPayload(it)
            imported = true
        }
        boxes?.opt("danmuShield")?.let {
            plan.shields += parseShieldPayload(it)
            imported = true
        }
        root.opt("shield")?.let {
            plan.shields += parseShieldPayload(it)
            imported = true
        }
        root.optionalArray("shieldPresets")?.let {
            plan.presets += parseShieldPresets(it)
            imported = true
        }
        boxes?.optionalArray("danmuShieldPreset")?.let {
            plan.presets += parseShieldPresets(it)
            imported = true
        }
        root.optionalArray("followUsers")?.let {
            plan.follows += parseFollowUsers(it)
            imported = true
        }
        boxes?.optionalArray("followUsers")?.let {
            plan.follows += parseFollowUsers(it)
            imported = true
        }
        root.optionalArray("followUserTags")?.let {
            plan.tags += parseFollowUserTags(it)
            imported = true
        }
        boxes?.optionalArray("followUserTags")?.let {
            plan.tags += parseFollowUserTags(it)
            imported = true
        }
        root.optionalArray("histories")?.let {
            plan.histories += parseHistories(it)
            imported = true
        }
        boxes?.optionalArray("histories")?.let {
            plan.histories += parseHistories(it)
            imported = true
        }

        if (!imported) throw IllegalArgumentException("invalid_json")
        return plan
    }

    private suspend fun applyImportPlan(plan: ImportPlan) {
        val changedEntries = settingEntries.filter { plan.settings.contains(it.key) }
        var settingsBefore: Preferences? = null
        try {
            database.withTransaction {
                val follows = plan.follows
                val tags = plan.tags
                val histories = plan.histories
                val existingValues = shieldRepository.getAllShields().first().mapTo(mutableSetOf()) { it.value }
                val shields = plan.shields.filter { existingValues.add(it.value) }
                val presets = plan.presets
                followRepository.addFollows(follows)
                followRepository.addTags(tags)
                historyRepository.addHistories(histories)
                shieldRepository.addShields(shields)
                shieldRepository.addPresets(presets)
                if (changedEntries.isNotEmpty()) {
                    settingsDataStore.edit { preferences ->
                        settingsBefore = preferences.toPreferences()
                        changedEntries.forEach { it.copyValue(plan.settings, preferences) }
                    }
                }
            }
        } catch (error: Throwable) {
            // Room rolls back all tables. DataStore is separate, so compensate only touched keys,
            // and preserve any later user edits. This covers exceptions/cancellation, not power loss.
            settingsBefore?.let { before ->
                try {
                    withContext(NonCancellable) {
                        settingsDataStore.edit { preferences ->
                            changedEntries.forEach { it.restoreIfUnchanged(before, plan.settings, preferences) }
                        }
                    }
                } catch (rollbackError: Throwable) {
                    error.addSuppressed(rollbackError)
                }
            }
            throw error
        }
    }

    private fun parseSettings(settings: JSONObject, trusted: Boolean, preferences: MutablePreferences) {
        val excluded = if (trusted) excludedKeys else untrustedExcludedKeys
        settings.keys().forEach { key ->
            if (key in excluded) return@forEach
            val entry = settingEntriesByName[key] ?: return@forEach
            entry.write(preferences, unwrapLegacyValue(settings.opt(key)))
        }
    }

    private fun parseFollowUsers(array: JSONArray): List<FollowUserEntity> = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                item.validateStrings("id", "siteId", "site", "userName", "name", "face", "avatar", "tag")
                item.validateRoomId()
                if (item.has("isSpecialFollow")) require(item.opt("isSpecialFollow") is Boolean) { "invalid_follow" }
                if (item.has("liveStatus")) require(item.opt("liveStatus") is Number &&
                    item.getDouble("liveStatus") == item.getInt("liveStatus").toDouble()) { "invalid_follow_status" }
                val siteId = item.optString("siteId", item.optString("site", ""))
                val roomId = item.optString("roomId", item.optString("room", ""))
                val id = item.optString("id", "${siteId}_${roomId}")
                require(id.isNotBlank() && siteId.isNotBlank() && roomId.isNotBlank()) { "invalid_follow" }
                val liveStatus = item.optInt("liveStatus", 0)
                require(liveStatus in 0..2) { "invalid_follow_status" }

                add(
                    FollowUserEntity(
                        id = id,
                        roomId = roomId,
                        siteId = siteId,
                        userName = item.optString("userName", item.optString("name", "")),
                        face = item.optString("face", item.optString("avatar", "")),
                        addTime = item.optTimestampMillis("addTime", System.currentTimeMillis()),
                        tag = item.optString("tag", ""),
                        isSpecialFollow = item.optBoolean("isSpecialFollow", false),
                        liveStatus = liveStatus,
                        liveStartTime = item.optNullableLong("liveStartTime"),
                        showTime = item.optNullableString("showTime"),
                        canonicalRoomId = item.optNullableString("canonicalRoomId")?.takeIf { it.isNotBlank() }
                    )
                )
            }
    }

    private fun parseFollowUserTags(array: JSONArray): List<FollowUserTagEntity> = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                item.validateStrings("id", "tag", "name")
                val tag = item.optString("tag", item.optString("name", ""))
                require(tag.isNotBlank()) { "invalid_follow_tag" }
                val id = item.optString("id", UUID.randomUUID().toString()).ifBlank {
                    UUID.randomUUID().toString()
                }
                add(
                    FollowUserTagEntity(
                        id = id,
                        tag = tag,
                        userIds = (item.optionalArray("userIds") ?: item.optionalArray("userId")).toStringList()
                    )
                )
            }
    }

    private fun parseHistories(array: JSONArray): List<HistoryEntity> = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                item.validateStrings("id", "siteId", "site", "userName", "name", "face", "avatar")
                item.validateRoomId()
                val siteId = item.optString("siteId", item.optString("site", ""))
                val roomId = item.optString("roomId", item.optString("room", ""))
                val id = item.optString("id", "${siteId}_${roomId}")
                require(id.isNotBlank() && siteId.isNotBlank() && roomId.isNotBlank()) { "invalid_history" }

                add(
                    HistoryEntity(
                        id = id,
                        roomId = roomId,
                        siteId = siteId,
                        userName = item.optString("userName", item.optString("name", "")),
                        face = item.optString("face", item.optString("avatar", "")),
                        updateTime = item.optTimestampMillis("updateTime", System.currentTimeMillis())
                    )
                )
            }
    }

    private fun parseShieldPayload(payload: Any?): List<ShieldEntity> = when (payload) {
            is JSONArray -> parseShields(payload)
            is JSONObject -> {
                require(listOf("raw", "keywords", "users", "userGroups").any { payload.has(it) }) { "invalid_shield" }
                val raw = payload.optionalArray("raw")
                if (raw != null) parseShields(raw) else buildList {
                addAll(parseShieldValues(payload.optionalArray("keywords"), "keyword:"))
                addAll(parseShieldValues(payload.optionalArray("users"), "user:__all__:"))
                payload.optionalObject("userGroups")?.let { groups ->
                    val keys = groups.keys()
                    while (keys.hasNext()) {
                        val siteId = keys.next()
                        addAll(parseShieldValues(groups.getJSONArray(siteId), "user:$siteId:"))
                    }
                }
                }
            }
            else -> throw IllegalArgumentException("invalid_shield")
    }

    private fun parseShields(array: JSONArray): List<ShieldEntity> = buildList {
            for (index in 0 until array.length()) {
                val value = when (val item = array.opt(index)) {
                    is JSONObject -> {
                        item.validateStrings("value", "keyword", "text")
                        item.optString("value", item.optString("keyword", item.optString("text", "")))
                    }
                    is String -> item
                    else -> throw IllegalArgumentException("invalid_shield")
                }
                require(value.isNotBlank()) { "invalid_shield" }
                add(ShieldEntity(value = value))
            }
    }

    private fun parseShieldValues(array: JSONArray?, prefix: String): List<ShieldEntity> = buildList {
        if (array == null) return@buildList
            for (index in 0 until array.length()) {
                val item = array.get(index)
                require(item is String) { "invalid_shield" }
                val raw = item.trim()
                require(raw.isNotBlank()) { "invalid_shield" }
                val value = if (raw.startsWith("keyword:") || raw.startsWith("user:")) raw else "$prefix$raw"
                add(ShieldEntity(value = value))
            }
    }

    private fun parseShieldPresets(array: JSONArray): List<ShieldPresetEntity> = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                item.validateStrings("name", "value")
                val name = item.optString("name", "")
                require(name.isNotBlank()) { "invalid_shield_preset" }
                add(
                    ShieldPresetEntity(
                        name = name,
                        value = item.optString("value", "")
                    )
                )
            }
    }

    private fun JSONObject.optionalObject(key: String): JSONObject? =
        if (has(key)) getJSONObject(key) else null

    private fun JSONObject.optionalArray(key: String): JSONArray? =
        if (has(key)) getJSONArray(key) else null

    private fun JSONObject.validateStrings(vararg keys: String) {
        keys.forEach { key ->
            if (has(key)) require(opt(key) is String) { "invalid_field_$key" }
        }
    }

    private fun JSONObject.validateRoomId() {
        listOf("roomId", "room").forEach { key ->
            if (has(key)) require(opt(key) is String || opt(key) is Number) { "invalid_field_$key" }
        }
        if (has("showTime") && !isNull("showTime")) require(opt("showTime") is String ||
            opt("showTime") is Number) { "invalid_field_showTime" }
        if (has("canonicalRoomId") && !isNull("canonicalRoomId"))
            require(opt("canonicalRoomId") is String) { "invalid_field_canonicalRoomId" }
    }

    private data class ImportPlan(
        val settings: MutablePreferences = mutablePreferencesOf(),
        var follows: List<FollowUserEntity> = emptyList(),
        var tags: List<FollowUserTagEntity> = emptyList(),
        var histories: List<HistoryEntity> = emptyList(),
        var shields: List<ShieldEntity> = emptyList(),
        var presets: List<ShieldPresetEntity> = emptyList()
    )

    private fun JSONObject.containsKnownSettings(): Boolean {
        val iterator = keys()
        while (iterator.hasNext()) {
            if (iterator.next() in settingEntriesByName) return true
        }
        return false
    }

    private fun unwrapLegacyValue(value: Any?): Any? {
        if (value is JSONObject && value.has("value")) {
            return value.opt("value")
        }
        return value
    }

    private fun FollowUserEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("roomId", roomId)
        .put("siteId", siteId)
        .put("userName", userName)
        .put("face", face)
        .put("addTime", addTime)
        .put("tag", tag)
        .put("isSpecialFollow", isSpecialFollow)
        .put("liveStatus", liveStatus)
        .put("liveStartTime", liveStartTime ?: JSONObject.NULL)
        .put("showTime", showTime ?: JSONObject.NULL)
        .put("canonicalRoomId", canonicalRoomId ?: JSONObject.NULL)

    private fun FollowUserTagEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("tag", tag)
        .put("userIds", JSONArray().apply { userIds.forEach { put(it) } })

    private fun HistoryEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("roomId", roomId)
        .put("siteId", siteId)
        .put("userName", userName)
        .put("face", face)
        .put("updateTime", updateTime)

    private fun ShieldEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("value", value)

    private fun ShieldPresetEntity.toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("value", value)

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = this@toStringList.get(index)
                require(value is String && value.isNotBlank()) { "invalid_tag_user" }
                add(value)
            }
        }
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return parseTimestampMillis(opt(key))
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optTimestampMillis(key: String, defaultValue: Long): Long {
        if (!has(key) || isNull(key)) return defaultValue
        return parseTimestampMillis(opt(key))
    }

    private fun parseTimestampMillis(value: Any?): Long = when (value) {
        is Number -> value.toLong().also {
            require(value.toDouble().isFinite() && value.toDouble() == it.toDouble()) { "invalid_timestamp" }
        }
        is String -> value.toLongOrNull() ?: runCatching { Instant.parse(value).toEpochMilli() }
            .getOrElse { throw IllegalArgumentException("invalid_timestamp", it) }
        else -> throw IllegalArgumentException("invalid_timestamp")
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private sealed class SettingEntry<T : Any>(
        val key: Preferences.Key<T>,
        val defaultValue: T
    ) {
        val name: String get() = key.name
        fun read(preferences: Preferences): Any = preferences[key] ?: defaultValue
        fun copyValue(source: Preferences, destination: MutablePreferences) {
            val value = source[key]
            if (value == null) destination.remove(key) else destination[key] = value
        }
        fun restoreIfUnchanged(before: Preferences, imported: Preferences, current: MutablePreferences) {
            if (current[key] == imported[key]) copyValue(before, current)
        }
        abstract fun write(preferences: MutablePreferences, value: Any?)
    }

    private class BooleanSettingEntry(
        key: Preferences.Key<Boolean>,
        defaultValue: Boolean
    ) : SettingEntry<Boolean>(key, defaultValue) {
        override fun write(preferences: MutablePreferences, value: Any?) {
            val parsed = when (value) {
                is Boolean -> value
                is Number -> when (value.toDouble()) { 0.0 -> false; 1.0 -> true; else -> null }
                is String -> value.toBooleanStrictOrNull()
                else -> null
            } ?: throw IllegalArgumentException("invalid_setting_${key.name}")
            preferences[key] = parsed
        }
    }

    private class IntSettingEntry(
        key: Preferences.Key<Int>,
        defaultValue: Int
    ) : SettingEntry<Int>(key, defaultValue) {
        override fun write(preferences: MutablePreferences, value: Any?) {
            val parsed = when (value) {
                is Number -> value.toDouble().takeIf { it.isFinite() && it >= Int.MIN_VALUE &&
                    it <= Int.MAX_VALUE && it == value.toInt().toDouble() }?.toInt()
                is String -> value.toIntOrNull()
                else -> null
            } ?: throw IllegalArgumentException("invalid_setting_${key.name}")
            preferences[key] = parsed
        }
    }

    private class DoubleSettingEntry(
        key: Preferences.Key<Double>,
        defaultValue: Double
    ) : SettingEntry<Double>(key, defaultValue) {
        override fun write(preferences: MutablePreferences, value: Any?) {
            val parsed = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }?.takeIf { it.isFinite() } ?: throw IllegalArgumentException("invalid_setting_${key.name}")
            preferences[key] = parsed
        }
    }

    private class StringSettingEntry(
        key: Preferences.Key<String>,
        defaultValue: String
    ) : SettingEntry<String>(key, defaultValue) {
        override fun write(preferences: MutablePreferences, value: Any?) {
            require(value is String) { "invalid_setting_${key.name}" }
            preferences[key] = value.toString()
        }
    }

    private fun bool(key: Preferences.Key<Boolean>, defaultValue: Boolean) =
        BooleanSettingEntry(key, defaultValue)

    private fun int(key: Preferences.Key<Int>, defaultValue: Int) =
        IntSettingEntry(key, defaultValue)

    private fun double(key: Preferences.Key<Double>, defaultValue: Double) =
        DoubleSettingEntry(key, defaultValue)

    private fun string(key: Preferences.Key<String>, defaultValue: String) =
        StringSettingEntry(key, defaultValue)

    companion object {
        const val PROFILE_TYPE = "my_live_profile"
        const val SCHEMA_VERSION = 3
    }
}
