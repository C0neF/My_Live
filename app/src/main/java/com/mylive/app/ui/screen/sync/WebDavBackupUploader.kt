package com.mylive.app.ui.screen.sync

import com.mylive.app.core.sync.WebDavBackup
import okhttp3.OkHttpClient

internal fun uploadWebDavBackup(
    okHttpClient: OkHttpClient,
    backupUrl: String,
    username: String,
    password: String,
    backupJson: String
) {
    WebDavBackup.upload(okHttpClient, backupUrl, username, password, backupJson)
}
