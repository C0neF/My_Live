package com.mylive.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.site.douyin.DouyinCookieInspection
import com.mylive.app.core.site.douyin.DouyinCookieInspector
import com.mylive.app.core.site.douyin.DouyinCookieKind
import com.mylive.app.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AccountMessage {
    object BilibiliCookieSaved : AccountMessage()
    object DouyinCookieSaved : AccountMessage()
    object BilibiliLoggedOut : AccountMessage()
    object DouyinLoggedOut : AccountMessage()
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
    val message: AccountMessage? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
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
