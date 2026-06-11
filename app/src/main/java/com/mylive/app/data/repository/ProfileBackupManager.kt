package com.mylive.app.data.repository

import androidx.datastore.preferences.core.Preferences
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.data.local.entity.ShieldPresetEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
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
    private val shieldRepository: ShieldRepository
) {

    private val excludedKeys = setOf(
        SettingsDataStore.BilibiliCookie.name,
        SettingsDataStore.DouyinCookie.name,
        SettingsDataStore.BilibiliLoginTip.name,
        SettingsDataStore.WebDAVUri.name,
        SettingsDataStore.WebDAVUser.name,
        SettingsDataStore.kWebDAVPassword.name,
        SettingsDataStore.kWebDAVLastUploadTime.name,
        SettingsDataStore.kWebDAVLastRecoverTime.name,
        SettingsDataStore.LastLiveRoom.name,
        SettingsDataStore.LastLiveRoomResumePending.name
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
        bool(SettingsDataStore.FirstRun, false),
        int(SettingsDataStore.ThemeMode, 0),
        bool(SettingsDataStore.DebugMode, false),
        bool(SettingsDataStore.LogEnable, false),
        int(SettingsDataStore.kStyleColor, 0xff3498db.toInt()),
        bool(SettingsDataStore.kIsDynamic, false),
        string(SettingsDataStore.SiteSort, "bilibili,douyu,huya,douyin"),
        string(SettingsDataStore.HomeSort, "recommend,follow,category,user"),
        string(SettingsDataStore.LiveRoomTabSort, "chat,super_chat,follow,settings"),
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
        int(SettingsDataStore.PlayerBufferSize, 0),
        bool(SettingsDataStore.PlayerForceHttps, false),
        double(SettingsDataStore.PlayerVolume, 1.0),
        bool(SettingsDataStore.AutoFullScreen, false),
        bool(SettingsDataStore.AutoPipOnExit, false),
        bool(SettingsDataStore.CustomPlayerOutput, false),
        string(SettingsDataStore.VideoOutputDriver, ""),
        string(SettingsDataStore.VideoHardwareDecoder, ""),
        string(SettingsDataStore.AudioOutputDriver, ""),
        bool(SettingsDataStore.AutoExitEnable, false),
        int(SettingsDataStore.AutoExitDuration, 60),
        int(SettingsDataStore.RoomAutoExitDuration, 60),
        double(SettingsDataStore.ChatTextSize, 14.0),
        double(SettingsDataStore.ChatTextGap, 4.0),
        bool(SettingsDataStore.ChatBubbleStyle, false),
        bool(SettingsDataStore.ContributionRankEnable, true),
        bool(SettingsDataStore.PIPHideDanmu, false),
        bool(SettingsDataStore.PIPHideDanmuDefaultMigrated, false),
        bool(SettingsDataStore.SuperChatSortDesc, false),
        bool(SettingsDataStore.AutoUpdateFollowEnable, true),
        int(SettingsDataStore.AutoUpdateFollowDuration, 60),
        int(SettingsDataStore.UpdateFollowThreadCount, 8),
        string(SettingsDataStore.UserRemarks, ""),
        string(SettingsDataStore.SyncServerUrl, ""),
        string(SettingsDataStore.SyncProxyUrl, ""),
        bool(SettingsDataStore.LiveSubtitleEnable, false),
        string(SettingsDataStore.LiveSubtitleModelPath, ""),
        string(SettingsDataStore.LiveSubtitleLanguage, ""),
        double(SettingsDataStore.LiveSubtitleFontSize, 16.0),
        int(SettingsDataStore.LiveSubtitlePosition, 0),
        double(SettingsDataStore.LiveSubtitleOffsetX, 0.0),
        double(SettingsDataStore.LiveSubtitleOffsetY, 0.0),
        int(SettingsDataStore.LiveSubtitleColor, -1),
        int(SettingsDataStore.LiveSubtitleFontWeight, 4),
        bool(SettingsDataStore.LiveSubtitleBackgroundEnable, false),
        bool(SettingsDataStore.LiveSubtitlePositionLocked, false),
        bool(SettingsDataStore.LiveSubtitleStartupGuard, false)
    ).filterNot { it.name in excludedKeys }

    private val settingEntriesByName = settingEntries.associateBy { it.name }

    suspend fun exportProfileJson(): String {
        val settings = JSONObject()
        settingEntries.forEach { entry ->
            settings.put(entry.name, entry.read(settingsDataStore))
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

        return root.toString(2)
    }

    /**
     * @param trusted whether the JSON comes from a user-initiated restore (local file / the
     * user's own WebDAV). Untrusted sources (a LAN peer push) cannot overwrite the sync endpoint
     * settings — see [untrustedExcludedKeys].
     */
    suspend fun importProfileJson(jsonString: String, trusted: Boolean = true) {
        try {
            importProfileJsonInternal(jsonString, trusted)
        } catch (e: JSONException) {
            throw IllegalArgumentException("invalid_json", e)
        }
    }

    private suspend fun importProfileJsonInternal(jsonString: String, trusted: Boolean) {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("invalid_json")

        if (trimmed.startsWith("[")) {
            importFollowUsers(JSONArray(trimmed))
            return
        }

        val root = JSONObject(trimmed)
        val boxes = root.optJSONObject("boxes")
        var imported = false

        val settings = root.optJSONObject("settings")
            ?: root.optJSONObject("config")
            ?: boxes?.optJSONObject("settings")
        if (settings != null) {
            importSettings(settings, trusted)
            imported = true
        } else if (root.containsKnownSettings()) {
            importSettings(root, trusted)
            imported = true
        }

        root.opt("danmuShield")?.let {
            importShieldPayload(it)
            imported = true
        }
        boxes?.opt("danmuShield")?.let {
            importShieldPayload(it)
            imported = true
        }
        root.opt("shield")?.let {
            importShieldPayload(it)
            imported = true
        }
        root.optJSONArray("shieldPresets")?.let {
            importShieldPresets(it)
            imported = true
        }
        boxes?.optJSONArray("danmuShieldPreset")?.let {
            importShieldPresets(it)
            imported = true
        }
        root.optJSONArray("followUsers")?.let {
            importFollowUsers(it)
            imported = true
        }
        boxes?.optJSONArray("followUsers")?.let {
            importFollowUsers(it)
            imported = true
        }
        root.optJSONArray("followUserTags")?.let {
            importFollowUserTags(it)
            imported = true
        }
        boxes?.optJSONArray("followUserTags")?.let {
            importFollowUserTags(it)
            imported = true
        }
        root.optJSONArray("histories")?.let {
            importHistories(it)
            imported = true
        }
        boxes?.optJSONArray("histories")?.let {
            importHistories(it)
            imported = true
        }

        if (!imported) throw IllegalArgumentException("invalid_json")
    }

    private suspend fun importSettings(settings: JSONObject, trusted: Boolean) {
        val excluded = if (trusted) excludedKeys else untrustedExcludedKeys
        settings.keys().forEach { key ->
            if (key in excluded) return@forEach
            val entry = settingEntriesByName[key] ?: return@forEach
            val value = settings.opt(key)
            entry.write(settingsDataStore, unwrapLegacyValue(value))
        }
    }

    private suspend fun importFollowUsers(array: JSONArray) {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val siteId = item.optString("siteId", item.optString("site", ""))
            val roomId = item.optString("roomId", item.optString("room", ""))
            val id = item.optString("id", "${siteId}_${roomId}")
            if (id.isBlank() || siteId.isBlank() || roomId.isBlank()) continue

            followRepository.addFollow(
                FollowUserEntity(
                    id = id,
                    roomId = roomId,
                    siteId = siteId,
                    userName = item.optString("userName", item.optString("name", "")),
                    face = item.optString("face", item.optString("avatar", "")),
                    addTime = item.optTimestampMillis("addTime", System.currentTimeMillis()),
                    tag = item.optString("tag", ""),
                    isSpecialFollow = item.optBoolean("isSpecialFollow", false),
                    liveStatus = item.optInt("liveStatus", 0),
                    liveStartTime = item.optNullableLong("liveStartTime")
                )
            )
        }
    }

    private suspend fun importFollowUserTags(array: JSONArray) {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val tag = item.optString("tag", item.optString("name", ""))
            if (tag.isBlank()) continue
            val id = item.optString("id", UUID.randomUUID().toString()).ifBlank {
                UUID.randomUUID().toString()
            }
            followRepository.addTag(
                FollowUserTagEntity(
                    id = id,
                    tag = tag,
                    userIds = (item.optJSONArray("userIds") ?: item.optJSONArray("userId")).toStringList()
                )
            )
        }
    }

    private suspend fun importHistories(array: JSONArray) {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val siteId = item.optString("siteId", item.optString("site", ""))
            val roomId = item.optString("roomId", item.optString("room", ""))
            val id = item.optString("id", "${siteId}_${roomId}")
            if (id.isBlank() || siteId.isBlank() || roomId.isBlank()) continue

            historyRepository.addHistory(
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

    private suspend fun importShieldPayload(payload: Any?) {
        when (payload) {
            is JSONArray -> importShields(payload)
            is JSONObject -> {
                val raw = payload.optJSONArray("raw")
                if (raw != null) {
                    importShields(raw)
                    return
                }
                importShieldValues(payload.optJSONArray("keywords"), "keyword:")
                importShieldValues(payload.optJSONArray("users"), "user:__all__:")
                payload.optJSONObject("userGroups")?.let { groups ->
                    val keys = groups.keys()
                    while (keys.hasNext()) {
                        val siteId = keys.next()
                        importShieldValues(groups.optJSONArray(siteId), "user:$siteId:")
                    }
                }
            }
        }
    }

    private suspend fun importShields(array: JSONArray) {
        val existingValues = shieldRepository.getAllShields().first().mapTo(mutableSetOf()) { it.value }
        for (index in 0 until array.length()) {
            val value = when (val item = array.opt(index)) {
                is JSONObject -> item.optString(
                    "value",
                    item.optString("keyword", item.optString("text", ""))
                )
                is String -> item
                else -> item?.toString() ?: ""
            }
            if (value.isBlank() || !existingValues.add(value)) continue
            shieldRepository.addShield(ShieldEntity(value = value))
        }
    }

    private suspend fun importShieldValues(array: JSONArray?, prefix: String) {
        if (array == null) return
        val existingValues = shieldRepository.getAllShields().first().mapTo(mutableSetOf()) { it.value }
        for (index in 0 until array.length()) {
            val raw = array.optString(index, "").trim()
            if (raw.isBlank()) continue
            val value = if (raw.startsWith("keyword:") || raw.startsWith("user:")) raw else "$prefix$raw"
            if (existingValues.add(value)) {
                shieldRepository.addShield(ShieldEntity(value = value))
            }
        }
    }

    private suspend fun importShieldPresets(array: JSONArray) {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name", "")
            if (name.isBlank()) continue
            shieldRepository.addPreset(
                ShieldPresetEntity(
                    name = name,
                    value = item.optString("value", "")
                )
            )
        }
    }

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
                val value = optString(index, "")
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key)
    }

    private fun JSONObject.optTimestampMillis(key: String, defaultValue: Long): Long {
        if (!has(key) || isNull(key)) return defaultValue
        return when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
                ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(defaultValue)
            else -> defaultValue
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private sealed class SettingEntry<T>(
        val key: Preferences.Key<T>,
        val defaultValue: T
    ) {
        val name: String get() = key.name
        abstract suspend fun read(settingsDataStore: SettingsDataStore): Any
        abstract suspend fun write(settingsDataStore: SettingsDataStore, value: Any?)
    }

    private class BooleanSettingEntry(
        key: Preferences.Key<Boolean>,
        defaultValue: Boolean
    ) : SettingEntry<Boolean>(key, defaultValue) {
        override suspend fun read(settingsDataStore: SettingsDataStore): Any =
            settingsDataStore.getFlow(key, defaultValue).first()

        override suspend fun write(settingsDataStore: SettingsDataStore, value: Any?) {
            val parsed = when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.toBooleanStrictOrNull()
                else -> null
            } ?: return
            settingsDataStore.setValue(key, parsed)
        }
    }

    private class IntSettingEntry(
        key: Preferences.Key<Int>,
        defaultValue: Int
    ) : SettingEntry<Int>(key, defaultValue) {
        override suspend fun read(settingsDataStore: SettingsDataStore): Any =
            settingsDataStore.getFlow(key, defaultValue).first()

        override suspend fun write(settingsDataStore: SettingsDataStore, value: Any?) {
            val parsed = when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            } ?: return
            settingsDataStore.setValue(key, parsed)
        }
    }

    private class DoubleSettingEntry(
        key: Preferences.Key<Double>,
        defaultValue: Double
    ) : SettingEntry<Double>(key, defaultValue) {
        override suspend fun read(settingsDataStore: SettingsDataStore): Any =
            settingsDataStore.getFlow(key, defaultValue).first()

        override suspend fun write(settingsDataStore: SettingsDataStore, value: Any?) {
            val parsed = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            } ?: return
            settingsDataStore.setValue(key, parsed)
        }
    }

    private class StringSettingEntry(
        key: Preferences.Key<String>,
        defaultValue: String
    ) : SettingEntry<String>(key, defaultValue) {
        override suspend fun read(settingsDataStore: SettingsDataStore): Any =
            settingsDataStore.getFlow(key, defaultValue).first()

        override suspend fun write(settingsDataStore: SettingsDataStore, value: Any?) {
            if (value == null || value == JSONObject.NULL) return
            settingsDataStore.setValue(key, value.toString())
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
        const val SCHEMA_VERSION = 2
    }
}
