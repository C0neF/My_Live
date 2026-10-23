package com.mylive.app.ui.screen.settings

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.MainThread
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.mylive.app.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class QRStatus {
    Loading,
    Unscanned,
    Scanned,
    Expired,
    Failed
}

data class QRLoginUiState(
    val status: QRStatus = QRStatus.Loading,
    val qrCodeUrl: String = "",
    val qrCodeBitmap: Bitmap? = null,
    val loginSuccess: Boolean = false,
    val errorMsg: String? = null
)

@HiltViewModel
class BiliBiliQRLoginViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(QRLoginUiState())
    val uiState: StateFlow<QRLoginUiState> = _uiState.asStateFlow()

    // Jobs, generation and UI state are owned by Main; HTTP callbacks only return data.
    private var loadJob: Job? = null
    private var pollJob: Job? = null
    private var qrGeneration = 0L

    init {
        loadQRCode()
    }

    @MainThread
    fun loadQRCode() {
        val generation = ++qrGeneration
        loadJob?.cancel()
        stopPoll()
        _uiState.value = QRLoginUiState(status = QRStatus.Loading)

        loadJob = viewModelScope.launch {
            try {
                val request = Request.Builder()
                    .url("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
                    .get()
                    .build()
                val response = requestQr(request)
                currentCoroutineContext().ensureActive()
                if (generation != qrGeneration) return@launch
                if (response.code !in 200..299) {
                    throw Exception("HTTP error: ${response.code}")
                }
                val json = withContext(Dispatchers.Default) { JSONObject(response.body) }
                if (json.optInt("code", -1) != 0) {
                    throw Exception(json.optString("message", "获取二维码失败"))
                }
                val data = json.getJSONObject("data")
                val key = data.getString("qrcode_key")
                val url = data.getString("url")
                val bitmap = withContext(Dispatchers.Default) {
                    generateQrCode(url)
                } ?: throw Exception("生成二维码失败")

                currentCoroutineContext().ensureActive()
                if (generation != qrGeneration) return@launch
                _uiState.value = QRLoginUiState(
                    status = QRStatus.Unscanned,
                    qrCodeUrl = url,
                    qrCodeBitmap = bitmap
                )
                startPoll(key, generation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                if (generation == qrGeneration) {
                    _uiState.value = QRLoginUiState(
                        status = QRStatus.Failed,
                        errorMsg = e.localizedMessage ?: "获取二维码失败"
                    )
                }
            }
        }
    }

    private fun startPoll(key: String, generation: Long) {
        stopPoll()
        pollJob = viewModelScope.launch {
            while (isActive && generation == qrGeneration) {
                delay(3000)
                if (!pollQRStatus(key, generation)) break
            }
        }
    }

    private fun stopPoll() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Returns false on a terminal response so only this generation's loop can finish. */
    private suspend fun pollQRStatus(key: String, generation: Long): Boolean {
        return try {
            val request = Request.Builder()
                .url("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$key")
                .get()
                .build()
            val response = requestQr(request)
            currentCoroutineContext().ensureActive()
            if (generation != qrGeneration) return false
            if (response.code !in 200..299) return true
            val json = withContext(Dispatchers.Default) { JSONObject(response.body) }
            currentCoroutineContext().ensureActive()
            if (generation != qrGeneration) return false
            if (json.optInt("code", -1) != 0) return true
            val data = json.getJSONObject("data")

            when (data.optInt("code", -1)) {
                0 -> {
                    val cookies = response.cookies.mapNotNull { header ->
                        header.substringBefore(';').trim().takeIf { it.isNotEmpty() }
                    }
                    if (cookies.isEmpty()) return true
                    currentCoroutineContext().ensureActive()
                    if (generation != qrGeneration) return false
                    withContext(Dispatchers.IO) {
                        accountRepository.setBilibiliCookie(cookies.joinToString("; "))
                    }
                    currentCoroutineContext().ensureActive()
                    if (generation == qrGeneration) {
                        _uiState.value = _uiState.value.copy(loginSuccess = true)
                    }
                    false
                }
                86038 -> {
                    _uiState.value = _uiState.value.copy(status = QRStatus.Expired)
                    false
                }
                86090 -> {
                    _uiState.value = _uiState.value.copy(status = QRStatus.Scanned)
                    true
                }
                86101 -> {
                    _uiState.value = _uiState.value.copy(status = QRStatus.Unscanned)
                    true
                }
                else -> true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Temporary failures leave the current QR polling, but cancellation never does.
            currentCoroutineContext().ensureActive()
            generation == qrGeneration
        }
    }

    private data class QrResponse(val code: Int, val body: String, val cookies: List<String>)

    private suspend fun requestQr(request: Request): QrResponse = suspendCancellableCoroutine { continuation ->
        val call = okHttpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        if (!continuation.isActive) return
                        // Read the body before resuming, keeping cancellation attached to the call.
                        QrResponse(response.code, response.body?.string().orEmpty(), response.headers("Set-Cookie"))
                    }
                    continuation.resume(result)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }

    private fun generateQrCode(content: String): Bitmap? {
        return try {
            val size = 512
            val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        qrGeneration++
        loadJob?.cancel()
        loadJob = null
        stopPoll()
        super.onCleared()
    }
}
