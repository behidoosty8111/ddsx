package com.example.mfb.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.mfb.core.model.ThemeMode
import com.example.mfb.presentation.editor.EditorSettingsDialog
import com.example.mfb.presentation.viewmodel.MfbViewModel
import com.example.mfb.ui.components.MfbTopBar
import com.example.mfb.ui.components.TechBadge

@Composable
fun SettingsScreen(
    viewModel: MfbViewModel,
    onNavigateToAbout: () -> Unit,
    onNavigateToUpdateLog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentTheme by viewModel.themeMode.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val editorSettings by viewModel.editorSettings.collectAsStateWithLifecycle()
    val buildServerUrl by viewModel.buildServerUrl.collectAsStateWithLifecycle()

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showEditorSettingsDialog by remember { mutableStateOf(false) }
    var showBuildServerDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember(buildServerUrl) { mutableStateOf(buildServerUrl) }

    Scaffold(
        topBar = {
            MfbTopBar(
                title = stringResource(R.string.nav_settings),
                showLogo = true
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .testTag("settings_screen"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Theme Selection Section
                item {
                    SettingsCard(title = stringResource(R.string.theme_selection)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeChoiceChip(
                                title = stringResource(R.string.theme_dark),
                                icon = Icons.Default.DarkMode,
                                isSelected = currentTheme == ThemeMode.DARK,
                                testTag = "theme_dark_button",
                                onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeChoiceChip(
                                title = stringResource(R.string.theme_system),
                                icon = Icons.Default.SettingsBrightness,
                                isSelected = currentTheme == ThemeMode.SYSTEM,
                                testTag = "theme_system_button",
                                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeChoiceChip(
                                title = stringResource(R.string.theme_light),
                                icon = Icons.Default.LightMode,
                                isSelected = currentTheme == ThemeMode.LIGHT,
                                testTag = "theme_light_button",
                                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 2. Language & Preferences
                item {
                    SettingsCard(title = "Preferences") {
                        SettingsRowItem(
                            icon = Icons.Default.Code,
                            title = "Code Editor Settings",
                            subtitle = "${editorSettings.fontSizeSp}sp • ${editorSettings.editorTheme.label} • Auto-save ${if (editorSettings.autoSaveEnabled) "ON" else "OFF"}",
                            testTag = "settings_editor_item",
                            onClick = { showEditorSettingsDialog = true }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        SettingsRowItem(
                            icon = Icons.Default.Language,
                            title = stringResource(R.string.settings_language),
                            subtitle = if (currentLanguage == "en") "English (Active)" else currentLanguage.uppercase(),
                            testTag = "settings_language_item",
                            onClick = { showLanguageDialog = true }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        SettingsRowItem(
                            icon = Icons.Default.Code,
                            title = "Gradle Build Server URL",
                            subtitle = buildServerUrl.ifBlank { "Not configured (Workspace Export mode)" },
                            testTag = "settings_build_server_item",
                            onClick = {
                                serverUrlInput = buildServerUrl
                                showBuildServerDialog = true
                            }
                        )
                    }
                }

                // 3. Application Information & Privacy
                item {
                    SettingsCard(title = "System & Security") {
                        SettingsRowItem(
                            icon = Icons.Default.History,
                            title = "Update Log",
                            subtitle = "What's New in MFB • v${BuildConfig.VERSION_NAME}",
                            testTag = "settings_update_log_item",
                            onClick = onNavigateToUpdateLog
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        SettingsRowItem(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.settings_about),
                            subtitle = "Architecture, specifications and 9-phase roadmap",
                            testTag = "settings_about_item",
                            onClick = onNavigateToAbout
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        SettingsRowItem(
                            icon = Icons.Default.Lock,
                            title = stringResource(R.string.settings_privacy),
                            subtitle = "Local-first sandbox privacy guarantee",
                            testTag = "settings_privacy_item",
                            onClick = { showPrivacyDialog = true }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        SettingsRowItem(
                            icon = Icons.AutoMirrored.Filled.HelpOutline,
                            title = stringResource(R.string.settings_help),
                            subtitle = "HTML-to-APK architectural guidelines",
                            testTag = "settings_help_item",
                            onClick = { showHelpDialog = true }
                        )
                    }
                }

                // 4. Version info card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "MFB Platform",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Phase 05 • Native Kotlin & Compose",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditorSettingsDialog) {
        EditorSettingsDialog(
            settings = editorSettings,
            onUpdateSettings = { transform -> viewModel.updateEditorSettings(transform) },
            onDismiss = { showEditorSettingsDialog = false }
        )
    }

    // Privacy Dialog
    if (showBuildServerDialog) {
        AlertDialog(
            onDismissRequest = { showBuildServerDialog = false },
            title = { Text("Remote Gradle Build Server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter your remote Gradle build server base URL (e.g. https://build.example.com) to compile real APKs from your phone.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setBuildServerUrl(serverUrlInput)
                        showBuildServerDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBuildServerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Text(
                    text = "Privacy Guarantee",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "MFB operates on a 100% on-device, local-first paradigm. Your imported HTML archives, scripts, source code, and build artifacts remain strictly inside your device's isolated application storage sandbox. No source files or keystores are uploaded to third-party tracking services.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showPrivacyDialog = false },
                    modifier = Modifier.testTag("privacy_dialog_confirm")
                ) {
                    Text("Understood", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = "Documentation & Guidelines",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "MFB is designed to convert any standards-compliant HTML5, CSS3, and JavaScript web application into a signed, installable Android APK or Google Play AAB.\n\nPhase 01: Setup & Dashboard (Current)\nPhase 02: ZIP/HTML Import Engine (Next)\nPhase 03–08: Editor, Live Preview, SDK & Keystore Export.\n\nEnsure your index.html contains proper viewport meta tags (<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showHelpDialog = false },
                    modifier = Modifier.testTag("help_dialog_confirm")
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Language Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = "Language Preference",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "MFB Phase 01 currently features full verified English UI. Additional locales will be fully translated as upcoming phases land.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "English (United States)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showLanguageDialog = false },
                    modifier = Modifier.testTag("language_dialog_confirm")
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ThemeChoiceChip(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
