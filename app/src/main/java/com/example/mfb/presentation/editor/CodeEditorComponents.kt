package com.example.mfb.presentation.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mfb.data.editor.EditorSyntaxPalette
import com.example.mfb.domain.model.EditorThemePreset
import com.example.mfb.domain.model.OpenEditorTab
import com.example.mfb.domain.model.SaveState
import com.example.mfb.domain.model.SearchState

data class EditorSurfacePalette(
    val isDark: Boolean,
    val editorBackground: Color,
    val gutterBackground: Color,
    val gutterText: Color,
    val activeLineBackground: Color,
    val cursorColor: Color,
    val borderColor: Color,
    val syntaxPalette: EditorSyntaxPalette
)

@Composable
fun resolveEditorPalette(preset: EditorThemePreset): EditorSurfacePalette {
    val appIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val useDark = when (preset) {
        EditorThemePreset.MATCH_APP -> appIsDark
        EditorThemePreset.DARK_IDE, EditorThemePreset.HIGH_CONTRAST_DARK -> true
        EditorThemePreset.LIGHT_IDE -> false
    }
    return when {
        preset == EditorThemePreset.HIGH_CONTRAST_DARK -> EditorSurfacePalette(
            isDark = true,
            editorBackground = Color(0xFF000000),
            gutterBackground = Color(0xFF0A0C10),
            gutterText = Color(0xFF9BA3B4),
            activeLineBackground = Color(0x266C63FF),
            cursorColor = Color(0xFF20C997),
            borderColor = Color(0xFF2B3245),
            syntaxPalette = EditorSyntaxPalette.dark().copy(text = Color(0xFFFFFFFF))
        )
        useDark -> EditorSurfacePalette(
            isDark = true,
            editorBackground = Color(0xFF0D1117),
            gutterBackground = Color(0xFF131822),
            gutterText = Color(0xFF6E7681),
            activeLineBackground = Color(0x1E6C63FF),
            cursorColor = Color(0xFF6C63FF),
            borderColor = Color(0xFF242B3D),
            syntaxPalette = EditorSyntaxPalette.dark()
        )
        else -> EditorSurfacePalette(
            isDark = false,
            editorBackground = Color(0xFFFAFBFC),
            gutterBackground = Color(0xFFF0F2F6),
            gutterText = Color(0xFF6E7781),
            activeLineBackground = Color(0x146C63FF),
            cursorColor = Color(0xFF6C63FF),
            borderColor = Color(0xFFD0D7DE),
            syntaxPalette = EditorSyntaxPalette.light()
        )
    }
}

@Composable
fun EditorTopBar(
    projectName: String,
    activeTab: OpenEditorTab?,
    canUndo: Boolean,
    canRedo: Boolean,
    hasAnyUnsaved: Boolean,
    onNavigateBack: () -> Unit,
    onSaveCurrent: () -> Unit,
    onSaveAll: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenSearch: () -> Unit,
    onGoToLine: () -> Unit,
    onInsertIndent: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPreview: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("editor_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = activeTab?.fileName ?: "Code Editor",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("editor_active_filename")
                    )
                    Text(
                        text = projectName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.testTag("editor_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo"
                    )
                }

                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.testTag("editor_redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo"
                    )
                }

                IconButton(
                    onClick = onRequestPreview,
                    modifier = Modifier.testTag("editor_preview_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Live Preview",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                // Save Button with visual state indicator
                val saveState = activeTab?.saveState ?: SaveState.Saved
                IconButton(
                    onClick = onSaveCurrent,
                    enabled = activeTab != null && saveState !is SaveState.Saving,
                    modifier = Modifier.testTag("editor_save_button")
                ) {
                    when (saveState) {
                        is SaveState.Saving -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        is SaveState.SaveFailed -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Save failed",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        is SaveState.Unsaved -> {
                            Box {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save unsaved changes",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                        is SaveState.Saved -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Saved",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("editor_more_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More editor options"
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Find & Replace") },
                            leadingIcon = { Icon(Icons.Default.FindReplace, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSearch()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Go to Line") },
                            leadingIcon = { Icon(Icons.Default.VerticalAlignCenter, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onGoToLine()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Insert Indent") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardTab, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onInsertIndent()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save All Modified") },
                            leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
                            enabled = hasAnyUnsaved,
                            onClick = {
                                menuExpanded = false
                                onSaveAll()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Select All") },
                            leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onSelectAll()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Cut") },
                            leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onCut()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Paste") },
                            leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onPaste()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Editor Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            }
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        }
    }
}

@Composable
fun EditorTabStrip(
    tabs: List<OpenEditorTab>,
    activeTabPath: String?,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit
) {
    if (tabs.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("editor_tab_strip"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isActive = tab.relativePath == activeTabPath
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        width = 1.dp,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectTab(tab.relativePath) }
                    .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                    .testTag("editor_tab_${tab.fileName}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tab.isDirty) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = tab.fileName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { onCloseTab(tab.relativePath) },
                    modifier = Modifier
                        .size(22.dp)
                        .testTag("close_tab_${tab.fileName}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close ${tab.fileName}",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EditorSearchReplaceBar(
    searchState: SearchState,
    onQueryChange: (String) -> Unit,
    onReplaceQueryChange: (String) -> Unit,
    onToggleReplace: () -> Unit,
    onToggleMatchCase: () -> Unit,
    onFindPrevious: () -> Unit,
    onFindNext: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    AnimatedVisibility(visible = searchState.isVisible) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onToggleReplace,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (searchState.isReplaceExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle replace"
                        )
                    }
                    OutlinedTextField(
                        value = searchState.query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Find...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_search_input")
                    )
                    Text(
                        text = if (searchState.matches.isEmpty()) {
                            "0"
                        } else {
                            "${searchState.currentMatchIndex + 1}/${searchState.matches.size}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onToggleMatchCase,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (searchState.matchCase) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                    ) {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (searchState.matchCase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onFindPrevious,
                        enabled = searchState.matches.isNotEmpty(),
                        modifier = Modifier.size(34.dp).testTag("editor_find_prev_button")
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                    }
                    IconButton(
                        onClick = onFindNext,
                        enabled = searchState.matches.isNotEmpty(),
                        modifier = Modifier.size(34.dp).testTag("editor_find_next_button")
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(34.dp).testTag("editor_search_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close search")
                    }
                }

                if (searchState.isReplaceExpanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchState.replaceQuery,
                            onValueChange = onReplaceQueryChange,
                            placeholder = { Text("Replace with...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_replace_input")
                        )
                        TextButton(
                            onClick = onReplaceCurrent,
                            enabled = searchState.matches.isNotEmpty(),
                            modifier = Modifier.testTag("editor_replace_one_button")
                        ) {
                            Text("Replace")
                        }
                        TextButton(
                            onClick = onReplaceAll,
                            enabled = searchState.matches.isNotEmpty(),
                            modifier = Modifier.testTag("editor_replace_all_button")
                        ) {
                            Text("All")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditorBottomInfoBar(
    activeTab: OpenEditorTab?,
    line: Int,
    column: Int,
    onClickGoToLine: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Ln $line, Col $column",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = activeTab != null, onClick = onClickGoToLine)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag("editor_cursor_position")
                    )
                    if (activeTab != null) {
                        Text(
                            text = activeTab.extension.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "${activeTab.encodingName} • ${activeTab.lineEnding.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (activeTab != null) {
                    val (statusText, statusColor) = when (val s = activeTab.saveState) {
                        is SaveState.Saved -> "Saved" to MaterialTheme.colorScheme.secondary
                        is SaveState.Unsaved -> "Modified" to MaterialTheme.colorScheme.tertiary
                        is SaveState.Saving -> "Saving..." to MaterialTheme.colorScheme.primary
                        is SaveState.SaveFailed -> "Save Error" to MaterialTheme.colorScheme.error
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.testTag("editor_save_status_label")
                    )
                }
            }
        }
    }
}
