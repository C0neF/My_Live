package com.mylive.app.ui.screen.sync

import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

private val WEB_DAV_BACKUP_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private val WEB_DAV_EMPTY_BODY = ByteArray(0).toRequestBody(null)
private const val WEB_DAV_MKCOL = "MKCOL"

internal fun uploadWebDavBackup(
    okHttpClient: OkHttpClient,
    backupUrl: String,
    username: String,
    password: String,
    backupJson: String
) {
    val firstFailure = executeWebDavPut(
        okHttpClient = okHttpClient,
        backupUrl = backupUrl,
        username = username,
        password = password,
        backupJson = backupJson
    ) ?: return

    if (firstFailure.code != 404 && firstFailure.code != 409) {
        throw firstFailure.toIOException()
    }

    val parentCollectionUrl = webDavParentCollectionUrl(backupUrl)
        ?: throw firstFailure.toIOException()
    createWebDavCollection(
        okHttpClient = okHttpClient,
        collectionUrl = parentCollectionUrl,
        username = username,
        password = password
    )

    val retryFailure = executeWebDavPut(
        okHttpClient = okHttpClient,
        backupUrl = backupUrl,
        username = username,
        password = password,
        backupJson = backupJson
    )
    if (retryFailure != null) {
        throw retryFailure.toIOException()
    }
}

private fun executeWebDavPut(
    okHttpClient: OkHttpClient,
    backupUrl: String,
    username: String,
    password: String,
    backupJson: String
): WebDavHttpFailure? {
    val request = Request.Builder()
        .url(backupUrl)
        .put(backupJson.toRequestBody(WEB_DAV_BACKUP_MEDIA_TYPE))
        .applyWebDavAuth(username, password)
        .build()

    return okHttpClient.newCall(request).execute().use { response ->
        response.toFailureOrNull()
    }
}

private fun createWebDavCollection(
    okHttpClient: OkHttpClient,
    collectionUrl: String,
    username: String,
    password: String
) {
    val request = Request.Builder()
        .url(collectionUrl)
        .method(WEB_DAV_MKCOL, WEB_DAV_EMPTY_BODY)
        .applyWebDavAuth(username, password)
        .build()

    okHttpClient.newCall(request).execute().use { response ->
        if (response.isSuccessful || response.code == 405) return
        throw response.toFailureOrNull()?.toIOException()
            ?: IOException("HTTP ${response.code}: ${response.message}")
    }
}

private fun webDavParentCollectionUrl(backupUrl: String): String? {
    val parsed = backupUrl.toHttpUrl()
    if (parsed.pathSegments.size <= 1) return null

    val parent = parsed.newBuilder()
        .removePathSegment(parsed.pathSegments.lastIndex)
        .build()

    return if (parent.encodedPath.endsWith("/")) {
        parent.toString()
    } else {
        parent.newBuilder()
            .addPathSegment("")
            .build()
            .toString()
    }
}

private fun Request.Builder.applyWebDavAuth(
    username: String,
    password: String
): Request.Builder {
    if (username.isNotBlank()) {
        header("Authorization", Credentials.basic(username, password))
    }
    return this
}

private fun Response.toFailureOrNull(): WebDavHttpFailure? {
    if (isSuccessful) return null
    return WebDavHttpFailure(code = code, message = message)
}

private data class WebDavHttpFailure(
    val code: Int,
    val message: String
) {
    fun toIOException(): IOException = IOException("HTTP $code: $message")
}
