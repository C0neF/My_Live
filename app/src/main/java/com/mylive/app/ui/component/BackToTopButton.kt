package com.mylive.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mylive.app.ui.theme.Icons

internal data class BackToTopButtonMetrics(
    val sizeDp: Int = 44,
    val iconSizeDp: Int = 22,
    val endPaddingDp: Int = 20,
    val bottomPaddingDp: Int = 112
)

internal fun backToTopButtonMetrics(): BackToTopButtonMetrics {
    return BackToTopButtonMetrics()
}

@Composable
fun BackToTopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = backToTopButtonMetrics()
    Surface(
        modifier = modifier.size(metrics.sizeDp.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.primary,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "回到顶部",
                modifier = Modifier.size(metrics.iconSizeDp.dp)
            )
        }
    }
}
