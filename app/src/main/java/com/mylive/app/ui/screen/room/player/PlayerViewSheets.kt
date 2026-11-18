package com.mylive.app.ui.screen.room.player

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView as Media3PlayerView
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import com.mylive.app.ui.motion.popoverEnter
import com.mylive.app.ui.motion.popoverExit
import kotlinx.coroutines.delay

@Composable
internal fun PlaybackDiagnosticsFloatingDialog(
    snapshot: PlaybackDiagnosticSnapshot,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val motionPolicy = LocalAppMotionPolicy.current
    val maxHeight = (configuration.screenHeightDp.dp * 0.62f).coerceIn(260.dp, 480.dp)
    val maxWidth = (configuration.screenWidthDp.dp - 36.dp).coerceIn(260.dp, 360.dp)
    val entrance = remember { Animatable(if (motionPolicy.reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!motionPolicy.reducedMotion) {
            entrance.animateTo(1f, animationSpec = motionPolicy.contentSpec())
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 32.dp)
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center
        ) {
            PlaybackDiagnosticsFloatingCard(
                snapshot = snapshot,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                onDismiss = onDismiss,
                modifier = Modifier.graphicsLayer {
                    val p = entrance.value
                    alpha = p
                    val s = 0.94f + 0.06f * p
                    scaleX = s
                    scaleY = s
                }
            )
        }
    }
}

@Composable
internal fun PlaybackDiagnosticsFloatingCard(
    snapshot: PlaybackDiagnosticSnapshot,
    maxWidth: Dp,
    maxHeight: Dp,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    val resolution = snapshot.valueFor("源分辨率") ?: "未知"
    val fps = snapshot.valueFor("视频输出帧率（近段）") ?: snapshot.valueFor("源帧率") ?: "未知"
    val bitrate = snapshot.valueFor("声明码率") ?: "未知"
    val dropAndRebuffer = "${snapshot.valueFor("丢帧") ?: "0"} / ${snapshot.valueFor("缓冲次数") ?: "0"}"

    Surface(
        modifier = modifier
            .width(maxWidth)
            .heightIn(max = maxHeight)
            .pointerInput(Unit) { detectTapGestures { /* Consume taps inside card */ } }
            .testTag("playback_diagnostics_popup"),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xED1C1C1E),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 15.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(Color(0xFF34C759), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "播放诊断",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText("播放诊断", snapshot.asText())
                        )
                        copied = true
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (copied) "已复制" else "复制",
                        color = if (copied) Color(0xFF34C759) else Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭播放诊断",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = Color.White.copy(alpha = 0.10f)
            )

            // Smooth vertical scroll container (no ModalBottomSheet gesture conflict!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (snapshot.entries.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.07f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DiagnosticsMetricTile(
                                    label = "分辨率",
                                    value = resolution,
                                    modifier = Modifier.weight(1f)
                                )
                                DiagnosticsMetricTile(
                                    label = "帧率",
                                    value = fps,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DiagnosticsMetricTile(
                                    label = "码率",
                                    value = bitrate,
                                    modifier = Modifier.weight(1f)
                                )
                                DiagnosticsMetricTile(
                                    label = "丢帧 / 缓冲",
                                    value = dropAndRebuffer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        snapshot.entries.forEachIndexed { index, entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.label,
                                    color = Color.White.copy(alpha = 0.58f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.44f)
                                )
                                Text(
                                    text = entry.value,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.End,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.56f)
                                )
                            }
                            if (index < snapshot.entries.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 12.dp),
                                    thickness = 0.5.dp,
                                    color = Color.White.copy(alpha = 0.07f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 10.sp,
            maxLines = 1
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "选择线路",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    lines.forEachIndexed { index, url ->
                        val isSelected = index == currentIndex
                        val isFlv = url.contains(".flv", ignoreCase = true)
                        val typeLabel = if (isFlv) "FLV" else "HLS"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(index) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "线路 ${index + 1}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        if (index < lines.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DanmakuQuickPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val motionPolicy = LocalAppMotionPolicy.current
    val pillColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = motionPolicy.effectsSpec(),
        label = "danmakuQuickPillBg"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motionPolicy.effectsSpec(),
        label = "danmakuQuickPillText"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = pillColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = labelColor,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
        )
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
                fontWeight = FontWeight.SemiBold,
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
                Text("弹幕字号", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
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
                        DanmakuQuickPill(
                            text = DanmakuSettingsPolicy.multiplierText(size),
                            selected = DanmakuSettingsPolicy.sameValue(sizeValue, size),
                            onClick = { onDanmuSizeChange(size) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Speed Selector
                Text("弹幕速度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
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
                        DanmakuQuickPill(
                            text = DanmakuSettingsPolicy.multiplierText(speed),
                            selected = DanmakuSettingsPolicy.sameValue(speedValue, speed),
                            onClick = { onDanmuSpeedChange(speed) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Area Selector
                Text("显示区域", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
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
                        DanmakuQuickPill(
                            text = DanmakuSettingsPolicy.percentText(area),
                            selected = DanmakuSettingsPolicy.sameValue(areaValue, area),
                            onClick = { onDanmuAreaChange(area) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Opacity Selector
                Text("不透明度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
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
                        DanmakuQuickPill(
                            text = DanmakuSettingsPolicy.percentText(opacity),
                            selected = DanmakuSettingsPolicy.sameValue(opacityValue, opacity),
                            onClick = { onDanmuOpacityChange(opacity) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Bubble style toggle
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("气泡样式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(
                            text = "聊天消息使用柔和气泡背景",
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
        shape = RoundedCornerShape(18.dp),
        color = Color(0xC21C1C1E),
        modifier = modifier
            .padding(16.dp)
            .width(72.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
