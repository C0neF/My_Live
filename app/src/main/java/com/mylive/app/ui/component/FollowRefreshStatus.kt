package com.mylive.app.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.service.FollowRefreshEntry
import com.mylive.app.service.FollowRefreshProgress
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun rememberFollowRefreshEntry(
    entries: StateFlow<Map<String, FollowRefreshEntry>>,
    id: String
): FollowRefreshEntry? {
    val flow = remember(entries, id) { entries.map { it[id] }.distinctUntilChanged() }
    val entry by flow.collectAsStateWithLifecycle(initialValue = null)
    return entry
}

internal fun followRefreshHint(entry: FollowRefreshEntry?): String {
    val lastSuccess = entry?.lastSuccessAtMillis?.let {
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
    }
    return when {
        entry?.refreshing == true -> if (lastSuccess == null) "正在刷新" else "刷新中 · 上次 $lastSuccess"
        entry?.failed == true -> if (lastSuccess == null) "刷新失败，显示缓存状态" else "刷新失败 · 上次 $lastSuccess"
        lastSuccess != null -> "更新于 $lastSuccess"
        else -> "尚未刷新，显示缓存状态"
    }
}

@Composable
internal fun FollowRefreshProgressLine(progress: FollowRefreshProgress, modifier: Modifier = Modifier) {
    val message = when {
        progress.error != null -> progress.error
        progress.total == 0 -> return
        progress.cancelled -> "刷新已中断：${progress.completed}/${progress.total}"
        else -> "${if (progress.running) "正在更新" else "已更新"} ${progress.completed}/${progress.total}" +
            if (progress.failed > 0) " · ${progress.failed} 项失败，保留缓存" else ""
    }
    Text(
        text = message,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 4.dp).testTag("follow_refresh_progress"),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
