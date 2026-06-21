package com.mylive.app.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val apkName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long
)

fun parseGitHubReleaseForUpdate(json: String): AppUpdateInfo? {
    val obj = Json.parseToJsonElement(json).jsonObject
    if (obj.boolean("prerelease")) return null

    val tagName = obj.string("tag_name").trim()
    val versionName = normalizeReleaseVersionName(tagName)
    if (versionName.isBlank()) return null

    val assets = obj["assets"]?.jsonArray ?: return null
    for (assetElement in assets) {
        val asset = assetElement.jsonObject
        val name = asset.string("name").trim()
        val downloadUrl = asset.string("browser_download_url").trim()
        if (isApkAsset(name, downloadUrl)) {
            return AppUpdateInfo(
                tagName = tagName,
                versionName = versionName,
                releaseName = obj.string("name").ifBlank { tagName },
                releaseNotes = obj.string("body"),
                releasePageUrl = obj.string("html_url"),
                apkName = name,
                apkDownloadUrl = downloadUrl,
                apkSizeBytes = asset.long("size")
            )
        }
    }
    return null
}

fun isReleaseNewer(candidateVersionName: String, currentVersionName: String): Boolean {
    return compareVersionNames(candidateVersionName, currentVersionName) > 0
}

internal fun normalizeReleaseVersionName(tagName: String): String {
    return tagName.trim()
        .removePrefix("refs/tags/")
        .removePrefix("v")
        .removePrefix("V")
}

internal fun compareVersionNames(left: String, right: String): Int {
    val leftSegments = left.versionSegments()
    val rightSegments = right.versionSegments()
    val maxSize = maxOf(leftSegments.size, rightSegments.size)
    for (index in 0 until maxSize) {
        val leftValue = leftSegments.getOrElse(index) { 0 }
        val rightValue = rightSegments.getOrElse(index) { 0 }
        if (leftValue != rightValue) {
            return leftValue.compareTo(rightValue)
        }
    }
    return 0
}

private fun String.versionSegments(): List<Int> {
    return trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.', '-', '_')
        .mapNotNull { segment ->
            segment.takeWhile { it.isDigit() }.takeIf { it.isNotBlank() }?.toIntOrNull()
        }
}

private fun isApkAsset(name: String, downloadUrl: String): Boolean {
    return name.endsWith(".apk", ignoreCase = true) &&
        downloadUrl.startsWith("https://", ignoreCase = true)
}

private fun JsonObject.string(key: String): String {
    return this[key]?.jsonPrimitive?.content.orEmpty()
}

private fun JsonObject.long(key: String): Long {
    return this[key]?.jsonPrimitive?.longOrNull ?: 0L
}

private fun JsonObject.boolean(key: String): Boolean {
    return this[key]?.jsonPrimitive?.booleanOrNull ?: false
}
