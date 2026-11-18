package com.mylive.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun AppearanceSettings(
    p: DesktopAppearancePreferences,
    enabled: Boolean,
    onChange: ((DesktopAppearancePreferences) -> DesktopAppearancePreferences) -> Unit
) {
    SettingsSectionCard("主题与强调色") {
        Text("外观模式", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("跟随系统", "浅色", "深色").forEachIndexed { index, label ->
                DesktopChoiceChip(
                    selected = p.theme == index,
                    enabled = enabled,
                    onClick = { onChange { it.copy(theme = index) } },
                    label = { Text(label) }
                )
            }
        }
        SettingsFormDivider()
        Text("强调色", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DesktopAccentOptions.forEach { option ->
                val color = option.value
                val name = option.name
                val swatchColor = desktopAccentColor(color, MaterialTheme.colorScheme.surface.luminance() < .5f)
                DesktopChoiceChip(
                    selected = p.accent == color,
                    enabled = enabled,
                    onClick = { onChange { it.copy(accent = color) } },
                    leading = {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(swatchColor, CircleShape)
                                .border(DesktopHairlineWidth, Color.Black.copy(alpha = .16f), CircleShape)
                        )
                    },
                    label = { Text(name) }
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SettingsSectionCard("文字大小") {
        Text(
            "调整菜单、列表等界面文字的大小。",
            style = MaterialTheme.typography.bodySmall,
            color = UiMuted
        )
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(90 to "紧凑 90%", 100 to "标准 100%", 110 to "舒适 110%", 120 to "大号 120%").forEach { (percent, label) ->
                DesktopChoiceChip(
                    selected = p.textScalePercent == percent,
                    enabled = enabled,
                    modifier = Modifier.testTag("appearance-text-scale-" + percent),
                    onClick = { onChange { it.copy(textScalePercent = percent) } },
                    label = { Text(label) }
                )
            }
        }
        Surface(
            color = UiSurface,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("文字预览 · Live", style = MaterialTheme.typography.titleMedium)
                Text("发现喜欢的直播，继续上次的观看。", style = MaterialTheme.typography.bodyLarge)
                Text("主播名称 / 直播分类 / 12.8 万人气", style = MaterialTheme.typography.bodySmall, color = UiMuted)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SettingsSectionCard("直播平台顺序") {
        Column {
            p.platformOrder.forEachIndexed { index, id ->
                key(id) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .background(UiSurface, RoundedCornerShape(6.dp))
                                .border(DesktopHairlineWidth, UiHairline, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = UiMuted)
                        }
                        Text(
                            when (id) {
                                "bilibili" -> "哔哩哔哩"
                                "douyu" -> "斗鱼"
                                "huya" -> "虎牙"
                                else -> "抖音"
                            },
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        DesktopOutlinedButton(
                            modifier = Modifier.height(28.dp),
                            enabled = enabled && index > 0,
                            contentPadding = PaddingValues(horizontal = 11.dp, vertical = 2.dp),
                            onClick = { onChange { it.copy(platformOrder = moveDesktopPlatform(it.platformOrder, index, -1)) } }
                        ) { Text("上移", style = MaterialTheme.typography.labelMedium) }
                        DesktopOutlinedButton(
                            modifier = Modifier.height(28.dp),
                            enabled = enabled && index < 3,
                            contentPadding = PaddingValues(horizontal = 11.dp, vertical = 2.dp),
                            onClick = { onChange { it.copy(platformOrder = moveDesktopPlatform(it.platformOrder, index, 1)) } }
                        ) { Text("下移", style = MaterialTheme.typography.labelMedium) }
                    }
                    if (index < p.platformOrder.lastIndex) {
                        SettingsFormDivider()
                    }
                }
            }
        }
    }
}

internal fun moveDesktopPlatform(order: List<String>, index: Int, delta: Int): List<String> {
    val target = index + delta
    if (index !in order.indices || target !in order.indices) return order
    return order.toMutableList().apply { add(target, removeAt(index)) }
}
