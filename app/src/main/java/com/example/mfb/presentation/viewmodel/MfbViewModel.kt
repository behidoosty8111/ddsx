package com.example.mfb.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.mfb.core.di.AppContainer
import com.example.mfb.core.model.ThemeMode
import com.example.mfb.data.engine.HtmlImportEngine
import com.example.mfb.data.engine.HtmlImportResult
import com.example.mfb.data.engine.ProjectFileManager
import com.example.mfb.data.engine.ProjectHealthChecker
import com.example.mfb.data.engine.ProjectStorageManager
import com.example.mfb.data.engine.ZipImportEngine
import com.example.mfb.data.engine.ZipImportResult
import com.example.mfb.data.local.datastore.UserPreferencesRepository
import com.example.mfb.domain.model.BuildRecord
import com.example.mfb.domain.model.EditorSettings
import com.example.mfb.domain.model.FileCategory
import com.example.mfb.domain.model.FileExplorerUiState
import com.example.mfb.domain.model.FilePreviewContent
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.ProjectFileItem
import com.example.mfb.domain.model.ProjectFilterOption
import com.example.mfb.domain.model.ProjectHealthReport
import com.example.mfb.domain.model.ProjectHealthStatus
import com.example.mfb.domain.model.ProjectOperationState
import com.example.mfb.domain.model.ProjectSortOption
import com.example.mfb.domain.model.ProjectStatistics
import com.example.mfb.domain.model.ProjectViewMode
import com.example.mfb.domain.model.ReleaseInfo
import com.example.mfb.domain.model.SourceType
import com.example.mfb.domain.repository.BuildRepository
import com.example.mfb.domain.repository.ChangelogRepository
import com.example.mfb.domain.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed class ImportUiState {
    object Idle : ImportUiState()
    data class Progress(val progress: Float, val statusMessage: String) : ImportUiState()
    data class NeedsEntryPointChoice(
        val tempDirName: String,
        val candidates: List<String>,
        val originalFileName: String,
        val suggestedName: String
    ) : ImportUiState()
    data class Success(val project: Project, val warnings: List<String> = emptyList()) : ImportUiState()
    data class Error(val errorMessage: String) : ImportUiState()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MfbViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val projectRepository: ProjectRepository,
    private val buildRepository: BuildRepository,
    private val changelogRepository: ChangelogRepository? = null,
    private val appContext: Context? = null
) : ViewModel() {

    private val operationMutex = Mutex()

    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.DARK
        )

    val appLanguage: StateFlow<String> = preferencesRepository.appLanguageFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "en"
        )

    val editorSettings: StateFlow<EditorSettings> = preferencesRepository.editorSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EditorSettings()
        )

    fun updateEditorSettings(transform: (EditorSettings) -> EditorSettings) {
        viewModelScope.launch {
            preferencesRepository.updateEditorSettings(transform)
        }
    }

    private val _projectsLoading = MutableStateFlow(true)
    val projectsLoading: StateFlow<Boolean> = _projectsLoading.asStateFlow()

    private val _projectsError = MutableStateFlow<String?>(null)
    val projectsError: StateFlow<String?> = _projectsError.asStateFlow()

    val projects: StateFlow<List<Project>> = projectRepository.getAllProjects()
        .onStart { _projectsLoading.value = true }
        .map { list ->
            _projectsLoading.value = false
            _projectsError.value = null
            list
        }
        .catch { e ->
            _projectsLoading.value = false
            _projectsError.value = e.message ?: "Failed to load projects"
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentBuilds: StateFlow<List<BuildRecord>> = buildRepository.getRecentBuilds(5)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- My Projects Search / Sort / Filter / ViewMode ---
    private val _projectSearchQuery = MutableStateFlow("")
    val projectSearchQuery: StateFlow<String> = _projectSearchQuery.asStateFlow()

    val projectSortOption: StateFlow<ProjectSortOption> = preferencesRepository.projectSortOptionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProjectSortOption.RECENTLY_MODIFIED
        )

    val projectFilterOption: StateFlow<ProjectFilterOption> = preferencesRepository.projectFilterOptionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProjectFilterOption.ALL
        )

    private val _projectViewMode = MutableStateFlow(ProjectViewMode.LIST)
    val projectViewMode: StateFlow<ProjectViewMode> = _projectViewMode.asStateFlow()

    // Database-backed search with debounce, combined with persisted sort & filter
    private val searchedProjectsFlow = _projectSearchQuery
        .debounce(150L)
        .flatMapLatest { query ->
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                projectRepository.getAllProjects()
            } else {
                projectRepository.searchProjects(trimmed)
            }
        }

    val filteredProjects: StateFlow<List<Project>> = combine(
        searchedProjectsFlow,
        projectSortOption,
        projectFilterOption
    ) { list, sort, filter ->
        val recentCutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        val filtered = list.filter { project ->
            when (filter) {
                ProjectFilterOption.ALL -> true
                ProjectFilterOption.HTML -> project.sourceType == SourceType.HTML
                ProjectFilterOption.ZIP -> project.sourceType == SourceType.ZIP
                ProjectFilterOption.HEALTHY -> project.healthStatus == ProjectHealthStatus.HEALTHY
                ProjectFilterOption.WARNINGS -> project.healthStatus != ProjectHealthStatus.HEALTHY &&
                    project.healthStatus != ProjectHealthStatus.CHECK_REQUIRED ||
                    project.missingLocalAssets.isNotEmpty()
                ProjectFilterOption.RECENTLY_MODIFIED -> project.updatedAt >= recentCutoff
                ProjectFilterOption.FAVORITES -> project.isFavorite
            }
        }
        when (sort) {
            ProjectSortOption.RECENTLY_MODIFIED -> filtered.sortedByDescending { it.updatedAt }
            ProjectSortOption.NEWEST_FIRST -> filtered.sortedByDescending { it.createdAt }
            ProjectSortOption.OLDEST_FIRST -> filtered.sortedBy { it.createdAt }
            ProjectSortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            ProjectSortOption.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            ProjectSortOption.LARGEST -> filtered.sortedByDescending { it.totalSizeBytes }
            ProjectSortOption.SMALLEST -> filtered.sortedBy { it.totalSizeBytes }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Project Statistics (calculated on Dispatchers.IO) ---
    val projectStatistics: StateFlow<ProjectStatistics> = projects
        .map { list ->
            computeStatistics(list)
        }
        .flowOn(Dispatchers.IO)
        .catch {
            emit(ProjectStatistics(isUnavailable = true))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProjectStatistics()
        )

    private fun computeStatistics(list: List<Project>): ProjectStatistics {
        if (list.isEmpty()) return ProjectStatistics()
        var htmlCount = 0
        var zipCount = 0
        var warningCount = 0
        var recentlyModifiedCount = 0
        var totalFiles = 0
        var totalBytes = 0L
        var missingDirs = 0
        val recentCutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L

        list.forEach { project ->
            if (project.sourceType == SourceType.HTML) htmlCount++ else zipCount++
            if (project.updatedAt >= recentCutoff) recentlyModifiedCount++
            val hasWarning = project.healthStatus != ProjectHealthStatus.HEALTHY &&
                project.healthStatus != ProjectHealthStatus.CHECK_REQUIRED ||
                project.missingLocalAssets.isNotEmpty()
            if (hasWarning) warningCount++

            val dir = resolveProjectDirectory(appContext, project)
            if (dir != null && dir.exists() && dir.isDirectory) {
                val (files, bytes) = ProjectStorageManager.calculateStats(dir)
                totalFiles += files
                totalBytes += bytes
            } else {
                if (project.internalProjectPath.isNotBlank()) {
                    missingDirs++
                }
                totalFiles += project.fileCount
                totalBytes += project.totalSizeBytes
            }
        }

        return ProjectStatistics(
            totalProjects = list.size,
            htmlCount = htmlCount,
            zipCount = zipCount,
            warningCount = warningCount,
            recentlyModifiedCount = recentlyModifiedCount,
            totalFiles = totalFiles,
            totalSizeBytes = totalBytes,
            largestProject = list.maxByOrNull { it.totalSizeBytes },
            mostRecentlyModifiedProject = list.maxByOrNull { it.updatedAt },
            missingDirectoriesCount = missingDirs,
            isUnavailable = false
        )
    }

    // --- Changelog & What's New ---
    val releases: List<ReleaseInfo>
        get() = changelogRepository?.getAllReleases() ?: emptyList()

    val whatsNewRelease: StateFlow<ReleaseInfo?> = preferencesRepository.lastViewedVersionCodeFlow
        .map { lastViewed ->
            val currentVersionCode = BuildConfig.VERSION_CODE
            if (currentVersionCode > lastViewed) {
                val repo = changelogRepository ?: return@map null
                repo.getReleaseByVersionCode(currentVersionCode)
                    ?: repo.getLatestRelease()?.takeIf { it.versionCode <= currentVersionCode && it.versionCode > lastViewed }
            } else {
                null
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun dismissWhatsNew() {
        viewModelScope.launch {
            preferencesRepository.setLastViewedVersionCode(BuildConfig.VERSION_CODE)
        }
    }

    // --- Import State ---
    private val _importUiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importUiState: StateFlow<ImportUiState> = _importUiState.asStateFlow()

    // --- Project Operation State ---
    private val _operationState = MutableStateFlow<ProjectOperationState>(ProjectOperationState.Idle)
    val operationState: StateFlow<ProjectOperationState> = _operationState.asStateFlow()

    fun clearOperationState() {
        _operationState.value = ProjectOperationState.Idle
    }

    // --- File Explorer, Health & Entry Point State ---
    private val _fileExplorerState = MutableStateFlow(FileExplorerUiState())
    val fileExplorerState: StateFlow<FileExplorerUiState> = _fileExplorerState.asStateFlow()

    private val _currentHealthReport = MutableStateFlow<ProjectHealthReport?>(null)
    val currentHealthReport: StateFlow<ProjectHealthReport?> = _currentHealthReport.asStateFlow()

    private val _htmlEntryCandidates = MutableStateFlow<List<String>>(emptyList())
    val htmlEntryCandidates: StateFlow<List<String>> = _htmlEntryCandidates.asStateFlow()

    private val _filePreviewContent = MutableStateFlow<FilePreviewContent?>(null)
    val filePreviewContent: StateFlow<FilePreviewContent?> = _filePreviewContent.asStateFlow()

    fun setProjectSearchQuery(query: String) {
        _projectSearchQuery.value = query
    }

    fun setProjectSortOption(option: ProjectSortOption) {
        viewModelScope.launch {
            preferencesRepository.setProjectSortOption(option)
        }
    }

    fun setProjectFilterOption(option: ProjectFilterOption) {
        viewModelScope.launch {
            preferencesRepository.setProjectFilterOption(option)
        }
    }

    fun setProjectViewMode(mode: ProjectViewMode) {
        _projectViewMode.value = mode
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setAppLanguage(languageCode: String) {
        viewModelScope.launch {
            preferencesRepository.setAppLanguage(languageCode)
        }
    }

    fun resetImportState() {
        _importUiState.value = ImportUiState.Idle
    }

    fun reportImportError(message: String) {
        android.util.Log.e("MfbZipImport", "Import error reported: $message")
        _importUiState.value = ImportUiState.Error(message)
    }

    fun importZip(context: Context, uri: Uri, customProjectName: String? = null) {
        viewModelScope.launch {
            android.util.Log.d("MfbZipImport", "ViewModel.importZip started for uri=$uri")
            _importUiState.value = ImportUiState.Progress(0.05f, "ZIP selected. Preparing extraction...")

            try {
                val result = ZipImportEngine.importFromUri(
                    context = context,
                    uri = uri,
                    customProjectName = customProjectName,
                    onProgress = { progress, message ->
                        _importUiState.value = ImportUiState.Progress(progress, message)
                    }
                )

                when (result) {
                    is ZipImportResult.Success -> {
                        _importUiState.value = ImportUiState.Progress(0.98f, "Saving project to database...")
                        try {
                            val saved = saveImportedProjectWithHealth(context, result.project)
                            android.util.Log.d("MfbZipImport", "Import completed: id=${saved.id}, entry=${saved.mainHtmlRelativePath}")
                            _importUiState.value = ImportUiState.Success(saved, emptyList())
                        } catch (dbErr: Exception) {
                            ProjectStorageManager.deleteProjectSafely(context, result.project.projectUuid)
                            android.util.Log.e("MfbZipImport", "Database registration failed", dbErr)
                            _importUiState.value = ImportUiState.Error(
                                "Failed to save project to database: ${dbErr.localizedMessage ?: "Unknown error"}"
                            )
                        }
                    }

                    is ZipImportResult.NeedsEntryPointChoice -> {
                        _importUiState.value = ImportUiState.NeedsEntryPointChoice(
                            tempDirName = result.tempDirName,
                            candidates = result.candidates,
                            originalFileName = result.originalFileName,
                            suggestedName = result.suggestedName
                        )
                    }

                    is ZipImportResult.Error -> {
                        android.util.Log.e("MfbZipImport", "ZIP import failed: ${result.message}", result.exception)
                        _importUiState.value = ImportUiState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MfbZipImport", "Unexpected ZIP import failure", e)
                _importUiState.value = ImportUiState.Error("ZIP import failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    fun completeZipWithEntryPoint(
        context: Context,
        tempDirName: String,
        selectedEntryPoint: String,
        projectName: String,
        originalFileName: String
    ) {
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Progress(0.92f, "Finalizing selected entry point: $selectedEntryPoint")

            try {
                val result = ZipImportEngine.completeWithSelectedEntryPoint(
                    context = context,
                    tempDirName = tempDirName,
                    selectedEntryPoint = selectedEntryPoint,
                    projectName = projectName,
                    originalFileName = originalFileName,
                    onProgress = { progress, message ->
                        _importUiState.value = ImportUiState.Progress(progress, message)
                    }
                )

                when (result) {
                    is ZipImportResult.Success -> {
                        _importUiState.value = ImportUiState.Progress(0.98f, "Saving project to database...")
                        try {
                            val saved = saveImportedProjectWithHealth(context, result.project)
                            android.util.Log.d("MfbZipImport", "Import completed after entry choice: id=${saved.id}")
                            _importUiState.value = ImportUiState.Success(saved, emptyList())
                        } catch (dbErr: Exception) {
                            ProjectStorageManager.deleteProjectSafely(context, result.project.projectUuid)
                            android.util.Log.e("MfbZipImport", "Database registration failed", dbErr)
                            _importUiState.value = ImportUiState.Error(
                                "Failed to save project to database: ${dbErr.localizedMessage ?: "Unknown error"}"
                            )
                        }
                    }

                    is ZipImportResult.Error -> {
                        _importUiState.value = ImportUiState.Error(result.message)
                    }

                    else -> {}
                }
            } catch (e: Exception) {
                android.util.Log.e("MfbZipImport", "Unexpected error finalizing ZIP entry point", e)
                _importUiState.value = ImportUiState.Error("Finalizing project failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    fun importHtml(context: Context, uri: Uri, customProjectName: String? = null) {
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Progress(0.10f, "Starting HTML import...")

            val result = HtmlImportEngine.importFromUri(
                context = context,
                uri = uri,
                customProjectName = customProjectName,
                onProgress = { progress, message ->
                    _importUiState.value = ImportUiState.Progress(progress, message)
                }
            )

            when (result) {
                is HtmlImportResult.Success -> {
                    val saved = saveImportedProjectWithHealth(context, result.project)
                    _importUiState.value = ImportUiState.Success(saved, result.warnings)
                }

                is HtmlImportResult.Error -> {
                    _importUiState.value = ImportUiState.Error(result.message)
                }
            }
        }
    }

    private suspend fun saveImportedProjectWithHealth(context: Context, project: Project): Project {
        val report = withContext(Dispatchers.IO) {
            ProjectHealthChecker.checkProjectHealth(context, project)
        }
        val enriched = project.copy(
            healthStatus = report.status,
            healthMessage = report.diagnostics.joinToString("; "),
            missingLocalAssets = report.missingLocalAssets
        )
        val id = projectRepository.insertProject(enriched)
        return enriched.copy(id = id)
    }

    // --- Health & Entry Point Management ---

    fun refreshProjectHealth(context: Context, project: Project) {
        viewModelScope.launch {
            val (report, candidates) = withContext(Dispatchers.IO) {
                val health = ProjectHealthChecker.checkProjectHealth(context, project)
                val dir = resolveProjectDirectory(context, project)
                val htmls = if (dir != null && dir.exists()) {
                    ProjectFileManager.findHtmlEntryCandidates(dir)
                } else emptyList()
                Pair(health, htmls)
            }
            _currentHealthReport.value = report
            _htmlEntryCandidates.value = candidates
            projectRepository.updateHealthStatus(
                id = project.id,
                status = report.status,
                message = report.diagnostics.joinToString("; "),
                missingLocalAssets = report.missingLocalAssets
            )
        }
    }

    fun changeEntryPoint(context: Context, project: Project, newEntryPointRelativePath: String) {
        viewModelScope.launch {
            operationMutex.withLock {
                val validation = withContext(Dispatchers.IO) {
                    val projectDir = resolveProjectDirectory(context, project)
                        ?: return@withContext Result.failure<String>(IllegalStateException("Project directory missing."))
                    val target = ProjectFileManager.resolveSafePath(projectDir, newEntryPointRelativePath)
                        .getOrElse { return@withContext Result.failure<String>(it) }
                    if (!target.exists() || !target.isFile) {
                        return@withContext Result.failure<String>(IllegalArgumentException("Selected HTML file does not exist."))
                    }
                    val ext = target.extension.lowercase()
                    if (ext != "html" && ext != "htm") {
                        return@withContext Result.failure<String>(IllegalArgumentException("Entry point must be an .html or .htm file."))
                    }
                    Result.success(newEntryPointRelativePath.trimStart('/'))
                }
                validation.fold(
                    onSuccess = { validPath ->
                        projectRepository.updateEntryPoint(project.id, validPath)
                        val updated = projectRepository.getProjectById(project.id)
                        if (updated != null) {
                            withContext(Dispatchers.IO) {
                                resolveProjectDirectory(context, updated)?.let { dir ->
                                    ProjectStorageManager.writeMetadata(dir, updated)
                                }
                            }
                            refreshProjectHealth(context, updated)
                        }
                        _operationState.value = ProjectOperationState.Success("Entry point updated to '$validPath'")
                    },
                    onFailure = { err ->
                        _operationState.value = ProjectOperationState.Error(err.message ?: "Failed to update entry point.")
                    }
                )
            }
        }
    }

    // --- Project Organization & Operations ---

    fun toggleFavorite(project: Project) {
        viewModelScope.launch {
            projectRepository.toggleFavorite(project.id, !project.isFavorite)
        }
    }

    fun recordProjectOpened(projectId: Long) {
        viewModelScope.launch {
            projectRepository.recordProjectOpened(projectId)
        }
    }

    fun renameProject(projectId: Long, newName: String) {
        val validation = ProjectFileManager.validateItemName(newName)
        if (validation.isFailure) {
            _operationState.value = ProjectOperationState.Error(
                validation.exceptionOrNull()?.message ?: "Invalid project name."
            )
            return
        }
        val trimmed = validation.getOrThrow()
        viewModelScope.launch {
            operationMutex.withLock {
                val isDuplicate = projectRepository.existsByNameExcludingId(trimmed, projectId)
                projectRepository.updateProjectName(projectId, trimmed)
                withContext(Dispatchers.IO) {
                    val updated = projectRepository.getProjectById(projectId)
                    val dir = updated?.let { resolveProjectDirectory(appContext, it) }
                    if (updated != null && dir != null && dir.exists()) {
                        ProjectStorageManager.writeMetadata(dir, updated)
                    }
                }
                val note = if (isDuplicate) " (note: another project shares this name)" else ""
                _operationState.value = ProjectOperationState.Success("Project renamed to '$trimmed'$note")
            }
        }
    }

    fun updateProjectMetadata(projectId: Long, name: String, description: String, tags: List<String>) {
        val validation = ProjectFileManager.validateItemName(name)
        if (validation.isFailure) {
            _operationState.value = ProjectOperationState.Error(
                validation.exceptionOrNull()?.message ?: "Project name is invalid."
            )
            return
        }
        val trimmedName = validation.getOrThrow()
        val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        viewModelScope.launch {
            operationMutex.withLock {
                projectRepository.updateProjectMetadata(
                    id = projectId,
                    name = trimmedName,
                    description = description.trim(),
                    tags = cleanTags
                )
                withContext(Dispatchers.IO) {
                    val updated = projectRepository.getProjectById(projectId)
                    val dir = updated?.let { resolveProjectDirectory(appContext, it) }
                    if (updated != null && dir != null && dir.exists()) {
                        ProjectStorageManager.writeMetadata(dir, updated)
                    }
                }
                _operationState.value = ProjectOperationState.Success("Project details updated")
            }
        }
    }

    fun duplicateProject(context: Context, project: Project) {
        viewModelScope.launch {
            operationMutex.withLock {
                _operationState.value = ProjectOperationState.InProgress("Duplicating '${project.name}'...")
                val result = withContext(Dispatchers.IO) {
                    val sourceDir = resolveProjectDirectory(context, project)
                        ?: return@withContext Result.failure<Project>(IllegalStateException("Source directory unavailable."))
                    val newUuid = UUID.randomUUID().toString()
                    val targetDir = ProjectStorageManager.getProjectDir(context, newUuid)
                    val copyResult = ProjectFileManager.duplicateProjectFiles(sourceDir, targetDir)
                    copyResult.fold(
                        onSuccess = { (fileCount, totalBytes) ->
                            val now = System.currentTimeMillis()
                            val duplicated = project.copy(
                                id = 0L,
                                projectUuid = newUuid,
                                name = "${project.name} (Copy)",
                                internalProjectPath = targetDir.absolutePath,
                                sourcePath = targetDir.absolutePath,
                                fileCount = fileCount,
                                totalSizeBytes = totalBytes,
                                createdAt = now,
                                updatedAt = now,
                                lastOpenedAt = 0L,
                                isFavorite = false
                            )
                            val healthReport = ProjectHealthChecker.checkProjectHealth(context, duplicated)
                            val verifiedDup = duplicated.copy(
                                healthStatus = healthReport.status,
                                healthMessage = healthReport.diagnostics.joinToString("; "),
                                missingLocalAssets = healthReport.missingLocalAssets
                            )
                            try {
                                ProjectStorageManager.writeMetadata(targetDir, verifiedDup)
                                val newId = projectRepository.insertProject(verifiedDup)
                                Result.success(verifiedDup.copy(id = newId))
                            } catch (e: Exception) {
                                targetDir.deleteRecursively()
                                Result.failure(e)
                            }
                        },
                        onFailure = { err ->
                            targetDir.deleteRecursively()
                            Result.failure(err)
                        }
                    )
                }
                result.fold(
                    onSuccess = { dup ->
                        _operationState.value = ProjectOperationState.Success("Duplicated as '${dup.name}'")
                    },
                    onFailure = { err ->
                        _operationState.value = ProjectOperationState.Error(err.message ?: "Failed to duplicate project.")
                    }
                )
            }
        }
    }

    fun deleteProject(context: Context, project: Project) {
        viewModelScope.launch {
            operationMutex.withLock {
                _operationState.value = ProjectOperationState.InProgress("Deleting '${project.name}'...")
                val deleteResult = withContext(Dispatchers.IO) {
                    val dirDelete = ProjectStorageManager.deleteProjectSafely(context, project.projectUuid)
                    if (dirDelete.isFailure) {
                        return@withContext dirDelete
                    }
                    projectRepository.deleteProject(project)
                    Result.success(Unit)
                }
                deleteResult.fold(
                    onSuccess = {
                        _operationState.value = ProjectOperationState.Success(
                            message = "Deleted '${project.name}'",
                            deletedProjectId = project.id
                        )
                    },
                    onFailure = { err ->
                        _operationState.value = ProjectOperationState.Error(
                            err.message ?: "Failed to delete project files completely."
                        )
                    }
                )
            }
        }
    }

    fun exportProjectAsZip(context: Context, project: Project, destinationUri: Uri) {
        viewModelScope.launch {
            operationMutex.withLock {
                _operationState.value = ProjectOperationState.InProgress("Exporting '${project.name}' as ZIP...")
                val exportResult = withContext(Dispatchers.IO) {
                    val projectDir = resolveProjectDirectory(context, project)
                        ?: return@withContext Result.failure<Int>(IllegalStateException("Project directory not found."))
                    val outputStream = context.contentResolver.openOutputStream(destinationUri)
                        ?: return@withContext Result.failure<Int>(IllegalStateException("Could not open destination file for writing."))
                    outputStream.use { os ->
                        ProjectFileManager.exportProjectToZip(projectDir, os)
                    }
                }
                exportResult.fold(
                    onSuccess = { count ->
                        _operationState.value = ProjectOperationState.Success("Exported $count file(s) to ZIP archive.")
                    },
                    onFailure = { err ->
                        _operationState.value = ProjectOperationState.Error(err.message ?: "ZIP export failed.")
                    }
                )
            }
        }
    }

    // --- File Explorer & Read-Only Preview Operations ---

    fun loadProjectFiles(context: Context, project: Project, relativePath: String = _fileExplorerState.value.currentRelativePath) {
        viewModelScope.launch {
            _fileExplorerState.value = _fileExplorerState.value.copy(
                currentRelativePath = relativePath,
                isLoading = true,
                errorMessage = null
            )
            val state = _fileExplorerState.value
            val result = withContext(Dispatchers.IO) {
                val projectDir = resolveProjectDirectory(context, project)
                    ?: return@withContext Result.failure<List<ProjectFileItem>>(IllegalStateException("Project directory missing on disk."))
                if (!projectDir.exists() || !projectDir.isDirectory) {
                    return@withContext Result.failure<List<ProjectFileItem>>(IllegalStateException("Project directory missing on disk."))
                }
                if (state.searchQuery.isNotBlank() || state.selectedCategory != FileCategory.ALL) {
                    ProjectFileManager.searchFiles(projectDir, state.searchQuery, state.selectedCategory)
                } else {
                    ProjectFileManager.listDirectory(projectDir, relativePath)
                }
            }
            result.fold(
                onSuccess = { items ->
                    _fileExplorerState.value = _fileExplorerState.value.copy(
                        items = items,
                        isLoading = false,
                        errorMessage = null
                    )
                },
                onFailure = { err ->
                    _fileExplorerState.value = _fileExplorerState.value.copy(
                        items = emptyList(),
                        isLoading = false,
                        errorMessage = err.message ?: "Unable to read project files."
                    )
                }
            )
        }
    }

    fun openFilePreview(context: Context, project: Project, relativePath: String) {
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                val projectDir = resolveProjectDirectory(context, project)
                    ?: return@withContext FilePreviewContent.Unsupported(relativePath, "Project directory missing.")
                ProjectFileManager.readFilePreview(projectDir, relativePath).getOrElse { err ->
                    FilePreviewContent.Unsupported(relativePath, err.message ?: "Unable to open file.")
                }
            }
            _filePreviewContent.value = preview
        }
    }

    fun closeFilePreview() {
        _filePreviewContent.value = null
    }

    fun navigateToFolder(context: Context, project: Project, relativePath: String) {
        _fileExplorerState.value = _fileExplorerState.value.copy(
            searchQuery = "",
            selectedCategory = FileCategory.ALL
        )
        loadProjectFiles(context, project, relativePath)
    }

    fun navigateUp(context: Context, project: Project) {
        val current = _fileExplorerState.value.currentRelativePath
        if (current.isBlank()) return
        val parent = current.substringBeforeLast('/', "")
        loadProjectFiles(context, project, parent)
    }

    fun setFileSearchQuery(context: Context, project: Project, query: String) {
        _fileExplorerState.value = _fileExplorerState.value.copy(searchQuery = query)
        loadProjectFiles(context, project, _fileExplorerState.value.currentRelativePath)
    }

    fun setFileCategoryFilter(context: Context, project: Project, category: FileCategory) {
        _fileExplorerState.value = _fileExplorerState.value.copy(selectedCategory = category)
        loadProjectFiles(context, project, _fileExplorerState.value.currentRelativePath)
    }

    fun createFolder(context: Context, project: Project, folderName: String) {
        viewModelScope.launch {
            operationMutex.withLock {
                val currentPath = _fileExplorerState.value.currentRelativePath
                val result = withContext(Dispatchers.IO) {
                    val projectDir = resolveProjectDirectory(context, project)
                        ?: return@withContext Result.failure<ProjectFileItem>(IllegalStateException("Project directory missing."))
                    val res = ProjectFileManager.createFolder(projectDir, currentPath, folderName)
                    if (res.isSuccess) syncStatsAfterFileChange(context, project, projectDir)
                    res
                }
                result.fold(
                    onSuccess = { item ->
                        _operationState.value = ProjectOperationState.Success("Created folder '${item.name}'")
                        loadProjectFiles(context, project, currentPath)
                    },
                    onFailure = { err ->
                        _operationState.value = ProjectOperationState.Error(err.message ?: "Could not create folder.")
                    }
                )
            }
        }
    }

    fun createEmptyFile(context: Context, project: Project, fileName: String) {
        viewModelScope.launch {
            operationMutex.withLock {
                val currentPath = _fileExplorerState.value.currentRelativePath
                val result = withContext(Dispatchers.IO) {
                    val projectDir = resolveProjectDirectory(context, project)
                        ?: return@withContext Result.failure<ProjectFileItem>(IllegalStateException("Project directory missing."))
                    val res = ProjectFileManager.createEmptyFile(projectDir, currentPath, fileName)
                    if (res.isSuccess) syncStatsAfterFileChange(context, project, projectDir)
                    res
                }
                result.fold(
                    onSuccess = { item ->
                        _operationState.value = ProjectOperationState.Success("Created file '${item.name}'")
                        loadProjectFiles(context, project, currentPath)
                    },
                    onFailure = { err ->
                        _operationState.value = ProjectOperationState.Error(err.message ?: "Could not create file.")
                    }
                )
            }
        }
    }

    fun renameFileOrFolder(context: Context, project: Project, itemRelativePath: String, newName: String) {
        viewModelScope.launch {
            operationMutex.withLock {
                val currentPath = _fileExplorerState.value.currentRelativePath
                val result = withContext(Dispatchers.IO) {
                    val projectDir = resolveProjectDirectory(context, project)
                        ?: return@withContext Result.failure<ProjectFileItem>(IllegalStateException("Project directory missing."))
                    val res = ProjectFileManager.renameItem(projectDir, itemRelativePath, newName)
                    if (res.isSuccess) syncStatsAfterFileChange(context, project, projectDir)
                    res
                }
                result.fold(
                    onSuccess = { item ->
                        _operationState.value = ProjectOperationState.Success("Renamed to '${item.name}'")
                        loadProjectFiles(context, project, currentPath)
                    },
                    onFailure = { err ->
                        _operationState.value = ProjectOperationState.Error(err.message ?: "Could not rename item.")
                    }
                )
            }
        }
    }

    fun deleteFileOrFolder(context: Context, project: Project, itemRelativePath: String) {
        viewModelScope.launch {
            operationMutex.withLock {
                val currentPath = _fileExplorerState.value.currentRelativePath
                val result = withContext(Dispatchers.IO) {
                    val projectDir = resolveProjectDirectory(context, project)
                        ?: return@withContext Result.failure<Unit>(IllegalStateException("Project directory missing."))
                    val res = ProjectFileManager.deleteItem(projectDir, itemRelativePath)
                    if (res.isSuccess) syncStatsAfterFileChange(context, project, projectDir)
                    res
                }
                result.fold(
                    onSuccess = {
                        _operationState.value = ProjectOperationState.Success("Deleted item successfully")
                        loadProjectFiles(context, project, currentPath)
                    },
                    onFailure = { err ->
                        _operationState.value = ProjectOperationState.Error(err.message ?: "Could not delete item.")
                    }
                )
            }
        }
    }

    private suspend fun syncStatsAfterFileChange(context: Context, project: Project, projectDir: File) {
        val (count, bytes) = ProjectStorageManager.calculateStats(projectDir)
        projectRepository.updateProjectStats(project.id, count, bytes)
        val updated = projectRepository.getProjectById(project.id)
        if (updated != null) {
            ProjectStorageManager.writeMetadata(projectDir, updated)
            val health = ProjectHealthChecker.checkProjectHealth(context, updated)
            projectRepository.updateHealthStatus(
                id = updated.id,
                status = health.status,
                message = health.diagnostics.joinToString("; "),
                missingLocalAssets = health.missingLocalAssets
            )
            _currentHealthReport.value = health
            _htmlEntryCandidates.value = ProjectFileManager.findHtmlEntryCandidates(projectDir)
        }
    }

    private fun resolveProjectDirectory(context: Context?, project: Project): File? {
        if (project.internalProjectPath.isNotBlank()) {
            val f = File(project.internalProjectPath)
            if (f.exists() && f.isDirectory) return f
        }
        if (context != null && project.projectUuid.isNotBlank()) {
            return File(ProjectStorageManager.getProjectsRoot(context), project.projectUuid)
        }
        return null
    }

    suspend fun getProject(id: Long): Project? {
        return projectRepository.getProjectById(id)
    }

    val buildServerUrl: StateFlow<String> = preferencesRepository.buildServerUrlFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    fun setBuildServerUrl(url: String) {
        viewModelScope.launch {
            preferencesRepository.setBuildServerUrl(url)
        }
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MfbViewModel::class.java)) {
                        return MfbViewModel(
                            preferencesRepository = appContainer.userPreferencesRepository,
                            projectRepository = appContainer.projectRepository,
                            buildRepository = appContainer.buildRepository,
                            changelogRepository = appContainer.changelogRepository,
                            appContext = appContainer.appContext
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
                }
            }
    }
}
