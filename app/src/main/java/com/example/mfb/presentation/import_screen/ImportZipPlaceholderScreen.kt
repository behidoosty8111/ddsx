package com.example.mfb.presentation.import_screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mfb.core.security.ZipSecurityValidator
import com.example.mfb.domain.model.Project
import com.example.mfb.presentation.viewmodel.ImportUiState
import com.example.mfb.presentation.viewmodel.MfbViewModel
import com.example.mfb.ui.components.MfbTopBar
import com.example.mfb.ui.components.TechBadge
import com.example.mfb.ui.components.formatBytes

@Composable
fun ImportZipPlaceholderScreen(
    onNavigateBack: () -> Unit,
    viewModel: MfbViewModel? = null,
    onNavigateToProjects: () -> Unit = onNavigateBack,
    onNavigateToProjectDetails: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val importState = viewModel?.importUiState?.collectAsStateWithLifecycle()?.value ?: ImportUiState.Idle
    var pickerStatusMessage by remember { mutableStateOf<String?>(null) }

    val fallbackGetContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && viewModel != null) {
            android.util.Log.d("MfbZipImport", "Fallback GetContent returned uri=$uri")
            pickerStatusMessage = null
            viewModel.importZip(context, uri)
        } else {
            android.util.Log.d("MfbZipImport", "Fallback GetContent cancelled by user")
            pickerStatusMessage = "File selection cancelled."
        }
    }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && viewModel != null) {
            android.util.Log.d("MfbZipImport", "OpenDocument returned uri=$uri")
            pickerStatusMessage = null
            viewModel.importZip(context, uri)
        } else {
            android.util.Log.d("MfbZipImport", "OpenDocument cancelled by user")
            pickerStatusMessage = "File selection cancelled."
        }
    }

    Scaffold(
        topBar = {
            MfbTopBar(
                title = "Import ZIP Package",
                canNavigateBack = true,
                onNavigateBack = {
                    viewModel?.resetImportState()
                    onNavigateBack()
                }
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
                    .testTag("import_zip_placeholder_screen"),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header & Action Card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(22.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(14.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderZip,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ZIP Extraction Engine",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    TechBadge(
                                        text = "PHASE 02 ACTIVE",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Select a .zip package containing your HTML, CSS, JavaScript, fonts, and images. The engine extracts web assets into private internal storage with sandbox security.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Interactive SAF Button with GetContent fallback
                            Button(
                                onClick = {
                                    android.util.Log.d("MfbZipImport", "Select ZIP Archive button tapped")
                                    pickerStatusMessage = "Opening file picker..."
                                    try {
                                        zipPickerLauncher.launch(
                                            arrayOf(
                                                "application/zip",
                                                "application/x-zip-compressed",
                                                "application/octet-stream",
                                                "*/*"
                                            )
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.w("MfbZipImport", "OpenDocument failed, trying GetContent fallback", e)
                                        try {
                                            fallbackGetContentLauncher.launch("application/zip")
                                        } catch (fallbackErr: Exception) {
                                            android.util.Log.e("MfbZipImport", "All file pickers failed", fallbackErr)
                                            pickerStatusMessage = null
                                            viewModel?.reportImportError(
                                                "Unable to open file picker: ${fallbackErr.localizedMessage ?: "No document provider found"}"
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("select_zip_file_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Select ZIP Archive",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            pickerStatusMessage?.let { status ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("zip_picker_status_text")
                                )
                            }
                        }
                    }
                }

                // Security & Sandbox Guarantees
                item {
                    Text(
                        text = "Security & Storage Architecture",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                item {
                    SecuritySpecItem(
                        title = "Zip Slip Directory Traversal Defense",
                        desc = "Strict canonical destination validation prevents extraction outside the private project sandbox."
                    )
                }

                item {
                    SecuritySpecItem(
                        title = "Zip Bomb & Decompression Quotas",
                        desc = "Guards against compression bombs (max ratio 100:1, max extracted size 250 MB, max 5,000 entries)."
                    )
                }

                item {
                    SecuritySpecItem(
                        title = "Zero-Permission SAF Streaming",
                        desc = "Operates exclusively via Android Storage Access Framework with zero broad storage permissions."
                    )
                }

                item {
                    SecuritySpecItem(
                        title = "Automated HTML Entry Point Detection",
                        desc = "Discovers index.html, index.htm, or prompts selection if multiple entry points exist."
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("zip_placeholder_back_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Back to Import Options",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // 1. Progress Dialog
    if (importState is ImportUiState.Progress) {
        AlertDialog(
            onDismissRequest = { /* Modal while extracting */ },
            title = {
                Text(
                    text = "Importing Archive...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { importState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = "${(importState.progress * 100).toInt()}% • ${importState.statusMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {}
        )
    }

    // 2. Candidate Selection Dialog
    if (importState is ImportUiState.NeedsEntryPointChoice) {
        var selectedCandidate by remember {
            mutableStateOf(importState.candidates.firstOrNull() ?: "index.html")
        }

        AlertDialog(
            onDismissRequest = { viewModel?.resetImportState() },
            title = {
                Text(
                    text = "Select HTML Entry Point",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Multiple HTML files were discovered in this archive without a clear default index.html. Choose the main entry point for your application:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    importState.candidates.forEach { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedCandidate = candidate }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (candidate == selectedCandidate),
                                onClick = { selectedCandidate = candidate }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = candidate,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (candidate == selectedCandidate) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel?.completeZipWithEntryPoint(
                            context = context,
                            tempDirName = importState.tempDirName,
                            selectedEntryPoint = selectedCandidate,
                            projectName = importState.suggestedName,
                            originalFileName = importState.originalFileName
                        )
                    },
                    modifier = Modifier.testTag("confirm_entry_point_button")
                ) {
                    Text(text = "Confirm Entry Point")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel?.resetImportState() }) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    // 3. Success Dialog
    if (importState is ImportUiState.Success) {
        val proj = importState.project
        AlertDialog(
            onDismissRequest = {
                viewModel?.resetImportState()
                onNavigateToProjects()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Project Imported Successfully!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Project Name: ${proj.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Entry Point: ${proj.mainHtmlRelativePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Files Extracted: ${proj.fileCount} files (${formatBytes(proj.totalSizeBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Storage: ${proj.internalProjectPath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel?.resetImportState()
                        onNavigateToProjectDetails(proj.id)
                    },
                    modifier = Modifier.testTag("import_success_details_button")
                ) {
                    Text(text = "View Project Details")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel?.resetImportState()
                        onNavigateToProjects()
                    }
                ) {
                    Text(text = "Go to Projects")
                }
            }
        )
    }

    // 4. Error Dialog
    if (importState is ImportUiState.Error) {
        AlertDialog(
            onDismissRequest = { viewModel?.resetImportState() },
            icon = {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Import Failed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = importState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel?.resetImportState() },
                    modifier = Modifier.testTag("import_error_dismiss_button")
                ) {
                    Text(text = "Dismiss")
                }
            }
        )
    }
}

@Composable
private fun SecuritySpecItem(
    title: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
