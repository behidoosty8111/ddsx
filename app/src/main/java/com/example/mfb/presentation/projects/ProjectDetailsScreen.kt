package com.example.mfb.presentation.projects

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.ProjectHealthReport
import com.example.mfb.domain.model.ProjectOperationState
import com.example.mfb.domain.model.SourceType
import com.example.mfb.presentation.viewmodel.MfbViewModel
import com.example.mfb.ui.components.HealthStatusBadge
import com.example.mfb.ui.components.MfbTopBar
import com.example.mfb.ui.components.TechBadge
import com.example.mfb.ui.components.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class WorkspaceTab(val title: String) {
    OVERVIEW("Overview"),
    FILES("Files"),
    INFO("Info"),
    ACTIONS("Actions")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailsScreen(
    projectId: Long,
    viewModel: MfbViewModel,
    onNavigateBack: () -> Unit,
    onOpenFileInEditor: (String) -> Unit = {},
    onOpenLivePreview: (String?) -> Unit = {},
    onOpenBuildPipeline: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val project = projects.firstOrNull { it.id == projectId }
    val explorerState by viewModel.fileExplorerState.collectAsStateWithLifecycle()
    val operationState by viewModel.operationState.collectAsStateWithLifecycle()
    val healthReport by viewModel.currentHealthReport.collectAsStateWithLifecycle()
    val htmlCandidates by viewModel.htmlEntryCandidates.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var showEditDetailsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEntryPointDialog by remember { mutableStateOf(false) }

    val zipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && project != null) {
            viewModel.exportProjectAsZip(context, project, uri)
        }
    }

    LaunchedEffect(projectId) {
        if (projectId > 0L) {
            viewModel.recordProjectOpened(projectId)
        }
    }

    LaunchedEffect(project?.id, project?.internalProjectPath) {
        if (project != null) {
            viewModel.loadProjectFiles(context, project, "")
            viewModel.refreshProjectHealth(context, project)
        }
    }

    LaunchedEffect(operationState) {
        when (val state = operationState) {
            is ProjectOperationState.Success -> {
                if (state.deletedProjectId == projectId) {
                    viewModel.clearOperationState()
                    onNavigateBack()
                } else {
                    snackbarHostState.showSnackbar(state.message)
                    viewModel.clearOperationState()
                }
            }
            is ProjectOperationState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearOperationState()
            }
            else -> {}
        }
    }

    BackHandler {
        if (selectedTab == WorkspaceTab.FILES.ordinal && explorerState.currentRelativePath.isNotBlank() && project != null) {
            viewModel.navigateUp(context, project)
        } else {
            onNavigateBack()
        }
    }

    if (project == null) {
        Scaffold(
            topBar = {
                MfbTopBar(
                    title = "Project Workspace",
                    canNavigateBack = true,
                    onNavigateBack = onNavigateBack
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = modifier
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Project not found or deleted.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val isZip = project.sourceType == SourceType.ZIP
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault()) }
    val createdDate = dateFormat.format(Date(project.createdAt))
    val modifiedDate = dateFormat.format(Date(project.updatedAt))
    val lastOpenedDate = if (project.lastOpenedAt > 0L) dateFormat.format(Date(project.lastOpenedAt)) else "First visit"

    Scaffold(
        topBar = {
            MfbTopBar(
                title = project.name,
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleFavorite(project) },
                        modifier = Modifier.testTag("workspace_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (project.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (project.isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (project.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (operationState is ProjectOperationState.InProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                WorkspaceTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = tab.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("workspace_tab_${tab.name.lowercase()}")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .testTag("project_details_screen"),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (WorkspaceTab.entries[selectedTab]) {
                    WorkspaceTab.OVERVIEW -> {
                        item {
                            WorkspaceHeaderCard(project = project, isZip = isZip)
                        }

                        item {
                            ProjectHealthCard(
                                project = project,
                                healthReport = healthReport,
                                onRefresh = { viewModel.refreshProjectHealth(context, project) }
                            )
                        }

                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Description & Tags",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        TextButton(
                                            onClick = { showEditDetailsDialog = true },
                                            modifier = Modifier.testTag("edit_metadata_button")
                                        ) {
                                            Text("Edit")
                                        }
                                    }

                                    Text(
                                        text = project.description.ifBlank { "No description added yet. Tap Edit to document this project." },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (project.description.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )

                                    if (project.tags.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            project.tags.forEach { tag ->
                                                TechBadge(
                                                    text = "#$tag",
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (project.missingLocalAssets.isNotEmpty()) {
                            item {
                                MissingAssetsWarningCard(missingAssets = project.missingLocalAssets)
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { selectedTab = WorkspaceTab.FILES.ordinal },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Browse Files (${project.fileCount})")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val safeName = project.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                        zipExportLauncher.launch("${safeName}.zip")
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Export ZIP")
                                }
                            }
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onOpenBuildPipeline,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("open_build_pipeline_button")
                                ) {
                                    Text("APK Build Pipeline (${project.projectCategory.badgeLabel})")
                                }

                                if (project.projectCategory.supportsLivePreview && project.mainHtmlRelativePath.isNotBlank()) {
                                    Button(
                                        onClick = { onOpenLivePreview(project.mainHtmlRelativePath) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.onSecondary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("open_live_preview_button")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Live Preview (${project.mainHtmlRelativePath})")
                                    }
                                }

                                if (project.mainHtmlRelativePath.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { onOpenFileInEditor(project.mainHtmlRelativePath) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("open_entry_in_editor_button")
                                    ) {
                                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Edit ${project.mainHtmlRelativePath} in Code Editor")
                                    }
                                }
                            }
                        }
                    }

                    WorkspaceTab.FILES -> {
                        item {
                            ProjectFileExplorerSection(
                                project = project,
                                explorerState = explorerState,
                                viewModel = viewModel,
                                context = context,
                                onOpenFileInEditor = onOpenFileInEditor,
                                onOpenLivePreview = { htmlRelPath -> onOpenLivePreview(htmlRelPath) }
                            )
                        }
                    }

                    WorkspaceTab.INFO -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(18.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    SpecRow(label = "Project ID", value = "#${project.id}")
                                    SpecRow(label = "Project UUID", value = project.projectUuid.ifBlank { "N/A" })
                                    SpecRow(label = "Source Type", value = project.sourceType.name)
                                    SpecRow(label = "Main HTML Entry", value = project.mainHtmlRelativePath)
                                    SpecRow(label = "Original File", value = project.originalFileName.ifBlank { "N/A" })
                                    SpecRow(label = "Total Files", value = "${project.fileCount} file(s)")
                                    SpecRow(label = "Total Storage Size", value = formatBytes(project.totalSizeBytes))
                                    SpecRow(label = "Created At", value = createdDate)
                                    SpecRow(label = "Last Modified", value = modifiedDate)
                                    SpecRow(label = "Last Opened", value = lastOpenedDate)
                                    SpecRow(label = "Internal Sandbox Path", value = project.internalProjectPath)
                                    SpecRow(label = "Package ID", value = project.packageId)
                                    SpecRow(label = "Target Version", value = "${project.versionName} (Build ${project.versionCode})")
                                }
                            }
                        }
                    }

                    WorkspaceTab.ACTIONS -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        renameInput = project.name
                                        showRenameDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("project_rename_button")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Rename Project")
                                }

                                OutlinedButton(
                                    onClick = { showEditDetailsDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("project_edit_details_button")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Edit Description & Tags")
                                }

                                OutlinedButton(
                                    onClick = { showEntryPointDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("project_change_entry_button")
                                ) {
                                    Icon(Icons.Default.Html, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Change Entry Point (${project.mainHtmlRelativePath})")
                                }

                                OutlinedButton(
                                    onClick = { viewModel.refreshProjectHealth(context, project) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("project_refresh_health_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Refresh Project Information")
                                }

                                OutlinedButton(
                                    onClick = { viewModel.duplicateProject(context, project) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("project_duplicate_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Duplicate Project")
                                }

                                Button(
                                    onClick = {
                                        val safeName = project.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                        zipExportLauncher.launch("${safeName}.zip")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("project_export_zip_button")
                                ) {
                                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Export Source as ZIP")
                                }

                                Button(
                                    onClick = { showDeleteConfirmDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("project_delete_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Delete Project Permanently")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(text = "Rename Project", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter a new visible name for this project (UUID and directory remain unchanged):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameProject(project.id, renameInput)
                            showRenameDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text(text = "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    // Edit Description & Tags Dialog
    if (showEditDetailsDialog) {
        var nameInput by remember(project) { mutableStateOf(project.name) }
        var descInput by remember(project) { mutableStateOf(project.description) }
        var tagsInput by remember(project) { mutableStateOf(project.tags.joinToString(", ")) }

        AlertDialog(
            onDismissRequest = { showEditDetailsDialog = false },
            title = { Text("Edit Project Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Project Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        label = { Text("Description") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tagsInput,
                        onValueChange = { tagsInput = it },
                        label = { Text("Tags (comma separated)") },
                        placeholder = { Text("e.g. portfolio, game, pwa") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedTags = tagsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        viewModel.updateProjectMetadata(project.id, nameInput, descInput, parsedTags)
                        showEditDetailsDialog = false
                    },
                    enabled = nameInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDetailsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(text = "Delete Project?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete '${project.name}'? All internal project files and its database record will be permanently removed.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProject(context, project)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text(text = "Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    // Entry Point Selection Dialog
    if (showEntryPointDialog) {
        var selectedEntry by remember(project) { mutableStateOf(project.mainHtmlRelativePath) }
        AlertDialog(
            onDismissRequest = { showEntryPointDialog = false },
            title = { Text("Select HTML Entry Point", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (htmlCandidates.isEmpty()) {
                        Text(
                            text = "No valid .html or .htm files found inside this project directory.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        htmlCandidates.forEach { candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedEntry == candidate,
                                    onClick = { selectedEntry = candidate }
                                )
                                Text(
                                    text = candidate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.changeEntryPoint(context, project, selectedEntry)
                        showEntryPointDialog = false
                    },
                    enabled = htmlCandidates.isNotEmpty() && selectedEntry != project.mainHtmlRelativePath
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEntryPointDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProjectHealthCard(
    project: Project,
    healthReport: ProjectHealthReport?,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp)
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Project Health",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    HealthStatusBadge(status = healthReport?.status ?: project.healthStatus)
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_health_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh health",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (healthReport != null) {
                Text(
                    text = "Directory: ${if (healthReport.directoryExists) "Verified" else "Missing"} • " +
                        "Entry Point: ${if (healthReport.entryPointExists && healthReport.entryPointReadable) "Readable" else "Invalid"} • " +
                        "Metadata: ${if (healthReport.metadataMatchesFiles) "Synced" else "Mismatch"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                healthReport.diagnostics.forEach { diag ->
                    Text(
                        text = "• $diag",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (project.healthMessage.isNotBlank()) {
                Text(
                    text = project.healthMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorkspaceHeaderCard(project: Project, isZip: Boolean) {
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
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isZip) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isZip) Icons.Default.FolderZip else Icons.Default.Code,
                        contentDescription = null,
                        tint = if (isZip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ID #${project.id} • Entry: ${project.mainHtmlRelativePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TechBadge(
                    text = if (isZip) "ZIP" else "HTML",
                    containerColor = if (isZip) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isZip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${project.fileCount} file(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatBytes(project.totalSizeBytes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MissingAssetsWarningCard(missingAssets: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Referenced Local Assets Not Found",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            missingAssets.take(6).forEach { asset ->
                Text(
                    text = "• $asset",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (missingAssets.size > 6) {
                Text(
                    text = "+${missingAssets.size - 6} more...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
