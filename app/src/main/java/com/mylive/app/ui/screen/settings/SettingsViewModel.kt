package com.mylive.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settingsRepository: SettingsRepository
) : ViewModel() {

    // --- Theme / Appearance ---
    val themeMode: StateFlow<Int> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isDynamic: StateFlow<Boolean> = settingsRepository.isDynamic
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val styleColor: StateFlow<Int> = settingsRepository.styleColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0xff3498db.toInt())

    fun setThemeMode(value: Int) = viewModelScope.launch { settingsRepository.setThemeMode(value) }
    fun setIsDynamic(value: Boolean) = viewModelScope.launch { settingsRepository.setIsDynamic(value) }
    fun setStyleColor(value: Int) = viewModelScope.launch { settingsRepository.setStyleColor(value) }

    // --- Danmaku ---
    val danmuEnable: StateFlow<Boolean> = settingsRepository.danmuEnable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val danmuRenderEmoji: StateFlow<Boolean> = settingsRepository.danmuRenderEmoji
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val danmuHideScroll: StateFlow<Boolean> = settingsRepository.danmuHideScroll
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val danmuHideBottom: StateFlow<Boolean> = settingsRepository.danmuHideBottom
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val danmuHideTop: StateFlow<Boolean> = settingsRepository.danmuHideTop
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val danmuSize: StateFlow<Double> = settingsRepository.danmuSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16.0)

    val danmuSpeed: StateFlow<Double> = settingsRepository.danmuSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10.0)

    val danmuArea: StateFlow<Double> = settingsRepository.danmuArea
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.8)

    val danmuLineCount: StateFlow<Int> = settingsRepository.danmuLineCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8)

    val danmuDelay: StateFlow<Double> = settingsRepository.danmuDelay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val danmuDelayBySiteJson: StateFlow<String> = settingsRepository.danmuDelayBySiteJson
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "{}")

    val danmuOpacity: StateFlow<Double> = settingsRepository.danmuOpacity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0)

    val danmuFontWeight: StateFlow<Int> = settingsRepository.danmuFontWeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    val danmuStrokeWidth: StateFlow<Double> = settingsRepository.danmuStrokeWidth
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2.0)

    val danmuTopMargin: StateFlow<Double> = settingsRepository.danmuTopMargin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val danmuBottomMargin: StateFlow<Double> = settingsRepository.danmuBottomMargin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun setDanmuEnable(value: Boolean) = viewModelScope.launch { settingsRepository.setDanmuEnable(value) }
    fun setDanmuRenderEmoji(value: Boolean) = viewModelScope.launch { settingsRepository.setDanmuRenderEmoji(value) }
    fun setDanmuHideScroll(value: Boolean) = viewModelScope.launch { settingsRepository.setDanmuHideScroll(value) }
    fun setDanmuHideBottom(value: Boolean) = viewModelScope.launch { settingsRepository.setDanmuHideBottom(value) }
    fun setDanmuHideTop(value: Boolean) = viewModelScope.launch { settingsRepository.setDanmuHideTop(value) }
    fun setDanmuSize(value: Double) = viewModelScope.launch { settingsRepository.setDanmuSize(value) }
    fun setDanmuSpeed(value: Double) = viewModelScope.launch { settingsRepository.setDanmuSpeed(value) }
    fun setDanmuArea(value: Double) = viewModelScope.launch { settingsRepository.setDanmuArea(value) }
    fun setDanmuLineCount(value: Int) = viewModelScope.launch { settingsRepository.setDanmuLineCount(value) }
    fun setDanmuDelay(value: Double) = viewModelScope.launch { settingsRepository.setDanmuDelay(value) }
    fun setDanmuDelayBySiteJson(value: String) = viewModelScope.launch { settingsRepository.setDanmuDelayBySiteJson(value) }
    fun setDanmuOpacity(value: Double) = viewModelScope.launch { settingsRepository.setDanmuOpacity(value) }
    fun setDanmuFontWeight(value: Int) = viewModelScope.launch { settingsRepository.setDanmuFontWeight(value) }
    fun setDanmuStrokeWidth(value: Double) = viewModelScope.launch { settingsRepository.setDanmuStrokeWidth(value) }
    fun setDanmuTopMargin(value: Double) = viewModelScope.launch { settingsRepository.setDanmuTopMargin(value) }
    fun setDanmuBottomMargin(value: Double) = viewModelScope.launch { settingsRepository.setDanmuBottomMargin(value) }

    val danmuDedupeEnable: StateFlow<Boolean> = settingsRepository.danmuDedupeEnable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val danmuDedupeWindow: StateFlow<Int> = settingsRepository.danmuDedupeWindow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)

    val danmuDedupeStep: StateFlow<Int> = settingsRepository.danmuDedupeStep
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val danmuDedupeStrictMode: StateFlow<Boolean> = settingsRepository.danmuDedupeStrictMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setDanmuDedupeEnable(value: Boolean) = viewModelScope.launch { settingsRepository.setDanmuDedupeEnable(value) }
    fun setDanmuDedupeWindow(value: Int) = viewModelScope.launch { settingsRepository.setDanmuDedupeWindow(value) }
    fun setDanmuDedupeStep(value: Int) = viewModelScope.launch { settingsRepository.setDanmuDedupeStep(value) }
    fun setDanmuDedupeStrictMode(value: Boolean) = viewModelScope.launch { settingsRepository.setDanmuDedupeStrictMode(value) }

    val danmuShieldEnable: StateFlow<Boolean> = settingsRepository.danmuShieldEnable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val danmuKeywordShieldEnable: StateFlow<Boolean> = settingsRepository.danmuKeywordShieldEnable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val danmuUserShieldEnable: StateFlow<Boolean> = settingsRepository.danmuUserShieldEnable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setDanmuShieldEnable(value: Boolean) = viewModelScope.launch { settingsRepository.setDanmuShieldEnable(value) }
    fun setDanmuKeywordShieldEnable(value: Boolean) = viewModelScope.launch { settingsRepository.setDanmuKeywordShieldEnable(value) }
    fun setDanmuUserShieldEnable(value: Boolean) = viewModelScope.launch { settingsRepository.setDanmuUserShieldEnable(value) }

    // --- Playback ---
    val qualityLevel: StateFlow<Int> = settingsRepository.qualityLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val qualityLevelCellular: StateFlow<Int> = settingsRepository.qualityLevelCellular
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val scaleMode: StateFlow<Int> = settingsRepository.scaleMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hardwareDecode: StateFlow<Boolean> = settingsRepository.hardwareDecode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val playerCompatMode: StateFlow<Boolean> = settingsRepository.playerCompatMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val playerAutoPause: StateFlow<Boolean> = settingsRepository.playerAutoPause
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val allowBackgroundPlayback: StateFlow<Boolean> = settingsRepository.allowBackgroundPlayback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val playerForceHttps: StateFlow<Boolean> = settingsRepository.playerForceHttps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setQualityLevel(value: Int) = viewModelScope.launch { settingsRepository.setQualityLevel(value) }
    fun setQualityLevelCellular(value: Int) = viewModelScope.launch { settingsRepository.setQualityLevelCellular(value) }
    fun setScaleMode(value: Int) = viewModelScope.launch { settingsRepository.setScaleMode(value) }
    fun setHardwareDecode(value: Boolean) = viewModelScope.launch { settingsRepository.setHardwareDecode(value) }
    fun setPlayerCompatMode(value: Boolean) = viewModelScope.launch { settingsRepository.setPlayerCompatMode(value) }
    fun setPlayerAutoPause(value: Boolean) = viewModelScope.launch { settingsRepository.setPlayerAutoPause(value) }
    fun setAllowBackgroundPlayback(value: Boolean) = viewModelScope.launch { settingsRepository.setAllowBackgroundPlayback(value) }
    fun setPlayerForceHttps(value: Boolean) = viewModelScope.launch { settingsRepository.setPlayerForceHttps(value) }

    // --- Auto Exit ---
    val autoExitEnable: StateFlow<Boolean> = settingsRepository.autoExitEnable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoExitDuration: StateFlow<Int> = settingsRepository.autoExitDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)

    val roomAutoExitDuration: StateFlow<Int> = settingsRepository.roomAutoExitDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setAutoExitEnable(value: Boolean) = viewModelScope.launch { settingsRepository.setAutoExitEnable(value) }
    fun setAutoExitDuration(value: Int) = viewModelScope.launch { settingsRepository.setAutoExitDuration(value) }
    fun setRoomAutoExitDuration(value: Int) = viewModelScope.launch { settingsRepository.setRoomAutoExitDuration(value) }

    // --- Other ---
    val debugMode: StateFlow<Boolean> = settingsRepository.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val logEnable: StateFlow<Boolean> = settingsRepository.logEnable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val contributionRankEnable: StateFlow<Boolean> = settingsRepository.contributionRankEnable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val superChatSortDesc: StateFlow<Boolean> = settingsRepository.superChatSortDesc
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val chatTextSize: StateFlow<Double> = settingsRepository.chatTextSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 14.0)

    val chatBubbleStyle: StateFlow<Boolean> = settingsRepository.chatBubbleStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val chatTextGap: StateFlow<Double> = settingsRepository.chatTextGap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4.0)

    val autoFullScreen: StateFlow<Boolean> = settingsRepository.autoFullScreen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setDebugMode(value: Boolean) = viewModelScope.launch { settingsRepository.setDebugMode(value) }
    fun setLogEnable(value: Boolean) = viewModelScope.launch { settingsRepository.setLogEnable(value) }
    fun setContributionRankEnable(value: Boolean) = viewModelScope.launch { settingsRepository.setContributionRankEnable(value) }
    fun setSuperChatSortDesc(value: Boolean) = viewModelScope.launch { settingsRepository.setSuperChatSortDesc(value) }
    fun setChatTextSize(value: Double) = viewModelScope.launch { settingsRepository.setChatTextSize(value) }
    fun setChatBubbleStyle(value: Boolean) = viewModelScope.launch { settingsRepository.setChatBubbleStyle(value) }
    fun setChatTextGap(value: Double) = viewModelScope.launch { settingsRepository.setChatTextGap(value) }
    fun setAutoFullScreen(value: Boolean) = viewModelScope.launch { settingsRepository.setAutoFullScreen(value) }

    // --- Follow ---
    val autoUpdateFollowEnable: StateFlow<Boolean> = settingsRepository.autoUpdateFollowEnable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoUpdateFollowDuration: StateFlow<Int> = settingsRepository.autoUpdateFollowDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)

    val updateFollowThreadCount: StateFlow<Int> = settingsRepository.updateFollowThreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8)

    fun setAutoUpdateFollowEnable(value: Boolean) = viewModelScope.launch { settingsRepository.setAutoUpdateFollowEnable(value) }
    fun setAutoUpdateFollowDuration(value: Int) = viewModelScope.launch { settingsRepository.setAutoUpdateFollowDuration(value) }
    fun setUpdateFollowThreadCount(value: Int) = viewModelScope.launch { settingsRepository.setUpdateFollowThreadCount(value) }

    // --- Sorting ---
    val siteSort: StateFlow<String> = settingsRepository.siteSort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "bilibili,douyu,huya,douyin")

    val homeSort: StateFlow<String> = settingsRepository.homeSort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "recommend,follow,category,user")

    val liveRoomTabSort: StateFlow<String> = settingsRepository.liveRoomTabSort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "chat,super_chat,follow,settings")

    val liveRoomQuickAccessSort: StateFlow<String> = settingsRepository.liveRoomQuickAccessSort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "follow,history,recommendation")

    val liveRoomQuickAccessEnabled: StateFlow<Boolean> = settingsRepository.liveRoomQuickAccessEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setSiteSort(value: String) = viewModelScope.launch { settingsRepository.setSiteSort(value) }
    fun setHomeSort(value: String) = viewModelScope.launch { settingsRepository.setHomeSort(value) }
    fun setLiveRoomTabSort(value: String) = viewModelScope.launch { settingsRepository.setLiveRoomTabSort(value) }
    fun setLiveRoomQuickAccessSort(value: String) = viewModelScope.launch { settingsRepository.setLiveRoomQuickAccessSort(value) }
    fun setLiveRoomQuickAccessEnabled(value: Boolean) = viewModelScope.launch { settingsRepository.setLiveRoomQuickAccessEnabled(value) }

    // --- PiP ---
    val autoPipOnExit: StateFlow<Boolean> = settingsRepository.autoPipOnExit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val pipHideDanmu: StateFlow<Boolean> = settingsRepository.pipHideDanmu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAutoPipOnExit(value: Boolean) = viewModelScope.launch { settingsRepository.setAutoPipOnExit(value) }
    fun setPipHideDanmu(value: Boolean) = viewModelScope.launch { settingsRepository.setPipHideDanmu(value) }
}
