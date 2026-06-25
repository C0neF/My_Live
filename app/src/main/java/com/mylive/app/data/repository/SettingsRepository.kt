package com.mylive.app.data.repository

import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val sensitiveCredentialStore: SensitiveCredentialStore
) {
    // Theme
    val themeMode: Flow<Int> = settingsDataStore.getFlow(SettingsDataStore.ThemeMode, 0)
    suspend fun setThemeMode(value: Int) = settingsDataStore.setValue(SettingsDataStore.ThemeMode, value)

    // Dynamic color
    val isDynamic: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.kIsDynamic, false)
    suspend fun setIsDynamic(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.kIsDynamic, value)

    // Style color
    val styleColor: Flow<Int> = settingsDataStore.getFlow(SettingsDataStore.kStyleColor, 0xff3498db.toInt())
    suspend fun setStyleColor(value: Int) = settingsDataStore.setValue(SettingsDataStore.kStyleColor, value)

    // Danmaku settings
    val danmuEnable: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.DanmuEnable, true)
    suspend fun setDanmuEnable(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.DanmuEnable, value)

    val danmuSize: Flow<Double> = settingsDataStore.getFlow(SettingsDataStore.DanmuSize, 16.0)
    suspend fun setDanmuSize(value: Double) = settingsDataStore.setValue(SettingsDataStore.DanmuSize, value)

    val danmuSpeed: Flow<Double> = settingsDataStore.getFlow(SettingsDataStore.DanmuSpeed, 10.0)
    suspend fun setDanmuSpeed(value: Double) = settingsDataStore.setValue(SettingsDataStore.DanmuSpeed, value)

    val danmuArea: Flow<Double> = settingsDataStore.getFlow(SettingsDataStore.DanmuArea, 0.8)
    suspend fun setDanmuArea(value: Double) = settingsDataStore.setValue(SettingsDataStore.DanmuArea, value)

    val danmuLineCount: Flow<Int> = settingsDataStore.getFlow(SettingsDataStore.DanmuLineCount, 8)
    suspend fun setDanmuLineCount(value: Int) = settingsDataStore.setValue(SettingsDataStore.DanmuLineCount, value)

    val danmuDelay: Flow<Double> = settingsDataStore.getFlow(SettingsDataStore.DanmuDelay, 0.0)
    suspend fun setDanmuDelay(value: Double) = settingsDataStore.setValue(SettingsDataStore.DanmuDelay, value)

    val danmuDelayBySiteJson: Flow<String> = settingsDataStore.getFlow(SettingsDataStore.DanmuDelayBySite, "{}")
    suspend fun setDanmuDelayBySiteJson(value: String) = settingsDataStore.setValue(SettingsDataStore.DanmuDelayBySite, value)

    val danmuOpacity: Flow<Double> = settingsDataStore.getFlow(SettingsDataStore.DanmuOpacity, 1.0)
    suspend fun setDanmuOpacity(value: Double) = settingsDataStore.setValue(SettingsDataStore.DanmuOpacity, value)

    val danmuShieldEnable: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.DanmuShieldEnable, true)
    suspend fun setDanmuShieldEnable(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.DanmuShieldEnable, value)

    val danmuKeywordShieldEnable: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.DanmuKeywordShieldEnable, true)
    suspend fun setDanmuKeywordShieldEnable(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.DanmuKeywordShieldEnable, value)

    val danmuUserShieldEnable: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.DanmuUserShieldEnable, true)
    suspend fun setDanmuUserShieldEnable(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.DanmuUserShieldEnable, value)

    // Quality
    val qualityLevel: Flow<Int> = settingsDataStore.getFlow(SettingsDataStore.QualityLevel, 1)
    suspend fun setQualityLevel(value: Int) = settingsDataStore.setValue(SettingsDataStore.QualityLevel, value)

    val qualityLevelCellular: Flow<Int> = settingsDataStore.getFlow(SettingsDataStore.QualityLevelCellular, 1)
    suspend fun setQualityLevelCellular(value: Int) = settingsDataStore.setValue(SettingsDataStore.QualityLevelCellular, value)

    // Danmaku extended
    val danmuRenderEmoji: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.DanmuRenderEmoji, true)
    suspend fun setDanmuRenderEmoji(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.DanmuRenderEmoji, value)

    val danmuHideScroll: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.DanmuHideScroll, false)
    suspend fun setDanmuHideScroll(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.DanmuHideScroll, value)

    val danmuHideBottom: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.DanmuHideBottom, false)
    suspend fun setDanmuHideBottom(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.DanmuHideBottom, value)

    val danmuHideTop: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.DanmuHideTop, false)
    suspend fun setDanmuHideTop(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.DanmuHideTop, value)

    val danmuFontWeight: Flow<Int> = settingsDataStore.getFlow(SettingsDataStore.DanmuFontWeight, 4)
    suspend fun setDanmuFontWeight(value: Int) = settingsDataStore.setValue(SettingsDataStore.DanmuFontWeight, value)

    val danmuStrokeWidth: Flow<Double> = settingsDataStore.getFlow(SettingsDataStore.DanmuStrokeWidth, 2.0)
    suspend fun setDanmuStrokeWidth(value: Double) = settingsDataStore.setValue(SettingsDataStore.DanmuStrokeWidth, value)

    val danmuTopMargin: Flow<Double> = settingsDataStore.getFlow(SettingsDataStore.DanmuTopMargin, 0.0)
    suspend fun setDanmuTopMargin(value: Double) = settingsDataStore.setValue(SettingsDataStore.DanmuTopMargin, value)

    val danmuBottomMargin: Flow<Double> = settingsDataStore.getFlow(SettingsDataStore.DanmuBottomMargin, 0.0)
    suspend fun setDanmuBottomMargin(value: Double) = settingsDataStore.setValue(SettingsDataStore.DanmuBottomMargin, value)

    // Deduplication
    val danmuDedupeEnable: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.DanmuDedupeEnable, false)
    suspend fun setDanmuDedupeEnable(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.DanmuDedupeEnable, value)

    val danmuDedupeWindow: Flow<Int> = settingsDataStore.getFlow(SettingsDataStore.DanmuDedupeWindow, 10)
    suspend fun setDanmuDedupeWindow(value: Int) = settingsDataStore.setValue(SettingsDataStore.DanmuDedupeWindow, value)

    val danmuDedupeStep: Flow<Int> = settingsDataStore.getFlow(SettingsDataStore.DanmuDedupeStep, 2)
    suspend fun setDanmuDedupeStep(value: Int) = settingsDataStore.setValue(SettingsDataStore.DanmuDedupeStep, value)

    val danmuDedupeStrictMode: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.DanmuDedupeStrictMode, false)
    suspend fun setDanmuDedupeStrictMode(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.DanmuDedupeStrictMode, value)

    // Playback
    val scaleMode: Flow<Int> = settingsDataStore.getFlow(SettingsDataStore.ScaleMode, 0)
    suspend fun setScaleMode(value: Int) = settingsDataStore.setValue(SettingsDataStore.ScaleMode, value)

    val hardwareDecode: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.HardwareDecode, true)
    suspend fun setHardwareDecode(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.HardwareDecode, value)

    val playerCompatMode: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.PlayerCompatMode, false)
    suspend fun setPlayerCompatMode(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.PlayerCompatMode, value)

    val playerAutoPause: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.PlayerAutoPause, false)
    suspend fun setPlayerAutoPause(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.PlayerAutoPause, value)

    val allowBackgroundPlayback: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.AllowBackgroundPlayback, false)
    suspend fun setAllowBackgroundPlayback(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.AllowBackgroundPlayback, value)

    val playerForceHttps: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.PlayerForceHttps, false)
    suspend fun setPlayerForceHttps(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.PlayerForceHttps, value)

    // Auto exit
    val autoExitEnable: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.AutoExitEnable, false)
    suspend fun setAutoExitEnable(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.AutoExitEnable, value)

    val autoExitDuration: Flow<Int> = settingsDataStore.getFlow(SettingsDataStore.AutoExitDuration, 60)
    suspend fun setAutoExitDuration(value: Int) = settingsDataStore.setValue(SettingsDataStore.AutoExitDuration, value)

    val roomAutoExitDuration: Flow<Int> = settingsDataStore.getFlow(SettingsDataStore.RoomAutoExitDuration, 0)
    suspend fun setRoomAutoExitDuration(value: Int) = settingsDataStore.setValue(SettingsDataStore.RoomAutoExitDuration, value)

    // Debug / Log
    val debugMode: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.DebugMode, false)
    suspend fun setDebugMode(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.DebugMode, value)

    val logEnable: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.LogEnable, false)
    suspend fun setLogEnable(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.LogEnable, value)

    // Chat / SC
    val superChatSortDesc: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.SuperChatSortDesc, false)
    suspend fun setSuperChatSortDesc(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.SuperChatSortDesc, value)

    val chatTextSize: Flow<Double> = settingsDataStore.getFlow(SettingsDataStore.ChatTextSize, 14.0)
    suspend fun setChatTextSize(value: Double) = settingsDataStore.setValue(SettingsDataStore.ChatTextSize, value)

    val chatBubbleStyle: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.ChatBubbleStyle, false)
    suspend fun setChatBubbleStyle(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.ChatBubbleStyle, value)

    val chatTextGap: Flow<Double> = settingsDataStore.getFlow(SettingsDataStore.ChatTextGap, 4.0)
    suspend fun setChatTextGap(value: Double) = settingsDataStore.setValue(SettingsDataStore.ChatTextGap, value)

    val autoFullScreen: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.AutoFullScreen, false)
    suspend fun setAutoFullScreen(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.AutoFullScreen, value)

    // Account cookies
    val bilibiliCookie: Flow<String> = sensitiveCredentialStore.bilibiliCookie
    suspend fun setBilibiliCookie(value: String) = sensitiveCredentialStore.setBilibiliCookie(value)

    val douyinCookie: Flow<String> = sensitiveCredentialStore.douyinCookie
    suspend fun setDouyinCookie(value: String) = sensitiveCredentialStore.setDouyinCookie(value)

    // Follow settings
    val autoUpdateFollowEnable: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.AutoUpdateFollowEnable, true)
    suspend fun setAutoUpdateFollowEnable(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.AutoUpdateFollowEnable, value)

    val autoUpdateFollowDuration: Flow<Int> = settingsDataStore.getFlow(SettingsDataStore.AutoUpdateFollowDuration, 60)
    suspend fun setAutoUpdateFollowDuration(value: Int) = settingsDataStore.setValue(SettingsDataStore.AutoUpdateFollowDuration, value)

    val updateFollowThreadCount: Flow<Int> = settingsDataStore.getFlow(SettingsDataStore.UpdateFollowThreadCount, 8)
    suspend fun setUpdateFollowThreadCount(value: Int) = settingsDataStore.setValue(SettingsDataStore.UpdateFollowThreadCount, value)

    // Sorting settings
    val siteSort: Flow<String> = settingsDataStore.getFlow(SettingsDataStore.SiteSort, "bilibili,douyu,huya,douyin")
        .map { it.ifBlank { "bilibili,douyu,huya,douyin" } }
    suspend fun setSiteSort(value: String) = settingsDataStore.setValue(SettingsDataStore.SiteSort, value)

    val homeSort: Flow<String> = settingsDataStore.getFlow(SettingsDataStore.HomeSort, "recommend,follow,category,user")
        .map { it.ifBlank { "recommend,follow,category,user" } }
    suspend fun setHomeSort(value: String) = settingsDataStore.setValue(SettingsDataStore.HomeSort, value)

    val liveRoomQuickAccessSort: Flow<String> = settingsDataStore.getFlow(SettingsDataStore.LiveRoomQuickAccessSort, "follow,history,recommendation")
        .map { it.ifBlank { "follow,history,recommendation" } }
    suspend fun setLiveRoomQuickAccessSort(value: String) = settingsDataStore.setValue(SettingsDataStore.LiveRoomQuickAccessSort, value)

    val liveRoomQuickAccessEnabled: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.LiveRoomQuickAccessEnabled, true)
    suspend fun setLiveRoomQuickAccessEnabled(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.LiveRoomQuickAccessEnabled, value)

    // PiP settings
    val autoPipOnExit: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.AutoPipOnExit, false)
    suspend fun setAutoPipOnExit(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.AutoPipOnExit, value)

    val pipHideDanmu: Flow<Boolean> = settingsDataStore.getFlow(SettingsDataStore.PIPHideDanmu, false)
    suspend fun setPipHideDanmu(value: Boolean) = settingsDataStore.setValue(SettingsDataStore.PIPHideDanmu, value)

    // User Remarks - stored as JSON Map: {"siteId::userName": "remark"}
    val userRemarksJson: Flow<String> = settingsDataStore.getFlow(SettingsDataStore.UserRemarks, "{}")
    suspend fun setUserRemarksJson(value: String) = settingsDataStore.setValue(SettingsDataStore.UserRemarks, value)

    // Sync server settings
    val syncServerUrl: Flow<String> = settingsDataStore.getFlow(SettingsDataStore.SyncServerUrl, "")
    suspend fun setSyncServerUrl(value: String) = settingsDataStore.setValue(SettingsDataStore.SyncServerUrl, value)

    val syncProxyUrl: Flow<String> = settingsDataStore.getFlow(SettingsDataStore.SyncProxyUrl, "")
    suspend fun setSyncProxyUrl(value: String) = settingsDataStore.setValue(SettingsDataStore.SyncProxyUrl, value)
}
