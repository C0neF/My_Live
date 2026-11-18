package com.mylive.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.R
import com.mylive.app.ui.component.settings.SettingsHairlineDivider
import com.mylive.app.ui.component.settings.SettingsInsetCard
import com.mylive.app.ui.component.settings.SettingsMenu
import com.mylive.app.ui.component.settings.SettingsSectionHeader
import com.mylive.app.ui.component.settings.SettingsSelectionDialog
import com.mylive.app.ui.layout.AdaptivePageFrame
import com.mylive.app.ui.layout.LocalAppLayout
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.theme.Icons

private val themeModeValues = listOf(0, 1, 2)
private const val LEGACY_STYLE_COLOR = 0xFF3498DB.toInt()
private const val APPLE_BLUE_STYLE_COLOR = 0xFF007AFF.toInt()

// Apple HIG System Accents
private val presetColors = listOf(
    APPLE_BLUE_STYLE_COLOR, // Apple Blue
    0xFF5856D6.toInt(),     // Apple Indigo
    0xFFAF52DE.toInt(),     // Apple Purple
    0xFFFF2D55.toInt(),     // Apple Pink
    0xFFFF3B30.toInt(),     // Apple Red
    0xFFFF9500.toInt(),     // Apple Orange
    0xFF34C759.toInt(),     // Apple Green
    0xFF00C7BE.toInt(),     // Apple Mint
    0xFF32ADE6.toInt(),     // Apple Cyan
    0xFF8E8E93.toInt(),     // Apple Graphite
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    navigator: Navigator,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val styleColor by viewModel.styleColor.collectAsStateWithLifecycle()
    val layoutMode by viewModel.appLayoutMode.collectAsStateWithLifecycle()
    val tabletOrientation by viewModel.tabletOrientationMode.collectAsStateWithLifecycle()

    val normalizedStyleColor = if (styleColor == LEGACY_STYLE_COLOR) {
        APPLE_BLUE_STYLE_COLOR
    } else {
        styleColor
    }

    val themeModeOptions = stringArrayResource(R.array.theme_mode_options)
    val themeModeDefault = stringResource(R.string.theme_mode_default)
    val selectedCd = stringResource(R.string.theme_color_selected)

    var showThemeDialog by remember { mutableStateOf(false) }
    val handleBack = navigator.rememberRouteActions().onBack

    Scaffold(
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_appearance)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        AdaptivePageFrame(Modifier.padding(padding), LocalAppLayout.current.settingsMaxWidthDp) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (embedded) Color.Transparent else MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column {
                    SettingsSectionHeader("外观与布局")
                    SettingsInsetCard(embedded = embedded) {
                        SettingsMenu(
                            title = stringResource(R.string.theme_mode_title),
                            subtitle = stringResource(R.string.theme_mode_subtitle),
                            value = themeModeOptions.getOrElse(themeModeValues.indexOf(themeMode)) { themeModeDefault },
                            onClick = { showThemeDialog = true }
                        )
                        SettingsHairlineDivider()
                        LayoutModeSettings(
                            mode = layoutMode,
                            orientation = tabletOrientation,
                            tabletActive = LocalAppLayout.current.tablet,
                            onModeChange = viewModel::setAppLayoutMode,
                            onOrientationChange = viewModel::setTabletOrientationMode,
                            showBottomDivider = false
                        )
                    }
                }

                Column {
                    SettingsSectionHeader(stringResource(R.string.theme_color_picker_title))
                    SettingsInsetCard(embedded = embedded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.theme_color_picker_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                presetColors.chunked(5).forEach { rowColors ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        rowColors.forEach { color ->
                                            val isSelected = normalizedStyleColor == color
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(color))
                                                    .then(
                                                        if (isSelected) {
                                                            Modifier.border(
                                                                width = 2.5.dp,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                                                shape = CircleShape
                                                            )
                                                        } else {
                                                            Modifier.border(
                                                                width = 0.5.dp,
                                                                color = Color.Black.copy(alpha = 0.12f),
                                                                shape = CircleShape
                                                            )
                                                        }
                                                    )
                                                    .clickable { viewModel.setStyleColor(color) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = selectedCd,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showThemeDialog) {
        SettingsSelectionDialog(
            title = stringResource(R.string.theme_mode_title),
            options = themeModeOptions,
            values = themeModeValues,
            selectedValue = themeMode,
            onSelect = { viewModel.setThemeMode(it) },
            onDismiss = { showThemeDialog = false }
        )
    }
}
