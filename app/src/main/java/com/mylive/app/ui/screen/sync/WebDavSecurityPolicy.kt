package com.mylive.app.ui.screen.sync

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val WEB_DAV_BACKUP_FILE_NAME = "mylive_profile_backup.json"
private const val NUTSTORE_HOST = "dav.jianguoyun.com"

private fun HttpUrl.isNutstoreServer(): Boolean = isHttps && host == NUTSTORE_HOST && port == 443

internal fun isNutstoreWebDavServer(serverUrl: String): Boolean =
    serverUrl.trim().toHttpUrlOrNull()?.isNutstoreServer() == true

fun buildWebDavBackupUrl(serverUrl: String): String {
    val trimmed = serverUrl.trim()
    require(trimmed.isNotEmpty()) { "WebDAV server URL is required" }

    val parsed = trimmed.toHttpUrlOrNull()
    require(parsed != null) { "Invalid WebDAV URL" }
    require(parsed.isHttps) { "WebDAV URL must use HTTPS" }

    val builder = parsed.newBuilder().fragment(null)
    val directory = parsed.encodedPath.trimEnd('/')
    if (parsed.isNutstoreServer() && directory in setOf("", "/dav")) {
        // Nutstore's DAV root lists collections. Put the default backup inside our own
        // collection; the uploader already creates this parent when it is missing.
        // Both backup and restore resolve through this function, so their locations agree.
        builder.encodedPath("/dav/MyLive/").addPathSegment(WEB_DAV_BACKUP_FILE_NAME)
    } else if (!parsed.encodedPath.endsWith(".json", ignoreCase = true)) {
        builder.encodedPath(directory.ifEmpty { "/" }).addPathSegment(WEB_DAV_BACKUP_FILE_NAME)
    }
    return builder.build().toString()
}
