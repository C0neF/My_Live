package com.mylive.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.site.douyin.DouyinCookieInspection
import com.mylive.app.core.site.douyin.DouyinCookieInspector
import com.mylive.app.core.site.douyin.DouyinCookieKind
import com.mylive.app.data.repository.AccountRepository
import com.mylive.app.core.site.douyu.DouyuAccountSession
import com.mylive.app.core.site.douyu.DouyuCookieInspection
import com.mylive.app.core.site.douyu.inspectDouyuCookie
import com.mylive.app.core.site.douyu.normalizeDouyuCookie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

sealed class AccountMessage {
    object BilibiliCookieSaved : AccountMessage()
    object DouyinCookieSaved : AccountMessage()
    object BilibiliLoggedOut : AccountMessage()
    object DouyinLoggedOut : AccountMessage()
    data class DouyuNotice(val text: String) : AccountMessage()
}

data class AccountUiState(
    val bilibiliCookie: String = "",
    val douyinCookie: String = "",
    val isLoggedInBiliBili: Boolean = false,
    val isLoggedInDouyin: Boolean = false,
    val hasDouyinCookie: Boolean = false,
    val douyinCookieInspection: DouyinCookieInspection =
        DouyinCookieInspection(DouyinCookieKind.NotConfigured),
    val showBilibiliCookieDialog: Boolean = false,
    val showDouyinCookieDialog: Boolean = false,
    val douyuInspection: DouyuCookieInspection = inspectDouyuCookie(""),
    val showDouyuCookieDialog: Boolean = false,
    val douyuBusy: Boolean = false,
    val message: AccountMessage? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val douyuSession: DouyuAccountSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Delay DataStore reads slightly to ensure enter transition runs smoothly
            kotlinx.coroutines.delay(250)
            
            launch {
                accountRepository.bilibiliCookie.collect { cookie ->
                    _uiState.value = _uiState.value.copy(
                        bilibiliCookie = cookie,
                        isLoggedInBiliBili = cookie.isNotEmpty()
                    )
                }
            }
            
            launch {
                accountRepository.douyinCookie.collect { cookie ->
                    val inspection = DouyinCookieInspector.inspect(cookie)
                    _uiState.value = _uiState.value.copy(
                        douyinCookie = cookie,
                        hasDouyinCookie = cookie.isNotBlank(),
                        isLoggedInDouyin = isDouyinLoginState(inspection),
                        douyinCookieInspection = inspection
                    )
                }
            }
            launch {
                accountRepository.douyuCookie.collect { cookie ->
                    _uiState.value = _uiState.value.copy(douyuInspection = inspectDouyuCookie(cookie))
                }
            }
        }
    }

    fun saveBilibiliCookie(cookie: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            accountRepository.setBilibiliCookie(cookie.trim())
            _uiState.value = _uiState.value.copy(
                showBilibiliCookieDialog = false,
                message = AccountMessage.BilibiliCookieSaved
            )
            onSaved()
        }
    }

    fun saveDouyinCookie(cookie: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            accountRepository.setDouyinCookie(DouyinCookieInspector.normalizeInput(cookie))
            _uiState.value = _uiState.value.copy(
                showDouyinCookieDialog = false,
                message = AccountMessage.DouyinCookieSaved
            )
            onSaved()
        }
    }

    fun logoutBiliBili() {
        viewModelScope.launch {
            accountRepository.logoutBiliBili()
            _uiState.value = _uiState.value.copy(message = AccountMessage.BilibiliLoggedOut)
        }
    }

    fun logoutDouyin() {
        viewModelScope.launch {
            accountRepository.logoutDouyin()
            _uiState.value = _uiState.value.copy(message = AccountMessage.DouyinLoggedOut)
        }
    }

    fun saveDouyuCookie(
        cookie: String, onSaved: () -> Unit = {}, onSaveFailed: () -> Unit = {}
    ) {
        if (_uiState.value.douyuBusy) return
        val normalized = normalizeDouyuCookie(cookie)
        val inspection = inspectDouyuCookie(normalized)
        if (!inspection.hasLoginToken && !inspection.canRenew) {
            _uiState.value = _uiState.value.copy(message = AccountMessage.DouyuNotice("未检测到斗鱼登录状态，请完成网页登录或导入完整 Cookie"))
            return
        }
        _uiState.value = _uiState.value.copy(douyuBusy = true)
        viewModelScope.launch {
            try {
                accountRepository.setDouyuCookie(normalized)
                val renewed = if (inspection.canRenew) douyuSession.renew() else false
                _uiState.value = _uiState.value.copy(
                    showDouyuCookieDialog = false,
                    message = AccountMessage.DouyuNotice(if (renewed) "斗鱼登录态已保存并续期" else "斗鱼配置已保存")
                )
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(message = AccountMessage.DouyuNotice("斗鱼配置保存失败，请重试"))
                onSaveFailed()
            } finally { _uiState.value = _uiState.value.copy(douyuBusy = false) }
        }
    }

    fun renewDouyuCookie() {
        if (_uiState.value.douyuBusy) return
        _uiState.value = _uiState.value.copy(douyuBusy = true)
        viewModelScope.launch {
            try {
                val renewed = douyuSession.renew()
                _uiState.value = _uiState.value.copy(message = AccountMessage.DouyuNotice(
                    if (renewed) "斗鱼登录态已续期" else "续期未成功，请检查网络或重新网页登录"
                ))
            } finally { _uiState.value = _uiState.value.copy(douyuBusy = false) }
        }
    }

    fun logoutDouyu() {
        viewModelScope.launch {
            accountRepository.logoutDouyu()
            _uiState.value = _uiState.value.copy(message = AccountMessage.DouyuNotice("斗鱼本机登录状态已清除"))
        }
    }

    fun showDouyuCookieDialog() { _uiState.value = _uiState.value.copy(showDouyuCookieDialog = true) }
    fun hideDouyuCookieDialog() { _uiState.value = _uiState.value.copy(showDouyuCookieDialog = false) }

    fun showBilibiliCookieDialog() {
        _uiState.value = _uiState.value.copy(showBilibiliCookieDialog = true)
    }

    fun hideBilibiliCookieDialog() {
        _uiState.value = _uiState.value.copy(showBilibiliCookieDialog = false)
    }

    fun showDouyinCookieDialog() {
        _uiState.value = _uiState.value.copy(showDouyinCookieDialog = true)
    }

    fun hideDouyinCookieDialog() {
        _uiState.value = _uiState.value.copy(showDouyinCookieDialog = false)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
