package com.mylive.app.update

import android.content.Context
import com.mylive.app.BuildConfig
import com.mylive.app.core.common.HttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
    private val httpClient: HttpClient,
    private val okHttpClient: OkHttpClient,
    @param:ApplicationContext private val context: Context
) {
    suspend fun checkLatestUpdate(): AppUpdateInfo? {
        val json = httpClient.getText(
            url = LATEST_RELEASE_URL,
            header = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to "MyLive/${BuildConfig.VERSION_NAME}"
            )
        )
        val release = parseGitHubReleaseForUpdate(json) ?: return null
        return release.takeIf {
            isReleaseNewer(
                candidateVersionName = it.versionName,
                currentVersionName = BuildConfig.VERSION_NAME
            )
        }
    }

    suspend fun downloadApk(
        updateInfo: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(updateInfo.apkDownloadUrl)
            .header("User-Agent", "MyLive/${BuildConfig.VERSION_NAME}")
            .build()
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updateDir, updateInfo.apkName.sanitizeFileName())
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("下载失败：HTTP ${response.code}")
            }
            val body = response.body ?: error("下载失败：文件为空")
            val totalBytes = if (body.contentLength() > 0) body.contentLength() else updateInfo.apkSizeBytes
            body.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            onProgress(((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
        }
        onProgress(100)
        targetFile
    }

    private fun String.sanitizeFileName(): String {
        return replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "mylive-update.apk" }
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/C0neF/My_Live/releases/latest"
    }
}
