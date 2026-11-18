package com.mylive.app.core.sync

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * WebDAV profile-backup location and upload shared by Android and desktop.
 * Callers still decide which URLs they are willing to send credentials to.
 */
object WebDavBackup {
    const val FILE_NAME = "mylive_profile_backup.json"
    const val NUTSTORE_HOST = "dav.jianguoyun.com"

    fun resolveBackupUrl(serverUrl: HttpUrl): HttpUrl {
        val builder = serverUrl.newBuilder().fragment(null)
        val directory = serverUrl.encodedPath.trimEnd('/')
        val nutstoreRoot = serverUrl.isHttps &&
            serverUrl.host == NUTSTORE_HOST &&
            serverUrl.port == 443 &&
            directory in setOf("", "/dav")
        if (nutstoreRoot) {
            builder.encodedPath("/dav/MyLive/").addPathSegment(FILE_NAME)
        } else if (!serverUrl.encodedPath.endsWith(".json", ignoreCase = true)) {
            builder.encodedPath(directory.ifEmpty { "/" }).addPathSegment(FILE_NAME)
        }
        return builder.build()
    }

    fun upload(
        okHttpClient: OkHttpClient,
        backupUrl: String,
        username: String,
        password: String,
        backupJson: String
    ) {
        val firstFailure = executePut(okHttpClient, backupUrl, username, password, backupJson) ?: return
        if (firstFailure.code != 403 && firstFailure.code != 404 && firstFailure.code != 409) {
            throw firstFailure.toIOException()
        }
        val parentCollectionUrl = parentCollectionUrl(backupUrl) ?: throw firstFailure.toIOException()
        createCollection(okHttpClient, parentCollectionUrl, username, password)
        val retryFailure = executePut(okHttpClient, backupUrl, username, password, backupJson)
        if (retryFailure != null) throw retryFailure.toIOException()
    }

    private fun executePut(
        okHttpClient: OkHttpClient,
        backupUrl: String,
        username: String,
        password: String,
        backupJson: String
    ): WebDavHttpFailure? {
        val request = Request.Builder()
            .url(backupUrl)
            .put(backupJson.toRequestBody(MEDIA_TYPE))
            .applyAuth(username, password)
            .build()
        return okHttpClient.newCall(request).execute().use { it.toFailureOrNull() }
    }

    private fun createCollection(
        okHttpClient: OkHttpClient,
        collectionUrl: String,
        username: String,
        password: String
    ) {
        val request = Request.Builder()
            .url(collectionUrl)
            .method("MKCOL", EMPTY_BODY)
            .applyAuth(username, password)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful || response.code == 405) return
            throw response.toFailureOrNull()?.toIOException()
                ?: IOException("HTTP ${response.code}: ${response.message}")
        }
    }

    private fun parentCollectionUrl(backupUrl: String): String? {
        val parsed = backupUrl.toHttpUrl()
        if (parsed.pathSegments.size <= 1) return null
        val parent = parsed.newBuilder().removePathSegment(parsed.pathSegments.lastIndex).build()
        return if (parent.encodedPath.endsWith("/")) {
            parent.toString()
        } else {
            parent.newBuilder().addPathSegment("").build().toString()
        }
    }

    private fun Request.Builder.applyAuth(username: String, password: String): Request.Builder {
        if (username.isNotBlank()) header("Authorization", Credentials.basic(username, password))
        return this
    }

    private fun Response.toFailureOrNull(): WebDavHttpFailure? =
        if (isSuccessful) null else WebDavHttpFailure(code, message)

    private data class WebDavHttpFailure(val code: Int, val message: String) {
        fun toIOException(): IOException = IOException("HTTP $code: $message")
    }

    private val MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
}
