package com.mylive.app.ui.screen.settings

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.mylive.app.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

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

    private var pollJob: Job? = null
    private var qrcodeKey: String = ""

    init {
        loadQRCode()
    }

    fun loadQRCode() {
        qrcodeKey = ""
        stopPoll()
        _uiState.value = QRLoginUiState(status = QRStatus.Loading)
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP error: ${response.code}")
                    }
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val code = json.optInt("code", -1)
                    if (code != 0) {
                        throw Exception(json.optString("message", "获取二维码失败"))
                    }
                    val data = json.getJSONObject("data")
                    val key = data.getString("qrcode_key")
                    val url = data.getString("url")
                    
                    val bitmap = withContext(Dispatchers.Default) {
                        generateQrCode(url)
                    }
                    
                    if (bitmap != null) {
                        qrcodeKey = key
                        _uiState.value = QRLoginUiState(
                            status = QRStatus.Unscanned,
                            qrCodeUrl = url,
                            qrCodeBitmap = bitmap
                        )
                        startPoll(key)
                    } else {
                        throw Exception("生成二维码失败")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = QRLoginUiState(
                    status = QRStatus.Failed,
                    errorMsg = e.localizedMessage ?: "获取二维码失败"
                )
            }
        }
    }

    private fun startPoll(key: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(3000)
                pollQRStatus(key)
            }
        }
    }

    private fun stopPoll() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun pollQRStatus(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$key")
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@launch
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val code = json.optInt("code", -1)
                    if (code != 0) return@launch
                    val data = json.getJSONObject("data")
                    val pollCode = data.optInt("code", -1)
                    
                    when (pollCode) {
                        0 -> {
                            val cookieHeaders = response.headers("Set-Cookie")
                            val cookieList = mutableListOf<String>()
                            for (header in cookieHeaders) {
                                val part = header.split(";").firstOrNull()
                                if (!part.isNullOrBlank()) {
                                    cookieList.add(part.trim())
                                }
                            }
                            if (cookieList.isNotEmpty()) {
                                val cookieStr = cookieList.joinToString("; ")
                                accountRepository.setBilibiliCookie(cookieStr)
                                _uiState.value = _uiState.value.copy(
                                    loginSuccess = true
                                )
                                stopPoll()
                            }
                        }
                        86038 -> {
                            _uiState.value = _uiState.value.copy(status = QRStatus.Expired)
                            stopPoll()
                        }
                        86090 -> {
                            _uiState.value = _uiState.value.copy(status = QRStatus.Scanned)
                        }
                        86101 -> {
                            _uiState.value = _uiState.value.copy(status = QRStatus.Unscanned)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore temporary network failures while polling to prevent crashing
            }
        }
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
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        stopPoll()
        super.onCleared()
    }
}
