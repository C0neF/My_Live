package com.mylive.app.ui.screen.mine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.R
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.theme.Icons
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout

@Composable
fun MineScreen(
    navigator: Navigator,
    contentBottomPadding: Dp = 96.dp
) {
    AdaptivePageFrame(maxWidthDp = LocalAppLayout.current.settingsMaxWidthDp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.nav_mine),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                val cardModifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MineCard(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.mine_history),
                        modifier = cardModifier.testTag("mine_history"),
                        onClick = { navigator.navigate(Route.History) }
                    )
                    MineCard(
                        icon = Icons.Default.Person,
                        title = stringResource(R.string.mine_account),
                        modifier = cardModifier.testTag("mine_account"),
                        onClick = { navigator.navigate(Route.SettingsAccount) }
                    )
                    MineCard(
                        icon = Icons.Default.Sync,
                        title = stringResource(R.string.mine_sync),
                        modifier = cardModifier.testTag("mine_sync"),
                        onClick = { navigator.navigate(Route.Sync) }
                    )
                    MineCard(
                        icon = Icons.Default.Link,
                        title = stringResource(R.string.parse_title),
                        modifier = cardModifier.testTag("mine_tools"),
                        onClick = { navigator.navigate(Route.Tools) }
                    )
                    MineCard(
                        icon = Icons.Default.Settings,
                        title = stringResource(R.string.mine_settings),
                        modifier = cardModifier.testTag("mine_settings"),
                        onClick = { navigator.navigate(Route.Settings) }
                    )
                }
            }

            Text(
                text = stringResource(R.string.mine_version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = contentBottomPadding)
            )
        }
    }
}

@Composable
private fun MineCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
