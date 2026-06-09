package com.mylive.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Index : Route

    @Serializable
    data object Search : Route

    @Serializable
    data class CategoryDetail(
        val siteId: String,
        val categoryId: String,
        val categoryName: String = ""
    ) : Route

    @Serializable
    data class LiveRoomDetail(
        val roomId: String,
        val siteId: String = "",
        val initialIsFollowing: Boolean? = null
    ) : Route

    @Serializable
    data object History : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object SettingsDanmu : Route

    @Serializable
    data object SettingsDanmuShield : Route

    @Serializable
    data object SettingsPlay : Route

    @Serializable
    data object SettingsAppStyle : Route

    @Serializable
    data object SettingsAutoExit : Route

    @Serializable
    data object SettingsOther : Route

    @Serializable
    data object SettingsAccount : Route

    @Serializable
    data object SettingsAccountLoginWebview : Route

    @Serializable
    data object SettingsAccountLoginQr : Route

    @Serializable
    data object SettingsAccountDouyinLoginWebview : Route

    @Serializable
    data object SettingsFollow : Route

    @Serializable
    data object SettingsIndexed : Route

    @Serializable
    data object SettingsPlaybackPage : Route

    @Serializable
    data object Sync : Route

    @Serializable
    data object ProfileBackup : Route

    @Serializable
    data object RemoteSyncWebDav : Route

    @Serializable
    data object Tools : Route

    @Serializable
    data object LocalSync : Route

    @Serializable
    data class RemoteSyncRoom(val roomId: String = "") : Route

    @Serializable
    data object DebugLog : Route

    @Serializable
    data object SyncScan : Route

    @Serializable
    data class SyncDevice(
        val address: String = "",
        val port: String = "23234",
        val name: String = "Unknown Device",
        val token: String = ""
    ) : Route
}
