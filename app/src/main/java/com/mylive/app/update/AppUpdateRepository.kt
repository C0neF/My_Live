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
            url = RELEASES_URL,
            queryParameters = mapOf("per_page" to "100"),
            header = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to "MyLive/${BuildConfig.VERSION_NAME}"
            )
        )
        return selectLatestStableReleaseForMajor(
            releasesJson = json,
            currentVersionName = BuildConfig.VERSION_NAME,
            majorVersion = 1
        )
    }

    suspend fun downloadApk(
        updateInfo: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(updateInfo.apkDownloadUrl)
            .header("User-Agent", "MyLive/${BuildConfig.VERSION_NAME}")
            .build()
        require(updateInfo.apkSizeBytes <= 0 || updateInfo.apkSizeBytes <= MAX_APK_SIZE_BYTES) {
            "下载失败：安装包超过大小限制"
        }
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updateDir, updateInfo.apkName.sanitizeFileName())
        val temporaryFile = File(updateDir, "${targetFile.name}.part")
        temporaryFile.delete()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("下载失败：HTTP ${response.code}")
                }
                val body = response.body ?: error("下载失败：文件为空")
                val responseSize = body.contentLength()
                require(responseSize <= 0 || responseSize <= MAX_APK_SIZE_BYTES) {
                    "下载失败：安装包超过大小限制"
                }
                val totalBytes = if (responseSize > 0) responseSize else updateInfo.apkSizeBytes
                var downloadedBytes = 0L
                body.byteStream().use { input ->
                    temporaryFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            downloadedBytes += read
                            require(downloadedBytes <= MAX_APK_SIZE_BYTES) {
                                "下载失败：安装包超过大小限制"
                            }
                            output.write(buffer, 0, read)
                            if (totalBytes > 0) {
                                onProgress(
                                    ((downloadedBytes * 100) / totalBytes)
                                        .toInt()
                                        .coerceIn(0, 100)
                                )
                            }
                        }
                    }
                }
                require(downloadedBytes > 0) { "下载失败：文件为空" }
                require(responseSize <= 0 || downloadedBytes == responseSize) {
                    "下载失败：文件不完整"
                }
            }
            if (targetFile.exists() && !targetFile.delete()) {
                error("下载失败：无法替换旧安装包")
            }
            if (!temporaryFile.renameTo(targetFile)) {
                error("下载失败：无法保存安装包")
            }
        } catch (error: Throwable) {
            temporaryFile.delete()
            throw error
        }
        onProgress(100)
        targetFile
    }

    private fun String.sanitizeFileName(): String {
        return replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "mylive-update.apk" }
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/C0neF/My_Live/releases"
        const val MAX_APK_SIZE_BYTES = 250L * 1024L * 1024L
    }
}
