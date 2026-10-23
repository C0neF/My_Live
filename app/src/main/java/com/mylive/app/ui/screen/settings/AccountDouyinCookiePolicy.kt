package com.mylive.app.ui.screen.settings

import com.mylive.app.core.site.douyin.DouyinCookieInspection
import com.mylive.app.core.site.douyin.DouyinCookieKind

internal fun isDouyinLoginState(inspection: DouyinCookieInspection): Boolean =
    inspection.kind == DouyinCookieKind.LoginStateShape

internal fun douyinCookieStatusLabel(kind: DouyinCookieKind): String = when (kind) {
    DouyinCookieKind.NotConfigured -> "未配置"
    DouyinCookieKind.TtwidOnly -> "仅 ttwid"
    DouyinCookieKind.LoginStateShape -> "检测到登录态"
    DouyinCookieKind.Unrecognized -> "结构未知"
}

internal fun douyinCookieStatusDescription(
    inspection: DouyinCookieInspection,
    nowMillis: Long = System.currentTimeMillis()
): String = when (inspection.kind) {
    DouyinCookieKind.NotConfigured -> "未配置 Cookie；播放会使用内置 ttwid"
    DouyinCookieKind.TtwidOnly -> "播放通常可用，搜索与关注刷新可能受限"
    DouyinCookieKind.Unrecognized -> "未识别典型登录字段，可重新粘贴完整 Request Headers"
    DouyinCookieKind.LoginStateShape -> {
        val expiry = inspection.estimatedExpiryEpochMillis
        val expiryText = when {
            expiry == null -> "无法从 sid_guard 估算有效期"
            expiry <= nowMillis -> "可解析有效期已过"
            else -> "预计剩余 ${formatCookieRemaining(expiry - nowMillis)}"
        }
        "检测到登录态结构，$expiryText；实际有效性以抖音服务端为准"
    }
}

private fun formatCookieRemaining(remainingMillis: Long): String {
    val totalMinutes = (remainingMillis / 60_000L).coerceAtLeast(0L)
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes / 60L) % 24L
    val minutes = totalMinutes % 60L
    return when {
        days > 0L -> "$days 天 $hours 小时"
        hours > 0L -> "$hours 小时 $minutes 分钟"
        else -> "$minutes 分钟"
    }
}
