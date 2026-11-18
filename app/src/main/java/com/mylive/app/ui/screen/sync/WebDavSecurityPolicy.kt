package com.mylive.app.ui.screen.sync

import com.mylive.app.core.sync.WebDavBackup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private fun HttpUrl.isNutstoreServer(): Boolean =
    isHttps && host == WebDavBackup.NUTSTORE_HOST && port == 443

internal fun isNutstoreWebDavServer(serverUrl: String): Boolean =
    serverUrl.trim().toHttpUrlOrNull()?.isNutstoreServer() == true

fun buildWebDavBackupUrl(serverUrl: String): String {
    val trimmed = serverUrl.trim()
    require(trimmed.isNotEmpty()) { "WebDAV server URL is required" }

    val parsed = trimmed.toHttpUrlOrNull()
    require(parsed != null) { "Invalid WebDAV URL" }
    require(parsed.isHttps) { "WebDAV URL must use HTTPS" }

    return WebDavBackup.resolveBackupUrl(parsed).toString()
}
