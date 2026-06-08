package com.mylive.app.ui.component.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun SettingsNumber(
    title: String,
    subtitle: String? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange = 0..100,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var tempValue by remember(value) { mutableIntStateOf(value) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = title) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (tempValue > range.first) tempValue--
                        },
                        enabled = tempValue > range.first
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "减少"
                        )
                    }
                    Text(
                        text = tempValue.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    IconButton(
                        onClick = {
                            if (tempValue < range.last) tempValue++
                        },
                        enabled = tempValue < range.last
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "增加"
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(tempValue)
                        showDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

