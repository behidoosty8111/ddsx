package com.example.mfb.presentation.preview

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mfb.core.di.AppContainer
import com.example.mfb.data.engine.ProjectFileManager
import com.example.mfb.data.local.datastore.UserPreferencesRepository
import com.example.mfb.data.preview.ProjectResourceHandler
import com.example.mfb.domain.model.ConsoleLogSeverity
import com.example.mfb.domain.model.ConsoleLogType
import com.example.mfb.domain.model.PendingExternalNavigation
import com.example.mfb.domain.model.PreviewHistoryEntry
import com.example.mfb.domain.model.PreviewLoadState
import com.example.mfb.domain.model.PreviewLogEntry
import com.example.mfb.domain.model.PreviewSettings
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.ViewportPreset
import com.example.mfb.domain.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class LivePreviewUiState(
    val project: Project? = null,
    val entryRelativePath: String = "",
    val entryUrl: String = "",
    val projectOrigin: String = "",
    val loadState: PreviewLoadState = PreviewLoadState.PreparingProject,
    val currentFileLabel: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isFullScreen: Boolean = false,
    val selectedViewport: ViewportPreset = ViewportPreset.RESPONSIVE,
    val zoomPercent: Int = 100,
    val logs: List<PreviewLogEntry> = emptyList(),
    val isConsoleExpanded: Boolean = false,
    val pendingExternalNavigation: PendingExternalNavigation? = null,
    val activeGenerationId: Long = 0L
) {
    val errorCount: Int
        get() = logs.count { it.severity == ConsoleLogSeverity.ERROR }

    val warningCount: Int
        get() = logs.count { it.severity == ConsoleLogSeverity.WARNING }
}

class LivePreviewViewModel(
    private val projectId: Long,
    private val entryPointOverride: String?,
    private val appContext: Context,
    private val projectRepository: ProjectRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LivePreviewUiState())
    val uiState: StateFlow<LivePreviewUiState> = _uiState.asStateFlow()

    val previewSettings: StateFlow<PreviewSettings> = preferencesRepository.previewSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PreviewSettings()
        )

    private val loadGenerationCounter = AtomicLong(0L)
    private var resourceHandler: ProjectResourceHandler? = null
    private var internalPathPrefix: String = ""

    init {
        preparePreview()
    }

    fun currentGenerationId(): Long = loadGenerationCounter.get()

    fun preparePreview() {
        val genId = loadGenerationCounter.incrementAndGet()
        resourceHandler = null
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loadState = PreviewLoadState.PreparingProject,
                    entryUrl = "",
                    projectOrigin = "",
                    canGoBack = false,
                    canGoForward = false,
                    pendingExternalNavigation = null,
                    logs = emptyList(),
                    activeGenerationId = genId
                )
            }

            val (initialSettings, savedProjectConfig, project) = withContext(Dispatchers.IO) {
                Triple(
                    preferencesRepository.previewSettingsFlow.first(),
                    preferencesRepository.getProjectPreviewConfig(projectId),
                    projectRepository.getProjectById(projectId)
                )
            }
            val resolvedViewport = savedProjectConfig?.viewportPreset ?: initialSettings.defaultViewportPreset
            val resolvedZoom = savedProjectConfig?.zoomPercent ?: 100

            if (project == null) {
                _uiState.update {
                    it.copy(
                        loadState = PreviewLoadState.Failed(
                            message = "Project could not be found.",
                            isEntryMissing = true
                        )
                    )
                }
                return@launch
            }

            val projectDir = File(project.internalProjectPath)
            internalPathPrefix = try {
                projectDir.canonicalPath
            } catch (_: Exception) {
                projectDir.absolutePath
            }

            val requestedEntry = (entryPointOverride?.takeIf { it.isNotBlank() } ?: project.mainHtmlRelativePath)
                .trim()
                .replace('\\', '/')
                .trimStart('/')

            val validationResult = withContext(Dispatchers.IO) {
                if (!projectDir.exists() || !projectDir.isDirectory) {
                    Result.failure(IllegalStateException("Project storage directory is missing."))
                } else if (requestedEntry.isEmpty()) {
                    Result.failure(IllegalArgumentException("No HTML entry point is configured for this project."))
                } else {
                    val safeRes = ProjectFileManager.resolveSafePath(projectDir, requestedEntry)
                    safeRes.fold(
                        onSuccess = { target ->
                            if (!target.exists() || !target.isFile || !target.canRead()) {
                                Result.failure(IllegalStateException("Entry Point Not Found: '$requestedEntry' does not exist or cannot be read."))
                            } else {
                                Result.success(target)
                            }
                        },
                        onFailure = { err -> Result.failure(err) }
                    )
                }
            }

            if (validationResult.isFailure) {
                val reason = validationResult.exceptionOrNull()?.message ?: "Entry Point Not Found"
                if (genId != loadGenerationCounter.get()) return@launch
                _uiState.update {
                    it.copy(
                        project = project,
                        entryRelativePath = requestedEntry,
                        currentFileLabel = requestedEntry.ifBlank { "Missing Entry" },
                        selectedViewport = resolvedViewport,
                        zoomPercent = resolvedZoom,
                        loadState = PreviewLoadState.Failed(
                            message = reason,
                            isEntryMissing = true
                        )
                    )
                }
                return@launch
            }

            val host = ProjectResourceHandler.buildProjectHost(project.projectUuid.ifBlank { project.id.toString() })
            val handler = ProjectResourceHandler(
                projectDir = projectDir,
                projectHost = host,
                onMissingResource = { missingPath ->
                    if (genId == loadGenerationCounter.get()) {
                        appendLog(
                            severity = ConsoleLogSeverity.ERROR,
                            type = ConsoleLogType.RESOURCE_ERROR,
                            message = "Local resource not found: $missingPath",
                            safeResourceRef = missingPath,
                            lineNumber = null
                        )
                    }
                },
                onSecurityViolation = { msg, ref ->
                    if (genId == loadGenerationCounter.get()) {
                        appendLog(
                            severity = ConsoleLogSeverity.ERROR,
                            type = ConsoleLogType.SECURITY_POLICY,
                            message = msg,
                            safeResourceRef = ref,
                            lineNumber = null
                        )
                    }
                }
            )
            if (genId != loadGenerationCounter.get()) return@launch
            resourceHandler = handler

            val entryUrl = handler.buildEntryUrl(requestedEntry)
            _uiState.update {
                it.copy(
                    project = project,
                    entryRelativePath = requestedEntry,
                    entryUrl = entryUrl,
                    projectOrigin = handler.projectOrigin,
                    currentFileLabel = requestedEntry,
                    selectedViewport = resolvedViewport,
                    zoomPercent = resolvedZoom,
                    loadState = PreviewLoadState.LoadingPage(0)
                )
            }

            withContext(Dispatchers.IO) {
                preferencesRepository.savePreviewHistory(
                    PreviewHistoryEntry(
                        projectId = project.id,
                        entryPointRelativePath = requestedEntry,
                        viewportPreset = resolvedViewport,
                        zoomPercent = resolvedZoom,
                        lastPreviewedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun getResourceHandler(): ProjectResourceHandler? = resourceHandler

    fun sanitizeText(raw: String): String {
        var cleaned = raw
        if (internalPathPrefix.isNotBlank()) {
            cleaned = cleaned.replace(internalPathPrefix, "<project>")
        }
        val filesPath = appContext.filesDir.absolutePath
        if (filesPath.isNotBlank()) {
            cleaned = cleaned.replace(filesPath, "<sandbox>")
        }
        return cleaned
    }

    fun onPageStarted(safeRelativeRef: String, generationId: Long = currentGenerationId()) {
        if (generationId != loadGenerationCounter.get()) return
        _uiState.update {
            it.copy(
                loadState = PreviewLoadState.LoadingPage(10),
                currentFileLabel = safeRelativeRef.ifBlank { it.entryRelativePath }
            )
        }
    }

    fun onProgressChanged(progress: Int, generationId: Long = currentGenerationId()) {
        if (generationId != loadGenerationCounter.get()) return
        _uiState.update { state ->
            when {
                state.loadState is PreviewLoadState.Failed || state.loadState is PreviewLoadState.Cancelled -> state
                progress < 100 -> state.copy(loadState = PreviewLoadState.LoadingPage(progress))
                state.loadState is PreviewLoadState.LoadingPage -> state.copy(loadState = PreviewLoadState.Rendering)
                else -> state
            }
        }
    }

    fun onPageFinished(
        safeRelativeRef: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
        generationId: Long = currentGenerationId()
    ) {
        if (generationId != loadGenerationCounter.get()) return
        _uiState.update { state ->
            val nextLoadState = if (state.loadState is PreviewLoadState.Failed || state.loadState is PreviewLoadState.Cancelled) {
                state.loadState
            } else {
                PreviewLoadState.Loaded(
                    currentUrl = safeRelativeRef.ifBlank { state.entryRelativePath },
                    pageTitle = title
                )
            }
            state.copy(
                loadState = nextLoadState,
                currentFileLabel = safeRelativeRef.ifBlank { state.entryRelativePath },
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    fun onHistoryUpdated(
        safeRelativeRef: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
        generationId: Long = currentGenerationId()
    ) {
        if (generationId != loadGenerationCounter.get()) return
        _uiState.update { state ->
            state.copy(
                currentFileLabel = safeRelativeRef.ifBlank { state.currentFileLabel },
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    fun onMainPageError(message: String, safeRelativeRef: String, generationId: Long = currentGenerationId()) {
        if (generationId != loadGenerationCounter.get()) return
        val clean = sanitizeText(message)
        appendLog(
            severity = ConsoleLogSeverity.ERROR,
            type = ConsoleLogType.PAGE_LOAD_ERROR,
            message = clean,
            safeResourceRef = safeRelativeRef,
            lineNumber = null
        )
        _uiState.update {
            it.copy(loadState = PreviewLoadState.Failed(clean, isEntryMissing = false))
        }
    }

    fun markCancelled() {
        _uiState.update {
            it.copy(loadState = PreviewLoadState.Cancelled)
        }
    }

    fun appendLog(
        severity: ConsoleLogSeverity,
        type: ConsoleLogType,
        message: String,
        safeResourceRef: String,
        lineNumber: Int? = null
    ) {
        val entry = PreviewLogEntry(
            id = UUID.randomUUID().toString(),
            timestampMs = System.currentTimeMillis(),
            severity = severity,
            type = type,
            message = sanitizeText(message),
            safeResourceRef = sanitizeText(safeResourceRef),
            lineNumber = lineNumber
        )
        _uiState.update { state ->
            val updated = (state.logs + entry).takeLast(200)
            state.copy(logs = updated)
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun toggleConsoleExpanded() {
        _uiState.update { it.copy(isConsoleExpanded = !it.isConsoleExpanded) }
    }

    fun setConsoleExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isConsoleExpanded = expanded) }
    }

    fun toggleFullScreen() {
        _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
    }

    fun exitFullScreen() {
        _uiState.update { it.copy(isFullScreen = false) }
    }

    fun selectViewport(preset: ViewportPreset) {
        _uiState.update { it.copy(selectedViewport = preset) }
        persistProjectPreviewConfig(preset = preset, zoomPercent = _uiState.value.zoomPercent)
    }

    fun zoomIn() {
        val next = (_uiState.value.zoomPercent + 25).coerceAtMost(200)
        _uiState.update { it.copy(zoomPercent = next) }
        persistProjectPreviewConfig(preset = _uiState.value.selectedViewport, zoomPercent = next)
    }

    fun zoomOut() {
        val next = (_uiState.value.zoomPercent - 25).coerceAtLeast(50)
        _uiState.update { it.copy(zoomPercent = next) }
        persistProjectPreviewConfig(preset = _uiState.value.selectedViewport, zoomPercent = next)
    }

    fun resetZoom() {
        _uiState.update { it.copy(zoomPercent = 100) }
        persistProjectPreviewConfig(preset = _uiState.value.selectedViewport, zoomPercent = 100)
    }

    private fun persistProjectPreviewConfig(preset: ViewportPreset, zoomPercent: Int) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.savePreviewHistory(
                PreviewHistoryEntry(
                    projectId = project.id,
                    entryPointRelativePath = _uiState.value.entryRelativePath,
                    viewportPreset = preset,
                    zoomPercent = zoomPercent,
                    lastPreviewedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun requestExternalNavigation(pending: PendingExternalNavigation) {
        _uiState.update { it.copy(pendingExternalNavigation = pending) }
    }

    fun dismissExternalNavigation() {
        _uiState.update { it.copy(pendingExternalNavigation = null) }
    }

    fun updatePreviewSettings(transform: (PreviewSettings) -> PreviewSettings) {
        viewModelScope.launch {
            preferencesRepository.updatePreviewSettings(transform)
        }
    }

    fun formatLogsForCopy(): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return _uiState.value.logs.joinToString("\n") { log ->
            val lineSuffix = log.lineNumber?.let { " (line $it)" } ?: ""
            "[${fmt.format(Date(log.timestampMs))}] [${log.severity.name}] [${log.type.label}] (${log.safeResourceRef}$lineSuffix): ${log.message}"
        }
    }

    companion object {
        fun provideFactory(
            appContainer: AppContainer,
            projectId: Long,
            entryPointOverride: String?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LivePreviewViewModel(
                    projectId = projectId,
                    entryPointOverride = entryPointOverride,
                    appContext = appContainer.appContext,
                    projectRepository = appContainer.projectRepository,
                    preferencesRepository = appContainer.userPreferencesRepository
                ) as T
            }
        }
    }
}
