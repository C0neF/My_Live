package com.mylive.app.update

import android.content.Context
import com.mylive.app.BuildConfig
import com.mylive.app.core.common.HttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

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
        return selectLatestStableReleaseForCurrentMajor(
            releasesJson = json,
            currentVersionName = BuildConfig.VERSION_NAME
        )
    }

    suspend fun downloadApk(
        updateInfo: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): File {
        val request = Request.Builder()
            .url(updateInfo.apkDownloadUrl)
            .header("User-Agent", "MyLive/${BuildConfig.VERSION_NAME}")
            .build()
        require(updateInfo.apkSizeBytes <= 0 || updateInfo.apkSizeBytes <= MAX_APK_SIZE_BYTES) {
            "下载失败：安装包超过大小限制"
        }
        val updateDir = File(context.cacheDir, "updates")
        // Navigation entries can overlap while a previous download is being cancelled. Each
        // request owns both paths, so its completion/cleanup cannot replace another request's APK.
        val targetFile = File(updateDir, "${UUID.randomUUID()}-${updateInfo.apkName.sanitizeFileName()}")
        val temporaryFile = File(updateDir, "${targetFile.name}.part")
        return suspendCancellableCoroutine { continuation ->
            val call = okHttpClient.newCall(request)
            // Keep this hook installed while the callback reads the body, not just until headers
            // arrive: cancelling the Call also unblocks a pending network read.
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            continuation.context.ensureActive()
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
                            updateDir.mkdirs()
                            body.byteStream().use { input ->
                                temporaryFile.outputStream().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    while (true) {
                                        continuation.context.ensureActive()
                                        val read = input.read(buffer)
                                        if (read == -1) break
                                        continuation.context.ensureActive()
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
                        continuation.context.ensureActive()
                        if (!temporaryFile.renameTo(targetFile)) {
                            error("下载失败：无法保存安装包")
                        }
                        continuation.context.ensureActive()
                        onProgress(100)
                        continuation.resume(targetFile) { _, file, _ -> file.delete() }
                    } catch (error: Throwable) {
                        temporaryFile.delete()
                        targetFile.delete()
                        continuation.resumeWithException(error)
                    }
                }
            })
        }
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
