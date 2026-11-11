package com.example.mfb.presentation.editor

import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mfb.core.di.AppContainer
import com.example.mfb.data.editor.CodeEditingHelpers
import com.example.mfb.data.editor.EditHistoryManager
import com.example.mfb.data.editor.EditorSessionTracker
import com.example.mfb.data.engine.EditorFileManager
import com.example.mfb.data.engine.ProjectStorageManager
import com.example.mfb.data.engine.SaveFileResult
import com.example.mfb.data.local.datastore.UserPreferencesRepository
import com.example.mfb.domain.model.EditorConflictState
import com.example.mfb.domain.model.EditorSessionSnapshot
import com.example.mfb.domain.model.EditorSettings
import com.example.mfb.domain.model.OpenEditorTab
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.SaveState
import com.example.mfb.domain.model.SearchState
import com.example.mfb.domain.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class CodeEditorUiState(
    val isLoading: Boolean = true,
    val project: Project? = null,
    val tabs: List<OpenEditorTab> = emptyList(),
    val activeTabPath: String? = null,
    val searchState: SearchState = SearchState(),
    val pendingCloseTabPath: String? = null,
    val conflictState: EditorConflictState? = null,
    val fatalError: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val scrollToOffsetSignal: Int = 0
) {
    val activeTab: OpenEditorTab?
        get() = tabs.firstOrNull { it.relativePath == activeTabPath }

    val hasAnyUnsavedChanges: Boolean
        get() = tabs.any { it.isDirty }
}

class CodeEditorViewModel(
    private val projectId: Long,
    private val initialFilePath: String?,
    private val appContext: Context,
    private val projectRepository: ProjectRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val saveMutex = Mutex()
    private val sessionMutex = Mutex()
    private val historyManager = EditHistoryManager()
    private var autoSaveJob: Job? = null

    private val _uiState = MutableStateFlow(CodeEditorUiState())
    val uiState: StateFlow<CodeEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val editorSettings: StateFlow<EditorSettings> = preferencesRepository.editorSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = EditorSettings()
        )

    init {
        initializeSession()
    }

    private fun initializeSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, fatalError = null) }
            val project = withContext(Dispatchers.IO) {
                projectRepository.getProjectById(projectId)
            }
            if (project == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        fatalError = "Project could not be found."
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(project = project) }

            val previousSession = withContext(Dispatchers.IO) {
                preferencesRepository.getEditorSession(projectId)
            }

            val pathsToOpen = linkedSetOf<String>()
            previousSession?.openTabPaths?.forEach { pathsToOpen.add(EditorFileManager.normalizeRelativePath(it)) }
            if (!initialFilePath.isNullOrBlank()) {
                pathsToOpen.add(EditorFileManager.normalizeRelativePath(initialFilePath))
            }

            for (path in pathsToOpen) {
                openFileInternal(project, path, makeActive = false, emitFailureOnMissingSession = path == initialFilePath)
            }

            val targetActive = when {
                !initialFilePath.isNullOrBlank() -> {
                    val norm = EditorFileManager.normalizeRelativePath(initialFilePath)
                    if (_uiState.value.tabs.any { it.relativePath == norm }) norm else _uiState.value.tabs.firstOrNull()?.relativePath
                }
                previousSession?.activeTabPath != null -> {
                    val norm = EditorFileManager.normalizeRelativePath(previousSession.activeTabPath)
                    if (_uiState.value.tabs.any { it.relativePath == norm }) norm else _uiState.value.tabs.firstOrNull()?.relativePath
                }
                else -> _uiState.value.tabs.firstOrNull()?.relativePath
            }

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    activeTabPath = targetActive,
                    canUndo = targetActive != null && historyManager.canUndo(targetActive),
                    canRedo = targetActive != null && historyManager.canRedo(targetActive)
                )
            }
            syncSessionTracking()
        }
    }

    fun openFile(relativePath: String) {
        val project = _uiState.value.project ?: return
        val normalized = EditorFileManager.normalizeRelativePath(relativePath)
        val existing = _uiState.value.tabs.firstOrNull { it.relativePath == normalized }
        if (existing != null) {
            selectTab(normalized)
            return
        }
        viewModelScope.launch {
            openFileInternal(project, normalized, makeActive = true, emitFailureOnMissingSession = true)
            syncSessionTracking()
        }
    }

    private suspend fun openFileInternal(
        project: Project,
        normalizedPath: String,
        makeActive: Boolean,
        emitFailureOnMissingSession: Boolean
    ) {
        val projectDir = File(project.internalProjectPath)
        val result = withContext(Dispatchers.IO) {
            EditorFileManager.readFileForEditor(projectDir, normalizedPath)
        }
        result.onSuccess { loaded ->
            val newTab = OpenEditorTab(
                relativePath = loaded.relativePath,
                fileName = loaded.fileName,
                extension = loaded.extension,
                language = loaded.language,
                encodingName = loaded.encodingName,
                hasBom = loaded.hasBom,
                lineEnding = loaded.lineEnding,
                textFieldValue = TextFieldValue(loaded.content),
                savedContent = loaded.content,
                lastKnownModifiedAt = loaded.lastModified,
                lastKnownSizeBytes = loaded.sizeBytes,
                saveState = SaveState.Saved,
                isLargeFile = loaded.isLargeFile,
                warningMessage = loaded.warningMessage
            )
            _uiState.update { state ->
                if (state.tabs.any { it.relativePath == newTab.relativePath }) {
                    if (makeActive) state.copy(activeTabPath = newTab.relativePath) else state
                } else {
                    val nextActive = if (makeActive || state.activeTabPath == null) newTab.relativePath else state.activeTabPath
                    state.copy(
                        tabs = state.tabs + newTab,
                        activeTabPath = nextActive,
                        canUndo = historyManager.canUndo(nextActive),
                        canRedo = historyManager.canRedo(nextActive)
                    )
                }
            }
        }.onFailure { err ->
            if (emitFailureOnMissingSession) {
                _events.tryEmit(err.message ?: "Unable to open '$normalizedPath'.")
            }
        }
    }

    fun selectTab(relativePath: String) {
        val normalized = EditorFileManager.normalizeRelativePath(relativePath)
        if (_uiState.value.tabs.none { it.relativePath == normalized }) return
        _uiState.update { state ->
            val activeTab = state.tabs.firstOrNull { it.relativePath == normalized }
            val updatedSearch = if (state.searchState.isVisible && activeTab != null) {
                recomputeSearch(state.searchState, activeTab.textFieldValue.text)
            } else {
                state.searchState
            }
            state.copy(
                activeTabPath = normalized,
                searchState = updatedSearch,
                canUndo = historyManager.canUndo(normalized),
                canRedo = historyManager.canRedo(normalized)
            )
        }
        syncSessionTracking()
    }

    fun requestCloseTab(relativePath: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.relativePath == relativePath } ?: return
        if (tab.isDirty) {
            _uiState.update { it.copy(pendingCloseTabPath = relativePath) }
        } else {
            closeTabImmediate(relativePath)
        }
    }

    fun dismissCloseTabDialog() {
        _uiState.update { it.copy(pendingCloseTabPath = null) }
    }

    fun confirmDiscardAndCloseTab(relativePath: String) {
        _uiState.update { it.copy(pendingCloseTabPath = null) }
        closeTabImmediate(relativePath)
    }

    fun saveAndCloseTab(relativePath: String) {
        viewModelScope.launch {
            val saved = saveTabInternal(relativePath, forceOverwrite = false)
            if (saved) {
                _uiState.update { it.copy(pendingCloseTabPath = null) }
                closeTabImmediate(relativePath)
            }
        }
    }

    private fun closeTabImmediate(relativePath: String) {
        historyManager.clearTab(relativePath)
        _uiState.update { state ->
            val idx = state.tabs.indexOfFirst { it.relativePath == relativePath }
            if (idx < 0) return@update state
            val remaining = state.tabs.filterNot { it.relativePath == relativePath }
            val nextActive = if (state.activeTabPath == relativePath) {
                remaining.getOrNull(idx.coerceAtMost(remaining.lastIndex))?.relativePath
            } else {
                state.activeTabPath
            }
            val nextTab = remaining.firstOrNull { it.relativePath == nextActive }
            state.copy(
                tabs = remaining,
                activeTabPath = nextActive,
                searchState = if (state.searchState.isVisible && nextTab != null) {
                    recomputeSearch(state.searchState, nextTab.textFieldValue.text)
                } else {
                    state.searchState.copy(matches = emptyList(), currentMatchIndex = -1)
                },
                canUndo = nextActive != null && historyManager.canUndo(nextActive),
                canRedo = nextActive != null && historyManager.canRedo(nextActive)
            )
        }
        syncSessionTracking()
    }

    fun onTextChanged(rawNewValue: TextFieldValue) {
        val activeTab = _uiState.value.activeTab ?: return
        val indentUnit = editorSettings.value.indentUnit
        val processedValue = CodeEditingHelpers.applyAutoIndent(
            oldValue = activeTab.textFieldValue,
            newValue = rawNewValue,
            indentUnit = indentUnit
        )

        val textChanged = processedValue.text != activeTab.textFieldValue.text
        if (textChanged) {
            historyManager.recordEdit(
                relativePath = activeTab.relativePath,
                previousValue = activeTab.textFieldValue,
                newValue = processedValue
            )
        }

        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.relativePath == activeTab.relativePath) {
                    val dirty = processedValue.text != tab.savedContent
                    val nextSaveState = when {
                        tab.saveState is SaveState.Saving -> tab.saveState
                        dirty -> SaveState.Unsaved
                        else -> SaveState.Saved
                    }
                    tab.copy(
                        textFieldValue = processedValue,
                        saveState = nextSaveState
                    )
                } else tab
            }
            val nextSearch = if (textChanged && state.searchState.isVisible) {
                recomputeSearch(state.searchState, processedValue.text)
            } else {
                state.searchState
            }
            state.copy(
                tabs = updatedTabs,
                searchState = nextSearch,
                canUndo = historyManager.canUndo(activeTab.relativePath),
                canRedo = historyManager.canRedo(activeTab.relativePath)
            )
        }

        if (textChanged) {
            syncSessionTracking()
            scheduleAutoSaveIfEnabled(activeTab.relativePath)
        }
    }

    fun insertIndentAtCursor() {
        val activeTab = _uiState.value.activeTab ?: return
        val updatedValue = CodeEditingHelpers.insertIndent(activeTab.textFieldValue, editorSettings.value.indentUnit)
        applyProgrammaticEdit(activeTab.relativePath, updatedValue)
    }

    fun undo() {
        val activeTab = _uiState.value.activeTab ?: return
        val restored = historyManager.undo(activeTab.relativePath, activeTab.textFieldValue) ?: return
        updateTabContentWithoutRecordingHistory(activeTab.relativePath, restored)
    }

    fun redo() {
        val activeTab = _uiState.value.activeTab ?: return
        val restored = historyManager.redo(activeTab.relativePath, activeTab.textFieldValue) ?: return
        updateTabContentWithoutRecordingHistory(activeTab.relativePath, restored)
    }

    private fun applyProgrammaticEdit(relativePath: String, newValue: TextFieldValue) {
        val tab = _uiState.value.tabs.firstOrNull { it.relativePath == relativePath } ?: return
        if (newValue.text != tab.textFieldValue.text) {
            historyManager.recordEdit(relativePath, tab.textFieldValue, newValue, forceDiscrete = true)
        }
        updateTabContentWithoutRecordingHistory(relativePath, newValue)
    }

    private fun updateTabContentWithoutRecordingHistory(relativePath: String, newValue: TextFieldValue) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.relativePath == relativePath) {
                    val dirty = newValue.text != tab.savedContent
                    tab.copy(
                        textFieldValue = newValue,
                        saveState = if (dirty) SaveState.Unsaved else SaveState.Saved
                    )
                } else tab
            }
            val nextSearch = if (state.searchState.isVisible && state.activeTabPath == relativePath) {
                recomputeSearch(state.searchState, newValue.text)
            } else {
                state.searchState
            }
            state.copy(
                tabs = updatedTabs,
                searchState = nextSearch,
                canUndo = historyManager.canUndo(relativePath),
                canRedo = historyManager.canRedo(relativePath)
            )
        }
        syncSessionTracking()
        scheduleAutoSaveIfEnabled(relativePath)
    }

    private fun scheduleAutoSaveIfEnabled(relativePath: String) {
        if (!editorSettings.value.autoSaveEnabled) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1500L)
            val currentTab = _uiState.value.tabs.firstOrNull { it.relativePath == relativePath }
            if (currentTab != null && currentTab.isDirty && editorSettings.value.autoSaveEnabled) {
                saveTabInternal(relativePath, forceOverwrite = false, silentOnSuccess = true)
            }
        }
    }

    fun cancelPendingAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    fun saveCurrentFile(forceOverwrite: Boolean = false) {
        val activePath = _uiState.value.activeTabPath ?: return
        viewModelScope.launch {
            saveTabInternal(activePath, forceOverwrite = forceOverwrite)
        }
    }

    fun saveAllModifiedFiles(
        onAllSaved: (() -> Unit)? = null,
        onSaveFailed: ((String) -> Unit)? = null
    ) {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            val dirtyPaths = _uiState.value.tabs.filter { it.isDirty }.map { it.relativePath }
            if (dirtyPaths.isEmpty()) {
                onAllSaved?.invoke()
                return@launch
            }
            var allSucceeded = true
            var firstFailureMessage: String? = null
            for (path in dirtyPaths) {
                val ok = saveTabInternal(path, forceOverwrite = false, silentOnSuccess = true)
                if (!ok) {
                    allSucceeded = false
                    if (firstFailureMessage == null) {
                        val tabState = _uiState.value.tabs.firstOrNull { it.relativePath == path }?.saveState
                        firstFailureMessage = (tabState as? SaveState.SaveFailed)?.message
                            ?: "Failed to save '$path'."
                    }
                }
            }
            if (allSucceeded) {
                _events.tryEmit("Saved ${dirtyPaths.size} modified file(s).")
                onAllSaved?.invoke()
            } else {
                onSaveFailed?.invoke(firstFailureMessage ?: "Unable to save modified files.")
            }
        }
    }

    private suspend fun saveTabInternal(
        relativePath: String,
        forceOverwrite: Boolean,
        silentOnSuccess: Boolean = false
    ): Boolean {
        return saveMutex.withLock {
            val state = _uiState.value
            val project = state.project ?: return@withLock false
            val tab = state.tabs.firstOrNull { it.relativePath == relativePath } ?: return@withLock false
            val contentToSave = tab.textFieldValue.text

            _uiState.update { current ->
                current.copy(
                    tabs = current.tabs.map {
                        if (it.relativePath == relativePath) it.copy(saveState = SaveState.Saving) else it
                    }
                )
            }

            val projectDir = File(project.internalProjectPath)
            val result = withContext(Dispatchers.IO) {
                EditorFileManager.saveFileAtomically(
                    projectDir = projectDir,
                    relativePath = tab.relativePath,
                    content = contentToSave,
                    hasBom = tab.hasBom,
                    lineEnding = tab.lineEnding,
                    expectedLastModified = tab.lastKnownModifiedAt,
                    forceOverwrite = forceOverwrite
                )
            }

            when (result) {
                is SaveFileResult.Success -> {
                    val updatedProject = withContext(Dispatchers.IO) {
                        val (fileCount, totalBytes) = ProjectStorageManager.calculateStats(projectDir)
                        val updated = project.copy(
                            fileCount = fileCount,
                            totalSizeBytes = totalBytes,
                            updatedAt = System.currentTimeMillis()
                        )
                        projectRepository.updateProject(updated)
                        ProjectStorageManager.writeMetadata(projectDir, updated)
                        updated
                    }
                    _uiState.update { current ->
                        current.copy(
                            project = updatedProject,
                            conflictState = if (current.conflictState?.relativePath == relativePath) null else current.conflictState,
                            tabs = current.tabs.map {
                                if (it.relativePath == relativePath) {
                                    val stillDirty = it.textFieldValue.text != contentToSave
                                    it.copy(
                                        savedContent = contentToSave,
                                        lastKnownModifiedAt = result.newLastModified,
                                        lastKnownSizeBytes = result.newSizeBytes,
                                        saveState = if (stillDirty) SaveState.Unsaved else SaveState.Saved
                                    )
                                } else it
                            }
                        )
                    }
                    syncSessionTracking()
                    if (!silentOnSuccess) {
                        _events.tryEmit("Saved ${tab.fileName}")
                    }
                    true
                }
                is SaveFileResult.Conflict -> {
                    _uiState.update { current ->
                        current.copy(
                            conflictState = EditorConflictState(
                                relativePath = tab.relativePath,
                                fileName = tab.fileName,
                                diskLastModified = result.diskLastModified,
                                reason = result.reason
                            ),
                            tabs = current.tabs.map {
                                if (it.relativePath == relativePath) {
                                    it.copy(saveState = SaveState.SaveFailed(result.reason))
                                } else it
                            }
                        )
                    }
                    false
                }
                is SaveFileResult.Failure -> {
                    _uiState.update { current ->
                        current.copy(
                            tabs = current.tabs.map {
                                if (it.relativePath == relativePath) {
                                    it.copy(saveState = SaveState.SaveFailed(result.message))
                                } else it
                            }
                        )
                    }
                    _events.tryEmit(result.message)
                    false
                }
            }
        }
    }

    fun dismissConflictDialog() {
        _uiState.update { it.copy(conflictState = null) }
    }

    fun resolveConflictOverwrite(relativePath: String) {
        _uiState.update { it.copy(conflictState = null) }
        viewModelScope.launch {
            saveTabInternal(relativePath, forceOverwrite = true)
        }
    }

    fun resolveConflictReloadFromDisk(relativePath: String) {
        _uiState.update { it.copy(conflictState = null) }
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            val projectDir = File(project.internalProjectPath)
            val result = withContext(Dispatchers.IO) {
                EditorFileManager.readFileForEditor(projectDir, relativePath)
            }
            result.onSuccess { loaded ->
                historyManager.clearTab(relativePath)
                _uiState.update { current ->
                    current.copy(
                        tabs = current.tabs.map { tab ->
                            if (tab.relativePath == relativePath) {
                                tab.copy(
                                    encodingName = loaded.encodingName,
                                    hasBom = loaded.hasBom,
                                    lineEnding = loaded.lineEnding,
                                    textFieldValue = TextFieldValue(loaded.content),
                                    savedContent = loaded.content,
                                    lastKnownModifiedAt = loaded.lastModified,
                                    lastKnownSizeBytes = loaded.sizeBytes,
                                    saveState = SaveState.Saved,
                                    isLargeFile = loaded.isLargeFile,
                                    warningMessage = loaded.warningMessage
                                )
                            } else tab
                        },
                        canUndo = historyManager.canUndo(relativePath),
                        canRedo = historyManager.canRedo(relativePath)
                    )
                }
                syncSessionTracking()
                _events.tryEmit("Reloaded ${loaded.fileName} from disk.")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Failed to reload file.")
            }
        }
    }

    // Search & Find/Replace
    fun openSearch(showReplace: Boolean = false) {
        val activeText = _uiState.value.activeTab?.textFieldValue?.text.orEmpty()
        _uiState.update { state ->
            val nextSearch = recomputeSearch(
                state.searchState.copy(isVisible = true, isReplaceExpanded = showReplace),
                activeText
            )
            state.copy(searchState = nextSearch)
        }
    }

    fun closeSearch() {
        _uiState.update { state ->
            state.copy(searchState = state.searchState.copy(isVisible = false, matches = emptyList(), currentMatchIndex = -1))
        }
    }

    fun toggleReplaceExpanded() {
        _uiState.update { state ->
            state.copy(searchState = state.searchState.copy(isReplaceExpanded = !state.searchState.isReplaceExpanded))
        }
    }

    fun updateSearchQuery(query: String) {
        val activeText = _uiState.value.activeTab?.textFieldValue?.text.orEmpty()
        _uiState.update { state ->
            state.copy(searchState = recomputeSearch(state.searchState.copy(query = query), activeText))
        }
    }

    fun updateReplaceQuery(replaceQuery: String) {
        _uiState.update { state ->
            state.copy(searchState = state.searchState.copy(replaceQuery = replaceQuery))
        }
    }

    fun toggleMatchCase() {
        val activeText = _uiState.value.activeTab?.textFieldValue?.text.orEmpty()
        _uiState.update { state ->
            val toggled = state.searchState.copy(matchCase = !state.searchState.matchCase)
            state.copy(searchState = recomputeSearch(toggled, activeText))
        }
    }

    fun findNext() {
        val state = _uiState.value
        val matches = state.searchState.matches
        if (matches.isEmpty()) return
        val nextIndex = if (state.searchState.currentMatchIndex + 1 >= matches.size) 0 else state.searchState.currentMatchIndex + 1
        selectSearchMatch(nextIndex)
    }

    fun findPrevious() {
        val state = _uiState.value
        val matches = state.searchState.matches
        if (matches.isEmpty()) return
        val prevIndex = if (state.searchState.currentMatchIndex - 1 < 0) matches.lastIndex else state.searchState.currentMatchIndex - 1
        selectSearchMatch(prevIndex)
    }

    private fun selectSearchMatch(index: Int) {
        val activeTab = _uiState.value.activeTab ?: return
        val match = _uiState.value.searchState.matches.getOrNull(index) ?: return
        val selection = TextRange(match.first, match.last + 1)
        _uiState.update { state ->
            state.copy(
                searchState = state.searchState.copy(currentMatchIndex = index),
                tabs = state.tabs.map {
                    if (it.relativePath == activeTab.relativePath) {
                        it.copy(textFieldValue = it.textFieldValue.copy(selection = selection))
                    } else it
                },
                scrollToOffsetSignal = state.scrollToOffsetSignal + 1
            )
        }
    }

    fun replaceCurrentMatch(): Int {
        val state = _uiState.value
        val activeTab = state.activeTab ?: return 0
        val matches = state.searchState.matches
        val idx = state.searchState.currentMatchIndex
        if (matches.isEmpty() || idx !in matches.indices) return 0
        val targetRange = matches[idx]
        val replacement = state.searchState.replaceQuery
        val newText = activeTab.textFieldValue.text.replaceRange(
            targetRange.first,
            targetRange.last + 1,
            replacement
        )
        val newCursor = targetRange.first + replacement.length
        applyProgrammaticEdit(
            activeTab.relativePath,
            activeTab.textFieldValue.copy(text = newText, selection = TextRange(newCursor))
        )
        return 1
    }

    fun replaceAllMatches(): Int {
        val state = _uiState.value
        val activeTab = state.activeTab ?: return 0
        val matches = state.searchState.matches
        if (matches.isEmpty()) return 0
        val replacement = state.searchState.replaceQuery
        val source = activeTab.textFieldValue.text
        val sb = StringBuilder(source.length)
        var lastIdx = 0
        for (range in matches) {
            sb.append(source, lastIdx, range.first)
            sb.append(replacement)
            lastIdx = range.last + 1
        }
        sb.append(source, lastIdx, source.length)
        val newText = sb.toString()
        applyProgrammaticEdit(
            activeTab.relativePath,
            activeTab.textFieldValue.copy(
                text = newText,
                selection = TextRange(newText.length.coerceAtMost(activeTab.textFieldValue.selection.start))
            )
        )
        _events.tryEmit("Replaced ${matches.size} match(es).")
        return matches.size
    }

    private fun recomputeSearch(search: SearchState, text: String): SearchState {
        val matches = CodeEditingHelpers.findSearchMatches(text, search.query, search.matchCase)
        val nextIdx = when {
            matches.isEmpty() -> -1
            search.currentMatchIndex in matches.indices -> search.currentMatchIndex
            else -> 0
        }
        return search.copy(matches = matches, currentMatchIndex = nextIdx)
    }

    fun goToLine(lineNumber: Int) {
        val activeTab = _uiState.value.activeTab ?: return
        val offset = CodeEditingHelpers.getOffsetForLine(activeTab.textFieldValue.text, lineNumber)
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map {
                    if (it.relativePath == activeTab.relativePath) {
                        it.copy(textFieldValue = it.textFieldValue.copy(selection = TextRange(offset)))
                    } else it
                },
                scrollToOffsetSignal = state.scrollToOffsetSignal + 1
            )
        }
    }

    fun selectAllInActiveTab() {
        val activeTab = _uiState.value.activeTab ?: return
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map {
                    if (it.relativePath == activeTab.relativePath) {
                        it.copy(
                            textFieldValue = it.textFieldValue.copy(
                                selection = TextRange(0, it.textFieldValue.text.length)
                            )
                        )
                    } else it
                }
            )
        }
    }

    fun updateEditorSettings(transform: (EditorSettings) -> EditorSettings) {
        viewModelScope.launch {
            preferencesRepository.updateEditorSettings(transform)
        }
    }

    private fun syncSessionTracking() {
        val snapshotState = _uiState.value
        val openPaths = snapshotState.tabs.map { it.relativePath }.toSet()
        val dirtyPaths = snapshotState.tabs.filter { it.isDirty }.map { it.relativePath }.toSet()
        EditorSessionTracker.updateProjectState(projectId, openPaths, dirtyPaths)
        viewModelScope.launch(Dispatchers.IO) {
            sessionMutex.withLock {
                val latest = _uiState.value
                preferencesRepository.saveEditorSession(
                    EditorSessionSnapshot(
                        projectId = projectId,
                        openTabPaths = latest.tabs.map { it.relativePath },
                        activeTabPath = latest.activeTabPath
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
    }

    companion object {
        fun provideFactory(
            appContainer: AppContainer,
            projectId: Long,
            initialFilePath: String?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CodeEditorViewModel(
                    projectId = projectId,
                    initialFilePath = initialFilePath,
                    appContext = appContainer.appContext,
                    projectRepository = appContainer.projectRepository,
                    preferencesRepository = appContainer.userPreferencesRepository
                ) as T
            }
        }
    }
}
