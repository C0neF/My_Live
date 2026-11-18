package com.mylive.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.ui.theme.Icons
import com.mylive.app.ui.theme.LiveSuccessGreen

internal fun followPlatformBadgeTextColor(
    siteId: String,
    themeBackground: Color,
    defaultColor: Color
): Color {
    return if (siteId == "douyin" && themeBackground.luminance() < 0.5f) {
        Color.White
    } else {
        defaultColor
    }
}

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
    avatarSize: Dp = 46.dp,
    cardHorizontalPadding: Dp = 16.dp,
    cardVerticalPadding: Dp = 4.dp,
    contentPadding: Dp = 12.dp,
    horizontalGap: Dp = 13.dp,
    liveBadgeSize: Dp = 11.dp,
    playIconSize: Dp = 20.dp,
    cornerRadius: Dp = 14.dp,
    refreshHint: String? = null
) {
    val isLive = liveStatus == 1
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = cardHorizontalPadding, vertical = cardVerticalPadding)
            .clip(RoundedCornerShape(cornerRadius))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = containerColor,
        shape = RoundedCornerShape(cornerRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(avatarSize)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    NetImage(
                        url = faceUrl,
                        contentDescription = userName,
                        size = avatarSize,
                        isCircle = true
                    )
                }
                if (isLive) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(liveBadgeSize),
                        shape = CircleShape,
                        color = LiveSuccessGreen,
                        border = BorderStroke(2.dp, containerColor)
                    ) {}
                }
            }

            Spacer(modifier = Modifier.width(horizontalGap))

            Column(modifier = Modifier.weight(1f)) {
                val isDarkSurface = containerColor.luminance() < 0.5f
                val favoriteBadgeBg = if (isDarkSurface) {
                    Color(0xFFFF9F0A).copy(alpha = 0.18f)
                } else {
                    Color(0xFFFF9500).copy(alpha = 0.13f)
                }
                val favoriteBadgeFg = if (isDarkSurface) {
                    Color(0xFFFFB340)
                } else {
                    Color(0xFFD97706)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSpecialFollow) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = favoriteBadgeBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.StarFilled,
                                    contentDescription = "已收藏",
                                    tint = favoriteBadgeFg,
                                    modifier = Modifier.size(10.5.dp)
                                )
                                Text(
                                    text = "收藏",
                                    color = favoriteBadgeFg,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                val siteLabel = when (siteId) {
                    "bilibili" -> "哔哩哔哩"
                    "douyu" -> "斗鱼"
                    "huya" -> "虎牙"
                    "douyin" -> "抖音"
                    else -> siteId
                }
                val statusLabel = when (liveStatus) {
                    1 -> {
                        val duration = formatLiveDuration(showTime)
                        if (duration.isNotEmpty()) "开播 $duration" else "直播中"
                    }
                    2 -> "未开播"
                    else -> "检测中"
                }
                Text(
                    text = if (siteLabel.isNotEmpty()) "$siteLabel · $statusLabel" else statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.5.sp,
                    color = if (isLive) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                refreshHint?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = if (isLive) Icons.Default.PlayArrow else Icons.Default.ChevronRight,
                contentDescription = if (isLive) "Go to Live" else null,
                tint = if (isLive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
                modifier = Modifier.size(if (isLive) playIconSize - 2.dp else 18.dp)
            )
        }
    }
}

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
