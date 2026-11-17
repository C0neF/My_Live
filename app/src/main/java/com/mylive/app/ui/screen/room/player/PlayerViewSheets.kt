package com.mylive.app.ui.screen.room.player

import android.app.Activity
import android.graphics.Rect
import android.os.SystemClock
import android.view.LayoutInflater
import com.mylive.app.R
import com.mylive.app.core.model.DanmakuSettingsPolicy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.mylive.app.BuildConfig
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import com.mylive.app.ui.layout.LocalAppLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView as Media3PlayerView
import com.mylive.app.core.model.LivePlayQuality
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackDiagnosticsBottomSheet(
    snapshot: PlaybackDiagnosticSnapshot,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "播放诊断",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            snapshot.entries.forEach { entry ->
                ListItem(
                    headlineContent = { Text(entry.label) },
                    supportingContent = { Text(entry.value) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LineBottomSheet(
    lines: List<String>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "选择线路",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider()

            lines.forEachIndexed { index, url ->
                val isSelected = index == currentIndex
                val isFlv = url.contains(".flv", ignoreCase = true)
                val typeLabel = if (isFlv) "FLV" else "HLS"

                Surface(
                    onClick = { onSelect(index) },
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "线路 ${index + 1}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                }
                            }
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DanmakuSettingsBottomSheet(
    danmuSize: Double,
    onDanmuSizeChange: (Double) -> Unit,
    danmuSpeed: Double,
    onDanmuSpeedChange: (Double) -> Unit,
    danmuArea: Double,
    onDanmuAreaChange: (Double) -> Unit,
    danmuOpacity: Double,
    onDanmuOpacityChange: (Double) -> Unit,
    chatBubbleStyle: Boolean,
    onChatBubbleStyleChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "弹幕快捷设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Size Selector
                Text("弹幕字号", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                val sizeValue = DanmakuSettingsPolicy.sizeMultiplier(danmuSize)
                val sizeValues = DanmakuSettingsPolicy.choicesWithCurrent(DanmakuSettingsPolicy.sizeValues, sizeValue)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sizeValues.forEach { size ->
                        FilterChip(
                            selected = DanmakuSettingsPolicy.sameValue(sizeValue, size),
                            onClick = { onDanmuSizeChange(size) },
                            label = { Text(DanmakuSettingsPolicy.multiplierText(size)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Speed Selector
                Text("弹幕速度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                val speedValue = DanmakuSettingsPolicy.speedMultiplier(danmuSpeed)
                val speedValues = DanmakuSettingsPolicy.choicesWithCurrent(DanmakuSettingsPolicy.speedValues, speedValue)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    speedValues.forEach { speed ->
                        FilterChip(
                            selected = DanmakuSettingsPolicy.sameValue(speedValue, speed),
                            onClick = { onDanmuSpeedChange(speed) },
                            label = { Text(DanmakuSettingsPolicy.multiplierText(speed)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Area Selector
                Text("显示区域", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                val areaValue = DanmakuSettingsPolicy.area(danmuArea)
                val areaValues = DanmakuSettingsPolicy.choicesWithCurrent(DanmakuSettingsPolicy.areaValues, areaValue)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    areaValues.forEach { area ->
                        FilterChip(
                            selected = DanmakuSettingsPolicy.sameValue(areaValue, area),
                            onClick = { onDanmuAreaChange(area) },
                            label = { Text(DanmakuSettingsPolicy.percentText(area)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Opacity Selector
                Text("不透明度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                val opacityValue = DanmakuSettingsPolicy.opacity(danmuOpacity)
                val opacityValues = DanmakuSettingsPolicy.choicesWithCurrent(DanmakuSettingsPolicy.opacityValues, opacityValue)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    opacityValues.forEach { opacity ->
                        FilterChip(
                            selected = DanmakuSettingsPolicy.sameValue(opacityValue, opacity),
                            onClick = { onDanmuOpacityChange(opacity) },
                            label = { Text(DanmakuSettingsPolicy.percentText(opacity)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Bubble style toggle
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("气泡样式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "聊天消息使用气泡背景",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = chatBubbleStyle,
                        onCheckedChange = onChatBubbleStyleChange
                    )
                }
            }
        }
    }
}

@Composable
internal fun IndicatorOverlay(
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.7f),
        modifier = modifier
            .padding(16.dp)
            .width(72.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

