package com.mylive.app.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

fun copyPlainText(context: Context, label: String, text: String) {
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
}
