package com.example.mfb.presentation.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mfb.data.editor.CodeEditingHelpers
import com.example.mfb.data.editor.CodeEditorVisualTransformation
import com.example.mfb.domain.model.EditorSettings
import com.example.mfb.domain.model.OpenEditorTab
import com.example.mfb.domain.model.SearchState
import com.example.mfb.ui.components.MfbEmptyState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun CodeEditorScreen(
    viewModel: CodeEditorViewModel,
    onNavigateBack: () -> Unit,
    onOpenLivePreview: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.editorSettings.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showExitUnsavedDialog by remember { mutableStateOf(false) }
    var showPreviewUnsavedDialog by remember { mutableStateOf(false) }
    var isSavingForPreview by remember { mutableStateOf(false) }
    var previewSaveError by remember { mutableStateOf<String?>(null) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var showReplaceAllConfirmDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val handleBackNavigation: () -> Unit = {
        if (uiState.hasAnyUnsavedChanges) {
            showExitUnsavedDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(onBack = handleBackNavigation)

    val activeTab = uiState.activeTab
    val cursorOffset = activeTab?.textFieldValue?.selection?.start ?: 0
    val (currentLine, currentColumn) = remember(activeTab?.textFieldValue?.text, cursorOffset) {
        if (activeTab == null) 1 to 1
        else CodeEditingHelpers.computeLineAndColumn(activeTab.textFieldValue.text, cursorOffset)
    }

    Scaffold(
        topBar = {
            EditorTopBar(
                projectName = uiState.project?.name ?: "MFB Project",
                activeTab = activeTab,
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                hasAnyUnsaved = uiState.hasAnyUnsavedChanges,
                onNavigateBack = handleBackNavigation,
                onSaveCurrent = { viewModel.saveCurrentFile() },
                onSaveAll = { viewModel.saveAllModifiedFiles() },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onOpenSearch = { viewModel.openSearch(showReplace = true) },
                onGoToLine = { showGoToLineDialog = true },
                onInsertIndent = { viewModel.insertIndentAtCursor() },
                onSelectAll = { viewModel.selectAllInActiveTab() },
                onCopy = {
                    if (activeTab != null) {
                        val sel = activeTab.textFieldValue.selection
                        val start = sel.min.coerceIn(0, activeTab.textFieldValue.text.length)
                        val end = sel.max.coerceIn(0, activeTab.textFieldValue.text.length)
                        if (start < end) {
                            val selected = activeTab.textFieldValue.text.substring(start, end)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Code", selected))
                        }
                    }
                },
                onCut = {
                    if (activeTab != null) {
                        val sel = activeTab.textFieldValue.selection
                        val start = sel.min.coerceIn(0, activeTab.textFieldValue.text.length)
                        val end = sel.max.coerceIn(0, activeTab.textFieldValue.text.length)
                        if (start < end) {
                            val selected = activeTab.textFieldValue.text.substring(start, end)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Code", selected))
                            val newText = activeTab.textFieldValue.text.removeRange(start, end)
                            viewModel.onTextChanged(
                                activeTab.textFieldValue.copy(text = newText, selection = TextRange(start))
                            )
                        }
                    }
                },
                onPaste = {
                    if (activeTab != null) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (!clipText.isNullOrEmpty()) {
                            val normalizedClip = clipText.replace("\r\n", "\n")
                            val sel = activeTab.textFieldValue.selection
                            val start = sel.min.coerceIn(0, activeTab.textFieldValue.text.length)
                            val end = sel.max.coerceIn(0, activeTab.textFieldValue.text.length)
                            val newText = activeTab.textFieldValue.text.replaceRange(start, end, normalizedClip)
                            viewModel.onTextChanged(
                                activeTab.textFieldValue.copy(
                                    text = newText,
                                    selection = TextRange(start + normalizedClip.length)
                                )
                            )
                        }
                    }
                },
                onOpenSettings = { showSettingsDialog = true },
                onRequestPreview = {
                    if (uiState.hasAnyUnsavedChanges) {
                        showPreviewUnsavedDialog = true
                    } else {
                        val htmlTarget = activeTab?.takeIf {
                            it.extension.equals("html", ignoreCase = true) ||
                                it.extension.equals("htm", ignoreCase = true)
                        }?.relativePath
                        onOpenLivePreview(htmlTarget)
                    }
                }
            )
        },
        bottomBar = {
            EditorBottomInfoBar(
                activeTab = activeTab,
                line = currentLine,
                column = currentColumn,
                onClickGoToLine = { showGoToLineDialog = true }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            EditorTabStrip(
                tabs = uiState.tabs,
                activeTabPath = uiState.activeTabPath,
                onSelectTab = { viewModel.selectTab(it) },
                onCloseTab = { viewModel.requestCloseTab(it) }
            )

            EditorSearchReplaceBar(
                searchState = uiState.searchState,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onReplaceQueryChange = { viewModel.updateReplaceQuery(it) },
                onToggleReplace = { viewModel.toggleReplaceExpanded() },
                onToggleMatchCase = { viewModel.toggleMatchCase() },
                onFindPrevious = { viewModel.findPrevious() },
                onFindNext = { viewModel.findNext() },
                onReplaceCurrent = { viewModel.replaceCurrentMatch() },
                onReplaceAll = {
                    if (uiState.searchState.matches.size > 1) {
                        showReplaceAllConfirmDialog = true
                    } else {
                        viewModel.replaceAllMatches()
                    }
                },
                onClose = { viewModel.closeSearch() }
            )

            if (activeTab?.warningMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = activeTab.warningMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.fatalError != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        MfbEmptyState(
                            icon = Icons.Default.WarningAmber,
                            title = "Unable to Open Editor",
                            supportingText = uiState.fatalError ?: "Unknown error"
                        )
                    }
                }
                activeTab == null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        MfbEmptyState(
                            icon = Icons.Default.Code,
                            title = "No File Open",
                            supportingText = "Select a supported source file (.html, .css, .js, .json, .xml, .md, .txt) from Project Files to start editing."
                        )
                    }
                }
                else -> {
                    // Force LTR for source code editing regardless of app language (e.g. Persian/RTL)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        CodeEditorWorkspace(
                            tab = activeTab,
                            settings = settings,
                            searchState = uiState.searchState,
                            scrollToOffsetSignal = uiState.scrollToOffsetSignal,
                            onValueChange = { viewModel.onTextChanged(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }

    // Close Dirty Tab Confirmation
    uiState.pendingCloseTabPath?.let { targetPath ->
        val tabName = uiState.tabs.firstOrNull { it.relativePath == targetPath }?.fileName ?: targetPath
        AlertDialog(
            onDismissRequest = { viewModel.dismissCloseTabDialog() },
            title = { Text("Unsaved Changes", fontWeight = FontWeight.Bold) },
            text = {
                Text("'$tabName' has unsaved changes. Would you like to save before closing?")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveAndCloseTab(targetPath) },
                    modifier = Modifier.testTag("dialog_save_and_close_button")
                ) {
                    Text("Save & Close")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.dismissCloseTabDialog() }) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = { viewModel.confirmDiscardAndCloseTab(targetPath) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("dialog_discard_tab_button")
                    ) {
                        Text("Discard")
                    }
                }
            }
        )
    }

    // Exit Editor with Unsaved Changes Confirmation
    if (showExitUnsavedDialog) {
        val dirtyCount = uiState.tabs.count { it.isDirty }
        AlertDialog(
            onDismissRequest = { showExitUnsavedDialog = false },
            title = { Text("Save Changes Before Leaving?", fontWeight = FontWeight.Bold) },
            text = {
                Text("You have $dirtyCount file(s) with unsaved changes. Save all before returning to the workspace?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitUnsavedDialog = false
                        viewModel.saveAllModifiedFiles(onAllSaved = onNavigateBack)
                    },
                    modifier = Modifier.testTag("dialog_save_all_exit_button")
                ) {
                    Text("Save All & Exit")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showExitUnsavedDialog = false }) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            showExitUnsavedDialog = false
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("dialog_discard_exit_button")
                    ) {
                        Text("Discard")
                    }
                }
            }
        )
    }

    // Unsaved Changes Before Live Preview Dialog
    if (showPreviewUnsavedDialog) {
        val htmlTarget = activeTab?.takeIf {
            it.extension.equals("html", ignoreCase = true) ||
                it.extension.equals("htm", ignoreCase = true)
        }?.relativePath
        AlertDialog(
            onDismissRequest = { if (!isSavingForPreview) showPreviewUnsavedDialog = false },
            title = { Text("You have unsaved changes.", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Save your changes before opening Live Preview, or preview the last saved version on disk.")
                    if (isSavingForPreview) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Saving changes...", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                isSavingForPreview = true
                                viewModel.saveAllModifiedFiles(
                                    onAllSaved = {
                                        isSavingForPreview = false
                                        showPreviewUnsavedDialog = false
                                        onOpenLivePreview(htmlTarget)
                                    },
                                    onSaveFailed = { err ->
                                        isSavingForPreview = false
                                        showPreviewUnsavedDialog = false
                                        previewSaveError = err
                                    }
                                )
                            },
                            enabled = !isSavingForPreview,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_save_and_preview_button")
                        ) {
                            Text("Save and Preview")
                        }
                        TextButton(
                            onClick = {
                                viewModel.cancelPendingAutoSave()
                                showPreviewUnsavedDialog = false
                                onOpenLivePreview(htmlTarget)
                            },
                            enabled = !isSavingForPreview,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_preview_last_saved_button")
                        ) {
                            Text("Preview Last Saved Version")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showPreviewUnsavedDialog = false },
                    enabled = !isSavingForPreview,
                    modifier = Modifier.testTag("dialog_cancel_preview_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Save Failure Before Preview Dialog (preserves unsaved editor content & allows retry)
    previewSaveError?.let { errorMsg ->
        val htmlTarget = activeTab?.takeIf {
            it.extension.equals("html", ignoreCase = true) ||
                it.extension.equals("htm", ignoreCase = true)
        }?.relativePath
        AlertDialog(
            onDismissRequest = { previewSaveError = null },
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Save Failed", fontWeight = FontWeight.Bold) },
            text = {
                Text("$errorMsg\n\nYour unsaved changes are preserved in the editor. Preview was cancelled.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        previewSaveError = null
                        isSavingForPreview = true
                        showPreviewUnsavedDialog = true
                        viewModel.saveAllModifiedFiles(
                            onAllSaved = {
                                isSavingForPreview = false
                                showPreviewUnsavedDialog = false
                                onOpenLivePreview(htmlTarget)
                            },
                            onSaveFailed = { err ->
                                isSavingForPreview = false
                                showPreviewUnsavedDialog = false
                                previewSaveError = err
                            }
                        )
                    },
                    modifier = Modifier.testTag("dialog_retry_save_preview_button")
                ) {
                    Text("Retry Save & Preview")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewSaveError = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // External File Conflict Resolution Dialog
    uiState.conflictState?.let { conflict ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissConflictDialog() },
            title = { Text("File Conflict Detected", fontWeight = FontWeight.Bold) },
            text = {
                Text("${conflict.reason}\n\nChoose whether to reload the version from disk (discards editor edits) or overwrite disk with your editor content.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.resolveConflictOverwrite(conflict.relativePath) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Overwrite Disk")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.dismissConflictDialog() }) {
                        Text("Cancel")
                    }
                    TextButton(onClick = { viewModel.resolveConflictReloadFromDisk(conflict.relativePath) }) {
                        Text("Reload Disk")
                    }
                }
            }
        )
    }

    // Go To Line Dialog
    if (showGoToLineDialog && activeTab != null) {
        val totalLines = remember(activeTab.textFieldValue.text) {
            CodeEditingHelpers.getLineCount(activeTab.textFieldValue.text)
        }
        var lineInput by remember { mutableStateOf(currentLine.toString()) }
        AlertDialog(
            onDismissRequest = { showGoToLineDialog = false },
            title = { Text("Go to Line", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a line number (1 – $totalLines):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = lineInput,
                        onValueChange = { input -> lineInput = input.filter { it.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("go_to_line_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = lineInput.toIntOrNull()?.coerceIn(1, totalLines) ?: 1
                        viewModel.goToLine(target)
                        showGoToLineDialog = false
                    },
                    enabled = lineInput.isNotBlank(),
                    modifier = Modifier.testTag("confirm_go_to_line_button")
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoToLineDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Replace All Confirmation
    if (showReplaceAllConfirmDialog) {
        val count = uiState.searchState.matches.size
        AlertDialog(
            onDismissRequest = { showReplaceAllConfirmDialog = false },
            title = { Text("Replace All Matches?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Replace all $count occurrences of '${uiState.searchState.query}' with '${uiState.searchState.replaceQuery}'?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReplaceAllConfirmDialog = false
                        viewModel.replaceAllMatches()
                    },
                    modifier = Modifier.testTag("confirm_replace_all_button")
                ) {
                    Text("Replace All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceAllConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // In-Editor Settings Dialog
    if (showSettingsDialog) {
        EditorSettingsDialog(
            settings = settings,
            onUpdateSettings = { transform -> viewModel.updateEditorSettings(transform) },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun CodeEditorWorkspace(
    tab: OpenEditorTab,
    settings: EditorSettings,
    searchState: SearchState,
    scrollToOffsetSignal: Int,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = resolveEditorPalette(settings.editorTheme)
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    var textLayoutResult by remember(tab.relativePath) { mutableStateOf<TextLayoutResult?>(null) }

    val matchingBrackets = remember(tab.textFieldValue.text, tab.textFieldValue.selection.start) {
        if (tab.isLargeFile) null
        else CodeEditingHelpers.findMatchingBracket(tab.textFieldValue.text, tab.textFieldValue.selection.start)
    }

    val visualTransformation = remember(
        tab.language,
        tab.isLargeFile,
        settings.syntaxHighlightingEnabled,
        palette.syntaxPalette,
        searchState.isVisible,
        searchState.matches,
        searchState.currentMatchIndex,
        matchingBrackets
    ) {
        CodeEditorVisualTransformation(
            language = tab.language,
            syntaxEnabled = settings.syntaxHighlightingEnabled && !tab.isLargeFile,
            palette = palette.syntaxPalette,
            searchMatches = if (searchState.isVisible) searchState.matches else emptyList(),
            activeMatchIndex = if (searchState.isVisible) searchState.currentMatchIndex else -1,
            matchingBracketPair = matchingBrackets
        )
    }

    val codeTextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = settings.fontSizeSp.sp,
        lineHeight = (settings.fontSizeSp * 1.45f).sp,
        color = palette.syntaxPalette.text
    )

    val gutterTextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = (settings.fontSizeSp - 1).coerceAtLeast(10).sp,
        color = palette.gutterText
    )

    // Scroll to cursor when Go To Line or Find Next/Prev triggers
    LaunchedEffect(scrollToOffsetSignal) {
        if (scrollToOffsetSignal > 0) {
            val layout = textLayoutResult ?: return@LaunchedEffect
            val safeOffset = tab.textFieldValue.selection.start.coerceIn(0, tab.textFieldValue.text.length)
            val visualLine = layout.getLineForOffset(safeOffset)
            val lineTop = layout.getLineTop(visualLine).roundToInt()
            coroutineScope.launch {
                verticalScroll.animateScrollTo((lineTop - 80).coerceAtLeast(0))
            }
        }
    }

    val lineCount = remember(tab.textFieldValue.text) {
        CodeEditingHelpers.getLineCount(tab.textFieldValue.text)
    }
    val gutterDigits = lineCount.toString().length.coerceAtLeast(3)
    val gutterWidth = with(density) { ((gutterDigits * settings.fontSizeSp * 0.65f) + 20f).dp }

    Row(
        modifier = modifier
            .background(palette.editorBackground)
            .verticalScroll(verticalScroll)
    ) {
        if (settings.showLineNumbers) {
            Box(
                modifier = Modifier
                    .width(gutterWidth)
                    .fillMaxHeight()
                    .background(palette.gutterBackground)
                    .border(
                        width = 0.5.dp,
                        color = palette.borderColor
                    )
                    .padding(vertical = 12.dp)
                    .drawBehind {
                        val layout = textLayoutResult
                        val sourceText = tab.textFieldValue.text
                        if (layout != null) {
                            var logicalLine = 1
                            for (v in 0 until layout.lineCount) {
                                val lineStart = layout.getLineStart(v)
                                val isLogicalStart = v == 0 || (lineStart > 0 && lineStart <= sourceText.length && sourceText[lineStart - 1] == '\n')
                                if (isLogicalStart) {
                                    val topY = layout.getLineTop(v)
                                    val measured = textMeasurer.measure(
                                        text = AnnotatedString(logicalLine.toString()),
                                        style = gutterTextStyle
                                    )
                                    val x = (size.width - measured.size.width - 8.dp.toPx()).coerceAtLeast(2f)
                                    drawText(
                                        textLayoutResult = measured,
                                        topLeft = Offset(x, topY)
                                    )
                                    logicalLine++
                                }
                            }
                        }
                    }
            ) {
                val layoutHeightDp = with(density) {
                    (textLayoutResult?.size?.height ?: 0).toDp()
                }
                Spacer(modifier = Modifier.height(layoutHeightDp.coerceAtLeast(240.dp)))
            }
        }

        val editorBoxModifier = Modifier
            .weight(1f)
            .then(
                if (!settings.wordWrap) Modifier.horizontalScroll(horizontalScroll)
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .drawBehind {
                val layout = textLayoutResult ?: return@drawBehind
                val safeOffset = tab.textFieldValue.selection.start.coerceIn(0, tab.textFieldValue.text.length)
                val activeVisualLine = layout.getLineForOffset(safeOffset)
                val top = layout.getLineTop(activeVisualLine)
                val bottom = layout.getLineBottom(activeVisualLine)
                drawRect(
                    color = palette.activeLineBackground,
                    topLeft = Offset(-12.dp.toPx(), top),
                    size = Size(size.width + 24.dp.toPx(), bottom - top)
                )
            }

        Box(modifier = editorBoxModifier) {
            BasicTextField(
                value = tab.textFieldValue,
                onValueChange = onValueChange,
                textStyle = codeTextStyle,
                cursorBrush = SolidColor(palette.cursorColor),
                visualTransformation = visualTransformation,
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("code_editor_text_field")
            )
        }
    }
}
