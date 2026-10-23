package com.mylive.app.ui.screen.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val roomStartTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

/** Keep the platform timestamp intact: follow refresh and duration calculations also consume it. */
internal fun formatLiveRoomStartTime(showTime: String?, zoneId: ZoneId = ZoneId.systemDefault()): String? {
    val timestamp = showTime?.trim()?.toLongOrNull()?.takeIf { it > 0L } ?: return null
    return try {
        val instant = if (timestamp >= 1_000_000_000_000L) {
            Instant.ofEpochMilli(timestamp)
        } else {
            Instant.ofEpochSecond(timestamp)
        }
        val localTime = instant.atZone(zoneId)
        if (localTime.year !in 1970..9999) null else roomStartTimeFormatter.format(localTime)
    } catch (_: DateTimeException) {
        null
    }
}

@Composable
internal fun LiveRoomStartTime(showTime: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("live_room_start_time")
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "开播时间",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatLiveRoomStartTime(showTime) ?: "暂未提供",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
