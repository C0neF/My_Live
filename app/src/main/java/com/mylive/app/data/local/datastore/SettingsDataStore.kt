package com.mylive.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        // === 通用 ===
        val FirstRun = booleanPreferencesKey("FirstRun")
        val ThemeMode = intPreferencesKey("ThemeMode")
        val DebugMode = booleanPreferencesKey("DebugMode")
        val LogEnable = booleanPreferencesKey("LogEnable")
        val kStyleColor = intPreferencesKey("kStyleColor")
        val kIsDynamic = booleanPreferencesKey("kIsDynamic")

        // === 排序 ===
        val SiteSort = stringPreferencesKey("SiteSort")
        val HomeSort = stringPreferencesKey("HomeSort")
        val LiveRoomTabSort = stringPreferencesKey("LiveRoomTabSort")
        val LiveRoomQuickAccessSort = stringPreferencesKey("LiveRoomQuickAccessSort")
        val LiveRoomQuickAccessEnabled = booleanPreferencesKey("LiveRoomQuickAccessEnabled")

        // === 用户备注 ===
        val UserRemarks = stringPreferencesKey("UserRemarks") // JSON Map: {"siteId::userName": "remark"}

        // === 弹幕 ===
        val DanmuEnable = booleanPreferencesKey("DanmuEnable")
        val DanmuSize = doublePreferencesKey("DanmuSize")
        val DanmuSpeed = doublePreferencesKey("DanmuSpeed")
        val DanmuArea = doublePreferencesKey("DanmuArea")
        val DanmuLineCount = intPreferencesKey("DanmuLineCount")
        val DanmuDelay = doublePreferencesKey("DanmuDelay")
        val DanmuDelayBySite = stringPreferencesKey("DanmuDelayBySite")
        val DanmuOpacity = doublePreferencesKey("DanmuOpacity")
        val DanmuStrokeWidth = doublePreferencesKey("DanmuStrokeWidth")
        val DanmuFontWeight = intPreferencesKey("DanmuFontWeight")
        val DanmuHideScroll = booleanPreferencesKey("DanmuHideScroll")
        val DanmuHideBottom = booleanPreferencesKey("DanmuHideBottom")
        val DanmuHideTop = booleanPreferencesKey("DanmuHideTop")
        val DanmuTopMargin = doublePreferencesKey("DanmuTopMargin")
        val DanmuBottomMargin = doublePreferencesKey("DanmuBottomMargin")
        val DanmuRenderEmoji = booleanPreferencesKey("DanmuRenderEmoji")
        val DanmuShieldEnable = booleanPreferencesKey("DanmuShieldEnable")
        val DanmuKeywordShieldEnable = booleanPreferencesKey("DanmuKeywordShieldEnable")
        val DanmuUserShieldEnable = booleanPreferencesKey("DanmuUserShieldEnable")

        // === 去重 ===
        val DanmuDedupeEnable = booleanPreferencesKey("DanmuDedupeEnable")
        val DanmuDedupeWindow = intPreferencesKey("DanmuDedupeWindow")
        val DanmuDedupeStep = intPreferencesKey("DanmuDedupeStep")
        val DanmuDedupeStrictMode = booleanPreferencesKey("DanmuDedupeStrictMode")

        // === 播放 ===
        val ScaleMode = intPreferencesKey("ScaleMode")
        val HardwareDecode = booleanPreferencesKey("HardwareDecode")
        val QualityLevel = intPreferencesKey("QualityLevel")
        val QualityLevelCellular = intPreferencesKey("QualityLevelCellular")
        val PlayerCompatMode = booleanPreferencesKey("PlayerCompatMode")
        val PlayerAutoPause = booleanPreferencesKey("PlayerAutoPause")
        val AllowBackgroundPlayback = booleanPreferencesKey("AllowBackgroundPlayback")
        val PlayerBufferSize = intPreferencesKey("PlayerBufferSize")
        val PlayerForceHttps = booleanPreferencesKey("PlayerForceHttps")
        val PlayerVolume = doublePreferencesKey("PlayerVolume")
        val PlayerShowSuperChat = booleanPreferencesKey("PlayerShowSuperChat")
        val AutoFullScreen = booleanPreferencesKey("AutoFullScreen")
        val AutoPipOnExit = booleanPreferencesKey("AutoPipOnExit")
        val CustomPlayerOutput = booleanPreferencesKey("CustomPlayerOutput")
        val VideoOutputDriver = stringPreferencesKey("VideoOutputDriver")
        val VideoHardwareDecoder = stringPreferencesKey("VideoHardwareDecoder")
        val AudioOutputDriver = stringPreferencesKey("AudioOutputDriver")

        // === 定时退出 ===
        val AutoExitEnable = booleanPreferencesKey("AutoExitEnable")
        val AutoExitDuration = intPreferencesKey("AutoExitDuration")
        val RoomAutoExitDuration = intPreferencesKey("RoomAutoExitDuration")

        // === 聊天 ===
        val ChatTextSize = doublePreferencesKey("ChatTextSize")
        val ChatTextGap = doublePreferencesKey("ChatTextGap")
        val ChatBubbleStyle = booleanPreferencesKey("ChatBubbleStyle")
        val ContributionRankEnable = booleanPreferencesKey("ContributionRankEnable")

        // === PiP ===
        val PIPHideDanmu = booleanPreferencesKey("PIPHideDanmu")
        val PIPHideDanmuDefaultMigrated = booleanPreferencesKey("PIPHideDanmuDefaultMigrated")

        // === SC ===
        val SuperChatSortDesc = booleanPreferencesKey("SuperChatSortDesc")

        // === 关注 ===
        val AutoUpdateFollowEnable = booleanPreferencesKey("AutoUpdateFollowEnable")
        val AutoUpdateFollowDuration = intPreferencesKey("AutoUpdateFollowDuration")
        val UpdateFollowThreadCount = intPreferencesKey("UpdateFollowThreadCount")

        // === 账号 (不导出) ===
        val BilibiliCookie = stringPreferencesKey("BilibiliCookie")
        val DouyinCookie = stringPreferencesKey("DouyinCookie")
        val BilibiliLoginTip = booleanPreferencesKey("BilibiliLoginTip")

        // === WebDAV (不导出) ===
        val WebDAVUri = stringPreferencesKey("WebDAVUri")
        val WebDAVUser = stringPreferencesKey("WebDAVUser")
        val kWebDAVPassword = stringPreferencesKey("kWebDAVPassword")
        val kWebDAVLastUploadTime = stringPreferencesKey("kWebDAVLastUploadTime")
        val kWebDAVLastRecoverTime = stringPreferencesKey("kWebDAVLastRecoverTime")

        // === 同步 ===
        val SyncServerUrl = stringPreferencesKey("SyncServerUrl")
        val SyncProxyUrl = stringPreferencesKey("SyncProxyUrl")

        // === 临时 (不导出) ===
        val LastLiveRoom = stringPreferencesKey("LastLiveRoom")
        val LastLiveRoomResumePending = booleanPreferencesKey("LastLiveRoomResumePending")

        // === 字幕 (当前功能隐藏) ===
        val LiveSubtitleEnable = booleanPreferencesKey("LiveSubtitleEnable")
        val LiveSubtitleModelPath = stringPreferencesKey("LiveSubtitleModelPath")
        val LiveSubtitleLanguage = stringPreferencesKey("LiveSubtitleLanguage")
        val LiveSubtitleFontSize = doublePreferencesKey("LiveSubtitleFontSize")
        val LiveSubtitlePosition = intPreferencesKey("LiveSubtitlePosition")
        val LiveSubtitleOffsetX = doublePreferencesKey("LiveSubtitleOffsetX")
        val LiveSubtitleOffsetY = doublePreferencesKey("LiveSubtitleOffsetY")
        val LiveSubtitleColor = intPreferencesKey("LiveSubtitleColor")
        val LiveSubtitleFontWeight = intPreferencesKey("LiveSubtitleFontWeight")
        val LiveSubtitleBackgroundEnable = booleanPreferencesKey("LiveSubtitleBackgroundEnable")
        val LiveSubtitlePositionLocked = booleanPreferencesKey("LiveSubtitlePositionLocked")
        val LiveSubtitleStartupGuard = booleanPreferencesKey("LiveSubtitleStartupGuard")
    }

    // === 通用读取/写入方法 ===

    fun <T> getFlow(key: Preferences.Key<T>, defaultValue: T): Flow<T> {
        return context.dataStore.data.map { it[key] ?: defaultValue }
    }

    suspend fun <T> setValue(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun <T> removeValue(key: Preferences.Key<T>) {
        context.dataStore.edit { it.remove(key) }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    // === 批量写入（用于 Profile 导入）===
    suspend fun batchEdit(block: (Preferences) -> Unit) {
        context.dataStore.edit { prefs ->
            // 通过 Preferences 的 edit builder 无法直接传递，使用 edit lambda
            block(prefs)
        }
    }
}
