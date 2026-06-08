package com.mylive.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                    _uiState.value = _uiState.value.copy(
                        douyinCookie = cookie,
                        isLoggedInDouyin = cookie.isNotEmpty()
                    )
                }
            }
        }
    }

    fun saveBilibiliCookie(cookie: String) {
        viewModelScope.launch {
            accountRepository.setBilibiliCookie(cookie.trim())
            _uiState.value = _uiState.value.copy(
                showBilibiliCookieDialog = false,
                message = AccountMessage.BilibiliCookieSaved
            )
        }
    }

    fun saveDouyinCookie(cookie: String) {
        viewModelScope.launch {
            accountRepository.setDouyinCookie(cookie.trim())
            _uiState.value = _uiState.value.copy(
                showDouyinCookieDialog = false,
                message = AccountMessage.DouyinCookieSaved
            )
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
