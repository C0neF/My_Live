package com.mylive.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A platform-neutral selector shared by the Android category page and desktop shell. */
@Composable
fun PlatformTab(
    name: String,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), label = "platformTabContent"
    )
    val indicatorColor by animateColorAsState(
        if (isSelected) accentColor else Color.Transparent, label = "platformTabIndicator"
    )
    Column(
        modifier = modifier.semantics { selected = isSelected }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            name, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ), fontSize = 15.sp
        )
        Spacer(Modifier.height(5.dp))
        Box(Modifier.height(2.dp).width(20.dp).background(indicatorColor, RoundedCornerShape(1.dp)))
    }
}
