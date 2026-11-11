package com.example.mfb.presentation.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mfb.domain.model.EditorSettings
import com.example.mfb.domain.model.EditorThemePreset
import kotlin.math.roundToInt

@Composable
fun EditorSettingsDialog(
    settings: EditorSettings,
    onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Code Editor Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Font Size
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Font Size",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${settings.fontSizeSp} sp",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = settings.fontSizeSp.toFloat(),
                        onValueChange = { value ->
                            val sp = value.roundToInt().coerceIn(11, 24)
                            onUpdateSettings { it.copy(fontSizeSp = sp) }
                        },
                        valueRange = 11f..24f,
                        steps = 12,
                        modifier = Modifier.testTag("editor_font_size_slider")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                // Editor Theme
                Text(
                    text = "Editor Theme",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EditorThemePreset.entries.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onUpdateSettings { it.copy(editorTheme = preset) } }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.editorTheme == preset,
                                onClick = { onUpdateSettings { it.copy(editorTheme = preset) } }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                // Toggles
                EditorToggleRow(
                    title = "Line Numbers",
                    subtitle = "Show line number gutter",
                    checked = settings.showLineNumbers,
                    testTag = "editor_setting_line_numbers",
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(showLineNumbers = checked) } }
                )

                EditorToggleRow(
                    title = "Word Wrap",
                    subtitle = "Wrap long lines instead of horizontal scroll",
                    checked = settings.wordWrap,
                    testTag = "editor_setting_word_wrap",
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(wordWrap = checked) } }
                )

                EditorToggleRow(
                    title = "Syntax Highlighting",
                    subtitle = "Colorize HTML, CSS, JS, JSON, XML & Markdown",
                    checked = settings.syntaxHighlightingEnabled,
                    testTag = "editor_setting_syntax",
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(syntaxHighlightingEnabled = checked) } }
                )

                EditorToggleRow(
                    title = "Auto-Save",
                    subtitle = "Save automatically shortly after typing stops",
                    checked = settings.autoSaveEnabled,
                    testTag = "editor_setting_autosave",
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(autoSaveEnabled = checked) } }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                // Indentation
                Text(
                    text = "Indentation",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !settings.useTabs && settings.indentWidth == 2,
                        onClick = { onUpdateSettings { it.copy(useTabs = false, indentWidth = 2) } },
                        label = { Text("2 Spaces") }
                    )
                    FilterChip(
                        selected = !settings.useTabs && settings.indentWidth == 4,
                        onClick = { onUpdateSettings { it.copy(useTabs = false, indentWidth = 4) } },
                        label = { Text("4 Spaces") }
                    )
                    FilterChip(
                        selected = settings.useTabs,
                        onClick = { onUpdateSettings { it.copy(useTabs = true) } },
                        label = { Text("Tab (\\t)") }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("editor_settings_done_button")
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun EditorToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
