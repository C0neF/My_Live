package com.mylive.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FollowUserItem(
    userName: String,
    faceUrl: String,
    liveStatus: Int, // 0=未知, 1=直播中, 2=未开播
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    siteId: String = "",
    showTime: String? = null,
    isSpecialFollow: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    avatarSize: Dp = 48.dp,
    cardHorizontalPadding: Dp = 16.dp,
    cardVerticalPadding: Dp = 6.dp,
    contentPadding: Dp = 12.dp,
    horizontalGap: Dp = 16.dp,
    liveBadgeSize: Dp = 12.dp,
    playIconSize: Dp = 24.dp,
    cornerRadius: Dp = 16.dp
) {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
        .copy(alpha = 0.25f)
        .compositeOver(MaterialTheme.colorScheme.background)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = cardHorizontalPadding, vertical = cardVerticalPadding)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(cornerRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(avatarSize)) {
                NetImage(
                    url = faceUrl,
                    contentDescription = userName,
                    size = avatarSize,
                    isCircle = true
                )
                if (liveStatus == 1) {
                    // Small live red dot badge at bottom-right corner
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(liveBadgeSize),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface)
                    ) {}
                }
            }
            
            Spacer(modifier = Modifier.width(horizontalGap))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSpecialFollow) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "置顶",
                            tint = Color(0xFFFF9F00),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (siteId.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val siteColor = when (siteId) {
                            "bilibili" -> Color(0xFFFB7299)
                            "douyu" -> Color(0xFFFF5D23)
                            "huya" -> Color(0xFFFF9F00)
                            "douyin" -> Color(0xFF000000)
                            else -> MaterialTheme.colorScheme.secondary
                        }
                        val siteName = when (siteId) {
                            "bilibili" -> "B站"
                            "douyu" -> "斗鱼"
                            "huya" -> "虎牙"
                            "douyin" -> "抖音"
                            else -> siteId
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = siteColor.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, siteColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = siteName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = siteColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (liveStatus) {
                        1 -> {
                            val duration = formatLiveDuration(showTime)
                            if (duration.isNotEmpty()) "开播了$duration" else "直播中"
                        }
                        2 -> "未开播"
                        else -> "未知"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (liveStatus == 1) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = if (liveStatus == 1) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            
            if (liveStatus == 1) {
                // Play icon for active streams
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Go to Live",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(playIconSize)
                )
            }
        }
    }
}

/**
 * 格式化开播时长。[showTime] 为平台报告的开播时间戳（秒）。
 * 返回如 "2小时30分钟"、"15分钟"、"不足1分钟"，无法解析时返回空字符串。
 */
fun formatLiveDuration(showTime: String?): String {
    if (showTime.isNullOrEmpty() || showTime == "0") return ""
    return try {
        val startSeconds = showTime.toLong()
        val nowSeconds = System.currentTimeMillis() / 1000
        val durationSeconds = nowSeconds - startSeconds
        if (durationSeconds < 0) return ""

        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60

        when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
            hours > 0 -> "${hours}小时"
            minutes > 0 -> "${minutes}分钟"
            else -> "不足1分钟"
        }
    } catch (_: Exception) {
        ""
    }
}
