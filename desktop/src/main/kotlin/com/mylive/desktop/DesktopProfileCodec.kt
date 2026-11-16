package com.mylive.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Serializable
data class DesktopProfilePreferences(
    val version: Int = 1,
    val player: DesktopPlayerPreferences = DesktopPlayerPreferences(),
    val danmaku: DesktopDanmakuPreferences = DesktopDanmakuPreferences(),
    val followOptions: DesktopFollowPreferences = DesktopFollowPreferences(),
    val appearance: DesktopAppearancePreferences = DesktopAppearancePreferences()
) {
    fun validate() {
        require(version == 1) { "Windows 备份版本不支持" }
        require(player.volume.isFinite() && player.volume in 0f..100f)
        require(player.qualityLevel in 0..2 && player.autoStopMinutes in 1..1440)
        require(player.chatTextSize.isFinite() && player.chatTextSize in 10f..28f)
        require(player.qualityBySite.all { it.key in appearance.platformOrder && it.value.length <= 120 })
        require(followOptions.intervalSeconds in 30..3600)
        danmaku.validate(); appearance.validate()
    }
}

/** Only explicit supported settings enter the plan; credentials and endpoint configuration never do. */
internal object DesktopProfileCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun decode(root: JSONObject, follows: List<SavedRoom>, history: List<SavedRoom>): RoomProfileImport {
        val boxes = root.optJSONObject("boxes")
        val settings = root.optJSONObject("settings") ?: root.optJSONObject("config") ?: boxes?.optJSONObject("settings")
        val tags = root.optJSONArray("followUserTags") ?: boxes?.optJSONArray("followUserTags")
        val memberships = mutableMapOf<String, MutableList<String>>()
        val sourceIds = mutableMapOf<String, MutableList<String>>()
        for (container in listOfNotNull(root, boxes)) for (key in listOf("followUsers", "follows")) {
            val rows = container.optJSONArray(key) ?: continue
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val site = row.optString("siteId", row.optString("site")); val room = row.optString("roomId", row.optString("room"))
                if (row.has("id")) sourceIds.getOrPut("${site}_$room") { mutableListOf() }.add(row.getString("id"))
            }
        }
        if (tags != null) for (i in 0 until tags.length()) {
            val row = tags.getJSONObject(i)
            val name = row.getString(if (row.has("tag")) "tag" else "name")
            require(name.isNotBlank() && name.length <= 80) { "关注分组格式不正确" }
            val ids = row.optJSONArray("userIds") ?: row.optJSONArray("userId") ?: JSONArray()
            for (j in 0 until ids.length()) memberships.getOrPut(ids.getString(j)) { mutableListOf() }.add(name)
        }
        val remarks = settings?.optString("UserRemarks")?.takeIf { it.isNotBlank() }?.let(::JSONObject)
        val mapped = follows.map { room ->
            val names = (listOf(room.roomId) + room.aliases).flatMap { id ->
                (listOf("${room.siteId}_$id") + sourceIds["${room.siteId}_$id"].orEmpty()).flatMap { memberships[it].orEmpty() }
            }
            room.copy(tags = (room.tags + names).distinct(),
                note = room.note.ifBlank { remarks?.optString("${room.siteId}::${room.userName}").orEmpty() })
        }
        val hasAndroidSettings = settings != null && settings.keys().asSequence().any { it in supported }
        var prefs = if (root.has("desktop")) json.decodeFromString<DesktopProfilePreferences>(root.getJSONObject("desktop").toString())
            else if (hasAndroidSettings) fromAndroid(settings!!) else null
        val shield = root.opt("danmuShield") ?: root.opt("blocked_word") ?: boxes?.opt("danmuShield")
        if (shield != null && shield != JSONObject.NULL) {
            val values: List<String> = when (shield) {
                is JSONArray -> List(shield.length()) { i ->
                    val value = shield.get(i)
                    if (value is JSONObject) value.getString("value") else value as? String ?: error("屏蔽内容格式不正确")
                }
                is JSONObject -> buildList {
                    val raw = shield.optJSONArray("raw")
                    if (raw != null) for (i in 0 until raw.length()) add(raw.getString(i))
                    else {
                        shield.optJSONArray("keywords")?.let { for (i in 0 until it.length()) add("keyword:${it.getString(i)}") }
                        shield.optJSONArray("users")?.let { for (i in 0 until it.length()) add("user:__all__:${it.getString(i)}") }
                        shield.optJSONObject("usersBySite")?.let { groups -> groups.keys().forEach { site ->
                            groups.getJSONArray(site).let { for (i in 0 until it.length()) add("user:$site:${it.getString(i)}") }
                        } }
                    }
                }
                else -> error("屏蔽内容格式不正确")
            }
            val base = prefs ?: DesktopProfilePreferences()
            // Windows section keeps all exact preferences; Android fields are the compatibility projection.
            if (!root.has("desktop")) prefs = base.copy(danmaku = base.danmaku.copy(
                keywords = values.filter { it.startsWith("keyword:") }.map { it.removePrefix("keyword:") }.distinct(),
                users = values.filter { it.startsWith("user:") }.map { value ->
                    val parts = value.split(':', limit = 3); require(parts.size == 3); DesktopBlockedUser(parts[1], parts[2])
                }.distinct()))
        }
        prefs?.validate()
        require(mapped.all { room -> room.note.length <= 1000 && room.tags.all { it.length <= 80 } })
        val sections = if (root.has("desktop")) setOf("player", "danmaku", "appearance", "followOptions") else buildSet {
            val keys = settings?.keys()?.asSequence()?.filter { it in supported }?.toSet().orEmpty()
            if (keys.any { it.startsWith("Danmu") && it != "DanmuEnable" } || shield != null) add("danmaku")
            if (keys.any { it in setOf("ThemeMode", "SiteSort", "kStyleColor") }) add("appearance")
            if (keys.any { it.startsWith("AutoUpdateFollow") }) add("followOptions")
            if (keys.any { it !in setOf("ThemeMode", "SiteSort", "kStyleColor") && !it.startsWith("AutoUpdateFollow") && (!it.startsWith("Danmu") || it == "DanmuEnable") }) add("player")
        }
        val shieldsOnly = !root.has("desktop") && shield != null && settings?.keys()?.asSequence()?.none { it.startsWith("Danmu") && it != "DanmuEnable" } != false
        return RoomProfileImport(mapped, history, prefs, sections, shieldsOnly)
    }

    fun encode(root: JSONObject, state: DesktopLibraryState) {
        val p = state.player; val d = state.danmaku; val a = state.appearance
        val prefs = DesktopProfilePreferences(player = p, danmaku = d, appearance = a, followOptions = state.followOptions)
        prefs.validate()
        root.put("desktop", JSONObject(json.encodeToString(prefs)))
        val remarks = JSONObject().apply { state.follows.filter { it.note.isNotBlank() }.forEach { put("${it.siteId}::${it.userName}", it.note) } }
        root.put("settings", JSONObject().put("ThemeMode", a.theme).put("SiteSort", a.platformOrder.joinToString(","))
            .put("DanmuEnable", p.danmakuEnabled).put("HardwareDecode", p.hardwareDecode).put("ScaleMode", when (p.scale) {
                DesktopVideoScale.Fit -> 0
                DesktopVideoScale.Stretch -> 1
                DesktopVideoScale.Crop -> 2
            })
            .put("QualityLevel", p.qualityLevel + 1).put("ChatTextSize", p.chatTextSize).put("AllowBackgroundPlayback", p.backgroundPlayback)
            .put("AutoExitEnable", p.autoStopEnabled).put("AutoExitDuration", p.autoStopMinutes)
            .put("DanmuSize", d.fontSize).put("DanmuSpeed", 80f / d.travelSeconds).put("DanmuOpacity", d.opacity)
            .put("DanmuArea", d.area).put("DanmuLineCount", d.lines).put("DanmuHideScroll", !d.scroll).put("DanmuHideTop", !d.top).put("DanmuHideBottom", !d.bottom)
            .put("DanmuShieldEnable", d.shieldEnabled).put("DanmuRenderEmoji", d.renderEmoji).put("DanmuDedupeEnable", d.deduplicate).put("DanmuDedupeWindow", d.dedupeSeconds)
            .put("AutoUpdateFollowEnable", state.followOptions.autoRefresh).put("AutoUpdateFollowDuration", state.followOptions.intervalSeconds)
            .put("UserRemarks", remarks.toString()).apply { if (a.accent != 0L) put("kStyleColor", a.accent.toInt()) })
        root.put("followUserTags", JSONArray().apply {
            state.follows.flatMap { it.tags }.distinct().forEach { tag ->
                put(JSONObject().put("id", UUID.nameUUIDFromBytes(tag.toByteArray()).toString()).put("tag", tag)
                    .put("userIds", JSONArray(state.follows.filter { tag in it.tags }.map { "${it.siteId}_${it.roomId}" })))
            }
        })
        root.put("danmuShield", JSONArray().apply {
            val values = d.keywords.map { "keyword:$it" } + d.users.map { "user:${it.siteId}:${it.userName}" }
            values.forEach { value -> put(JSONObject().put("id", UUID.nameUUIDFromBytes(value.toByteArray()).toString()).put("value", value)) }
        })
    }

    private val supported = setOf("ThemeMode", "SiteSort", "kStyleColor", "DanmuEnable", "HardwareDecode", "ScaleMode", "QualityLevel",
        "ChatTextSize", "AllowBackgroundPlayback", "AutoExitEnable", "AutoExitDuration", "DanmuSize", "DanmuSpeed", "DanmuArea", "DanmuOpacity",
        "DanmuLineCount", "DanmuHideScroll", "DanmuHideTop", "DanmuHideBottom", "DanmuShieldEnable", "DanmuRenderEmoji", "DanmuDedupeEnable", "DanmuDedupeWindow",
        "AutoUpdateFollowEnable", "AutoUpdateFollowDuration")
    private fun fromAndroid(s: JSONObject): DesktopProfilePreferences {
        fun number(key: String, default: Float): Float = if (s.has(key)) (s.get(key) as? Number)?.toFloat()?.also { require(it.isFinite()) } ?: error("$key 格式不正确") else default
        fun int(key: String, default: Int): Int = number(key, default.toFloat()).let { require(it % 1f == 0f); it.toInt() }
        fun bool(key: String, default: Boolean) = if (s.has(key)) s.get(key) as? Boolean ?: error("$key 格式不正确") else default
        val defaults = DesktopAppearancePreferences().platformOrder
        val order = if (s.has("SiteSort")) (s.getString("SiteSort").split(',').filter { it in defaults } + defaults).distinct() else defaults
        return DesktopProfilePreferences(
            player = DesktopPlayerPreferences(danmakuEnabled = bool("DanmuEnable", true), hardwareDecode = bool("HardwareDecode", true),
                scale = when (int("ScaleMode", 0)) {
                    1 -> DesktopVideoScale.Stretch
                    2 -> DesktopVideoScale.Crop
                    else -> DesktopVideoScale.Fit
                }, qualityLevel = (int("QualityLevel", 1) - 1).coerceIn(0, 2),
                chatTextSize = number("ChatTextSize", 13f).coerceIn(10f, 28f), backgroundPlayback = bool("AllowBackgroundPlayback", false),
                autoStopEnabled = bool("AutoExitEnable", false), autoStopMinutes = int("AutoExitDuration", 60).coerceIn(1, 1440)),
            danmaku = DesktopDanmakuPreferences(fontSize = number("DanmuSize", 18f).coerceIn(12f, 48f),
                travelSeconds = (80f / number("DanmuSpeed", 10f).coerceAtLeast(.1f)).coerceIn(3f, 30f),
                opacity = number("DanmuOpacity", 1f).coerceIn(.1f, 1f), area = number("DanmuArea", .8f).coerceIn(.2f, 1f),
                lines = int("DanmuLineCount", 8).coerceIn(1, 20), scroll = !bool("DanmuHideScroll", false), top = !bool("DanmuHideTop", false),
                bottom = !bool("DanmuHideBottom", false), shieldEnabled = bool("DanmuShieldEnable", true),
                deduplicate = bool("DanmuDedupeEnable", false), dedupeSeconds = int("DanmuDedupeWindow", 10).coerceIn(1, 120), renderEmoji = bool("DanmuRenderEmoji", true)),
            appearance = DesktopAppearancePreferences(theme = int("ThemeMode", 0), accent = if (s.has("kStyleColor")) s.getLong("kStyleColor") and 0xffffffffL else 0, platformOrder = order),
            followOptions = DesktopFollowPreferences(bool("AutoUpdateFollowEnable", true), int("AutoUpdateFollowDuration", 60).coerceIn(30, 3600))
        )
    }
}
