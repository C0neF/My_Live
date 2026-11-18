package com.mylive.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DesktopQrStatus { Loading, Unscanned, Scanned, Expired, Failed, Success }
data class DesktopQrState(val status: DesktopQrStatus = DesktopQrStatus.Loading, val url: String = "", val message: String? = null)

class BilibiliQrController(
    private val api: BilibiliQrApi, private val onAuthenticated: suspend (String) -> Boolean,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val pollingMillis: Long = 3_000, private val lifetimeMillis: Long = 180_000
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow(DesktopQrState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    fun load() {
        job?.cancel()
        val token = ++generation
        _state.value = DesktopQrState()
        job = scope.launch {
            try {
                val challenge = api.challenge()
                ensureActive(); if (token != generation) return@launch
                _state.value = DesktopQrState(DesktopQrStatus.Unscanned, challenge.url)
                val cookie = withTimeout(lifetimeMillis) {
                    while (isActive) {
                        delay(pollingMillis)
                        val result = try { api.poll(challenge.key) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { _state.value = _state.value.copy(message = "网络暂时不可用，正在重试…"); continue }
                        ensureActive(); if (token != generation) return@withTimeout null
                        when (result.code) {
                            0 -> {
                                require(result.cookie.split(';').any { it.trim().startsWith("SESSDATA=") && it.substringAfter('=').isNotBlank() }) { "登录响应缺少凭据" }
                                return@withTimeout result.cookie
                            }
                            86038 -> { _state.value = _state.value.copy(status = DesktopQrStatus.Expired, message = "二维码已过期"); return@withTimeout null }
                            86090 -> _state.value = _state.value.copy(status = DesktopQrStatus.Scanned, message = "已扫码，请在手机上确认")
                            86101 -> _state.value = _state.value.copy(status = DesktopQrStatus.Unscanned, message = null)
                        }
                    }
                    null
                }
                if (cookie != null) {
                    val saved = onAuthenticated(cookie)
                    ensureActive()
                    if (token == generation) _state.value = DesktopQrState(
                        if (saved) DesktopQrStatus.Success else DesktopQrStatus.Failed,
                        message = if (saved) "登录成功" else "登录信息保存失败，请重试")
                }
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                if (token == generation) _state.value = _state.value.copy(status = DesktopQrStatus.Expired, message = "二维码已过期")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == generation) _state.value = _state.value.copy(status = DesktopQrStatus.Failed, message = "二维码登录失败，请刷新重试") }
        }
    }
    fun close() { generation++; scope.cancel() }
}
