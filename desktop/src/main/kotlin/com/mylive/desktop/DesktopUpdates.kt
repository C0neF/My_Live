package com.mylive.desktop

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class DesktopRelease(val version: String, val url: String, val windowsAssets: List<Pair<String, String>>, val checksumsUrl: String? = null) {
    val newer: Boolean get() = desktopVersionIsNewer(version, DESKTOP_VERSION)
}

internal data class DesktopVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<DesktopVersion> {
    override fun compareTo(other: DesktopVersion) = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    override fun toString() = "$major.$minor.$patch"
    companion object {
        private val syntax = Regex("^(?:windows-)?[vV]?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
        fun parse(value: String): DesktopVersion? {
            val parts = syntax.matchEntire(value)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return null } ?: return null
            return DesktopVersion(parts[0], parts[1], parts[2])
        }
    }
}

internal fun desktopVersionIsNewer(remote: String, current: String): Boolean =
    (DesktopVersion.parse(remote) ?: return false) > (DesktopVersion.parse(current) ?: return false)

class DesktopUpdates(private val client: OkHttpClient) {
    suspend fun latest(): DesktopRelease? = client.desktopHttp(Request.Builder()
        .url("https://api.github.com/repos/C0neF/My_Live/releases?per_page=100").header("Accept", "application/vnd.github+json")
        .header("User-Agent", "MyLive-Windows/$DESKTOP_VERSION").build()) {
        check(it.isSuccessful) { "检查更新失败（HTTP ${it.code}）" }; decodeCatalog(it.boundedText(2 * 1024 * 1024))
    }

    internal fun decodeCatalog(text: String): DesktopRelease? {
        val rows = JSONArray(text)
        require(rows.length() <= 100) { "发布列表过大" }
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            if (row.optBoolean("draft") || row.optBoolean("prerelease")) return@mapNotNull null
            runCatching { decode(row.toString()) }.getOrNull()?.takeIf { it.windowsAssets.isNotEmpty() }
        }.maxByOrNull { requireNotNull(DesktopVersion.parse(it.version)) }
    }

    internal fun decode(text: String): DesktopRelease {
        val obj = JSONObject(text)
        val tag = obj.getString("tag_name")
        val version = requireNotNull(DesktopVersion.parse(tag)) { "不支持的正式版本号" }
        val link = obj.getString("html_url")
        require(link == "$DESKTOP_PROJECT_URL/releases/tag/$tag") { "发布地址无效" }
        val assets = obj.optJSONArray("assets")
        var checksumsUrl: String? = null
        val windows = if (assets == null) emptyList() else (0 until assets.length()).mapNotNull { i ->
            val row = assets.optJSONObject(i) ?: return@mapNotNull null
            val name = row.optString("name"); val url = row.optString("browser_download_url")
            if (!Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,180}$").matches(name) || url != "$DESKTOP_PROJECT_URL/releases/download/$tag/$name") return@mapNotNull null
            if (name == "SHA256SUMS.txt") checksumsUrl = url
            val lower = name.lowercase()
            if ((name.startsWith("MyLive-") || lower == "mylive.exe") && !lower.contains("source") &&
                (lower.endsWith(".exe") || lower.endsWith(".msi") || lower.contains("windows") && lower.endsWith(".zip"))) name to url else null
        }
        return DesktopRelease(version.toString(), link, windows.distinctBy { it.first }, checksumsUrl)
    }
}
