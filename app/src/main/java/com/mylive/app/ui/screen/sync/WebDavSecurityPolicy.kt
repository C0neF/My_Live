package com.mylive.app.ui.screen.sync

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val WEB_DAV_BACKUP_FILE_NAME = "mylive_profile_backup.json"

fun buildWebDavBackupUrl(serverUrl: String): String {
    val trimmed = serverUrl.trim()
    require(trimmed.isNotEmpty()) { "WebDAV server URL is required" }

    val backupUrl = if (trimmed.endsWith(".json", ignoreCase = true)) {
        trimmed
    } else {
        "${trimmed.trimEnd('/')}/$WEB_DAV_BACKUP_FILE_NAME"
    }
    val parsed = backupUrl.toHttpUrlOrNull()
    require(parsed != null) { "Invalid WebDAV URL" }
    require(parsed.isHttps) { "WebDAV URL must use HTTPS" }
    return backupUrl
}
