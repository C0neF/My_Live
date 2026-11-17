package com.mylive.desktop

import com.mylive.app.core.common.MAX_IMPORTED_JSON_BYTES
import com.mylive.app.core.sync.LanSyncClient
import com.mylive.app.core.sync.LanSyncImportType
import okhttp3.OkHttpClient

class DesktopLanClient(client: OkHttpClient) {
    private val sync = LanSyncClient(client)

    suspend fun send(address: String, port: Int, token: String, profile: String) {
        require(token.length in 16..256) { "请填写对方的完整配对码" }
        require(profile.toByteArray().size <= MAX_IMPORTED_JSON_BYTES)
        sync.connect(address.trim(), port, token)
            .import(LanSyncImportType.PROFILE, profile, overlay = false)
    }
}
