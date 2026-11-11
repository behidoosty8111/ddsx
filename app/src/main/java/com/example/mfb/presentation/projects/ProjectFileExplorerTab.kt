package com.example.mfb.presentation.projects

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Javascript
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mfb.data.editor.EditorSessionTracker
import com.example.mfb.data.engine.EditorFileManager
import com.example.mfb.domain.model.FileCategory
import com.example.mfb.domain.model.FileExplorerUiState
import com.example.mfb.domain.model.FilePreviewContent
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.ProjectFileItem
import com.example.mfb.presentation.viewmodel.MfbViewModel
import com.example.mfb.ui.components.MfbEmptyState
import com.example.mfb.ui.components.TechBadge
import com.example.mfb.ui.components.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectFileExplorerSection(
    project: Project,
    explorerState: FileExplorerUiState,
    viewModel: MfbViewModel,
    context: Context,
    onOpenFileInEditor: (String) -> Unit = {},
    onOpenLivePreview: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val filePreview by viewModel.filePreviewContent.collectAsStateWithLifecycle()
    val openFilesMap by EditorSessionTracker.openFilesByProject.collectAsStateWithLifecycle()
    val dirtyFilesMap by EditorSessionTracker.dirtyFilesByProject.collectAsStateWithLifecycle()
    val openFiles = openFilesMap[project.id].orEmpty()
    val dirtyFiles = dirtyFilesMap[project.id].orEmpty()
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<ProjectFileItem?>(null) }
    var itemToDelete by remember { mutableStateOf<ProjectFileItem?>(null) }
    var itemForInfo by remember { mutableStateOf<ProjectFileItem?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Action Toolbar: Create Folder, Create File, Refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { showCreateFolderDialog = true },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("create_folder_button")
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            OutlinedButton(
                onClick = { showCreateFileDialog = true },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("create_file_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New File", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            IconButton(
                onClick = { viewModel.loadProjectFiles(context, project, explorerState.currentRelativePath) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
                    .testTag("refresh_files_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh files", modifier = Modifier.size(18.dp))
            }
        }

        // Search inside project
        OutlinedTextField(
            value = explorerState.searchQuery,
            onValueChange = { viewModel.setFileSearchQuery(context, project, it) },
            placeholder = { Text("Search files in project...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (explorerState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setFileSearchQuery(context, project, "") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("file_explorer_search")
        )

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FileCategory.entries.forEach { category ->
                FilterChip(
                    selected = explorerState.selectedCategory == category,
                    onClick = { viewModel.setFileCategoryFilter(context, project, category) },
                    label = { Text(category.label) }
                )
            }
        }

        // Breadcrumb & Back Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (explorerState.currentRelativePath.isNotBlank()) {
                IconButton(
                    onClick = { viewModel.navigateUp(context, project) },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("file_explorer_up_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Parent folder",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "root",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { viewModel.navigateToFolder(context, project, "") }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                val segments = explorerState.currentRelativePath.split('/').filter { it.isNotBlank() }
                var cumulative = ""
                segments.forEach { seg ->
                    cumulative = if (cumulative.isEmpty()) seg else "$cumulative/$seg"
                    val targetPath = cumulative
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = seg,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { viewModel.navigateToFolder(context, project, targetPath) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // File List Content
        when {
            explorerState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            explorerState.errorMessage != null -> {
                MfbEmptyState(
                    icon = Icons.Default.ErrorOutline,
                    title = "Directory Unavailable",
                    supportingText = explorerState.errorMessage
                )
            }

            explorerState.items.isEmpty() -> {
                MfbEmptyState(
                    icon = Icons.Default.Folder,
                    title = "Empty Folder",
                    supportingText = if (explorerState.searchQuery.isNotBlank() || explorerState.selectedCategory != FileCategory.ALL) {
                        "No files match the current search or category filter."
                    } else {
                        "Create a new file or folder to organize this directory."
                    }
                )
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    explorerState.items.forEach { item ->
                        val isEditable = !item.isDirectory && EditorFileManager.isEditableFile(item.name)
                        val isOpenInEditor = !item.isDirectory && item.relativePath in openFiles
                        val isDirtyInEditor = !item.isDirectory && item.relativePath in dirtyFiles
                        val isHtml = !item.isDirectory && (item.extension.equals("html", ignoreCase = true) || item.extension.equals("htm", ignoreCase = true))
                        FileExplorerItemRow(
                            item = item,
                            isEditable = isEditable,
                            isHtml = isHtml,
                            isOpenInEditor = isOpenInEditor,
                            isDirtyInEditor = isDirtyInEditor,
                            onClick = {
                                if (item.isDirectory) {
                                    viewModel.navigateToFolder(context, project, item.relativePath)
                                } else if (isEditable) {
                                    onOpenFileInEditor(item.relativePath)
                                } else {
                                    viewModel.openFilePreview(context, project, item.relativePath)
                                }
                            },
                            onOpenInEditor = { onOpenFileInEditor(item.relativePath) },
                            onOpenLivePreview = { onOpenLivePreview(item.relativePath) },
                            onPreview = { viewModel.openFilePreview(context, project, item.relativePath) },
                            onCopyPath = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Relative Path", item.relativePath))
                                Toast.makeText(context, "Copied: ${item.relativePath}", Toast.LENGTH_SHORT).show()
                            },
                            onInfo = { itemForInfo = item },
                            onRename = { itemToRename = item },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }
            }
        }
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a name for the new folder:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        placeholder = { Text("e.g. assets") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_folder_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createFolder(context, project, folderName)
                        showCreateFolderDialog = false
                    },
                    enabled = folderName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_create_folder_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create Empty File Dialog
    if (showCreateFileDialog) {
        var fileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create Empty File", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Supported extensions: .html, .htm, .css, .js, .json, .txt, .xml, .md",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        placeholder = { Text("e.g. style.css") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_file_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createEmptyFile(context, project, fileName)
                        showCreateFileDialog = false
                    },
                    enabled = fileName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_create_file_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename File/Folder Dialog
    itemToRename?.let { target ->
        var newName by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename ${if (target.isDirectory) "Folder" else "File"}", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_item_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameFileOrFolder(context, project, target.relativePath, newName)
                        itemToRename = null
                    },
                    enabled = newName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_rename_item_button")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete File/Folder Confirmation Dialog
    itemToDelete?.let { target ->
        val isDirtyInEditor = !target.isDirectory && target.relativePath in dirtyFiles
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    text = "Delete ${if (target.isDirectory) "Folder" else "File"}?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = when {
                        target.isDirectory -> "Are you sure you want to delete folder '${target.name}'? All files and subfolders inside it will also be permanently deleted."
                        isDirtyInEditor -> "WARNING: '${target.name}' currently has unsaved changes in the Code Editor! Deleting it will permanently discard those changes."
                        else -> "Are you sure you want to permanently delete '${target.name}'?"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFileOrFolder(context, project, target.relativePath)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_item_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // File Info Dialog
    itemForInfo?.let { info ->
        val dateStr = SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault())
            .format(Date(info.lastModified))
        AlertDialog(
            onDismissRequest = { itemForInfo = null },
            title = { Text(info.name, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoLine("Type", if (info.isDirectory) "Folder" else "${info.category.label} (.${info.extension.ifEmpty { "none" }})")
                    InfoLine("Relative Path", info.relativePath)
                    if (info.isDirectory) {
                        InfoLine("Items", "${info.childCount} item(s)")
                    } else {
                        InfoLine("Size", formatBytes(info.sizeBytes))
                    }
                    InfoLine("Last Modified", dateStr)
                }
            },
            confirmButton = {
                TextButton(onClick = { itemForInfo = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Read-Only File Preview Dialog
    filePreview?.let { preview ->
        FilePreviewDialog(
            preview = preview,
            onDismiss = { viewModel.closeFilePreview() }
        )
    }
}

@Composable
private fun FilePreviewDialog(
    preview: FilePreviewContent,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = when (preview) {
                is FilePreviewContent.TextPreview -> preview.name
                is FilePreviewContent.ImagePreview -> preview.name
                is FilePreviewContent.Unsupported -> preview.name
            }
            Text(text = title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        text = {
            when (preview) {
                is FilePreviewContent.TextPreview -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Read-Only Preview • ${preview.relativePath}" +
                                if (preview.truncated) " (truncated)" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 360.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp)
                        ) {
                            Text(
                                text = preview.content.ifEmpty { "(Empty file)" },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                is FilePreviewContent.ImagePreview -> {
                    val bitmap = remember(preview.absolutePath) {
                        val opts = BitmapFactory.Options().apply {
                            val maxDim = 800
                            var sample = 1
                            while ((preview.width / sample) > maxDim || (preview.height / sample) > maxDim) {
                                sample *= 2
                            }
                            inSampleSize = sample
                        }
                        BitmapFactory.decodeFile(preview.absolutePath, opts)?.asImageBitmap()
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${preview.width} × ${preview.height} px • ${preview.relativePath}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = preview.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Text("Unable to render image preview.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                is FilePreviewContent.Unsupported -> {
                    Text(
                        text = preview.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun FileExplorerItemRow(
    item: ProjectFileItem,
    isEditable: Boolean,
    isHtml: Boolean,
    isOpenInEditor: Boolean,
    isDirtyInEditor: Boolean,
    onClick: () -> Unit,
    onOpenInEditor: () -> Unit,
    onOpenLivePreview: () -> Unit,
    onPreview: () -> Unit,
    onCopyPath: () -> Unit,
    onInfo: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag("file_item_${item.name}")
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconForFileItem(item),
            contentDescription = null,
            tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isDirtyInEditor) {
                    TechBadge(
                        text = "MODIFIED",
                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                        contentColor = MaterialTheme.colorScheme.tertiary
                    )
                } else if (isOpenInEditor) {
                    TechBadge(
                        text = "OPEN",
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = if (item.isDirectory) {
                    "${item.childCount} item${if (item.childCount != 1) "s" else ""} • ${item.relativePath}"
                } else {
                    "${formatBytes(item.sizeBytes)} • ${item.relativePath}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.testTag("file_menu_${item.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "File options"
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (!item.isDirectory && isHtml) {
                    DropdownMenuItem(
                        text = { Text("Live Preview") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onOpenLivePreview()
                        }
                    )
                }
                if (!item.isDirectory && isEditable) {
                    DropdownMenuItem(
                        text = { Text("Open in Code Editor") },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onOpenInEditor()
                        }
                    )
                }
                if (!item.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("Preview (Read-Only)") },
                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onPreview()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Copy Relative Path") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onCopyPath()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Info") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onInfo()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private fun iconForFileItem(item: ProjectFileItem): ImageVector {
    if (item.isDirectory) return Icons.Default.Folder
    return when (item.category) {
        FileCategory.HTML -> Icons.Default.Code
        FileCategory.STYLES -> Icons.Default.Style
        FileCategory.SCRIPTS -> Icons.Default.Javascript
        FileCategory.IMAGES -> Icons.Default.Image
        FileCategory.FONTS -> Icons.Default.FontDownload
        FileCategory.DATA -> Icons.Default.DataObject
        FileCategory.TEXT -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
