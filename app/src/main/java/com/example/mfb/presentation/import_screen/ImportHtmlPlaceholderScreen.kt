package com.example.mfb.presentation.import_screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mfb.presentation.viewmodel.ImportUiState
import com.example.mfb.presentation.viewmodel.MfbViewModel
import com.example.mfb.ui.components.MfbTopBar
import com.example.mfb.ui.components.TechBadge
import com.example.mfb.ui.components.formatBytes

@Composable
fun ImportHtmlPlaceholderScreen(
    onNavigateBack: () -> Unit,
    viewModel: MfbViewModel? = null,
    onNavigateToProjects: () -> Unit = onNavigateBack,
    onNavigateToProjectDetails: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val importState = viewModel?.importUiState?.collectAsStateWithLifecycle()?.value ?: ImportUiState.Idle

    val htmlPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && viewModel != null) {
            viewModel.importHtml(context, uri)
        }
    }

    Scaffold(
        topBar = {
            MfbTopBar(
                title = "Import HTML Entry",
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
                    .testTag("import_html_placeholder_screen"),
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
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(14.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "HTML5 Document Parser",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    TechBadge(
                                        text = "PHASE 02 ACTIVE",
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Import a single-file HTML5 application (.html or .htm). The engine validates document markup, inspects external local asset references, and scaffolds an isolated Android project sandbox.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    htmlPickerLauncher.launch(
                                        arrayOf("text/html", "application/xhtml+xml", "*/*")
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("select_html_file_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Select HTML File",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Specs & Verification
                item {
                    Text(
                        text = "Document Inspection & Scaffolding",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                item {
                    HtmlSpecItem(
                        title = "Document Structure Validation",
                        desc = "Verifies standard HTML tags (DOCTYPE, html, body, head) to prevent non-HTML text ingestion."
                    )
                }

                item {
                    HtmlSpecItem(
                        title = "Asset Dependency Link Scanner",
                        desc = "Scans link, script, and img tags. Alerts you if external CSS or JS files are missing from standalone import."
                    )
                }

                item {
                    HtmlSpecItem(
                        title = "Private Sandbox Isolation",
                        desc = "Saved to internal app storage (filesDir/projects/{uuid}/index.html) with complete persistence."
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("html_placeholder_back_button"),
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
            onDismissRequest = { /* Modal */ },
            title = {
                Text(
                    text = "Importing HTML Document...",
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

    // 2. Success Dialog
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
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "HTML Project Imported!",
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
                        text = "Size: ${formatBytes(proj.totalSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Storage: ${proj.internalProjectPath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    if (importState.warnings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                                .padding(8.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${importState.warnings.size} Missing Local Asset(s)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    text = "Your HTML references relative assets (e.g. ${importState.warnings.first()}). Use 'Import ZIP' to bundle stylesheets and scripts.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel?.resetImportState()
                        onNavigateToProjectDetails(proj.id)
                    },
                    modifier = Modifier.testTag("import_html_success_details_button")
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

    // 3. Error Dialog
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
                    text = "HTML Import Failed",
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
                    modifier = Modifier.testTag("import_html_error_dismiss_button")
                ) {
                    Text(text = "Dismiss")
                }
            }
        )
    }
}

@Composable
private fun HtmlSpecItem(
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
