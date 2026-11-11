package com.example.mfb.presentation.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mfb.domain.model.ConsoleLogSeverity
import com.example.mfb.domain.model.PendingExternalNavigation
import com.example.mfb.domain.model.PreviewLoadState
import com.example.mfb.domain.model.PreviewLogEntry
import com.example.mfb.domain.model.PreviewSettings
import com.example.mfb.domain.model.ViewportPreset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PreviewToolbar(
    projectName: String,
    currentFileLabel: String,
    loadState: PreviewLoadState,
    canGoBack: Boolean,
    canGoForward: Boolean,
    selectedViewport: ViewportPreset,
    errorCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefreshOrStop: () -> Unit,
    onReloadEntryPoint: () -> Unit,
    onSelectViewport: (ViewportPreset) -> Unit,
    onToggleFullScreen: () -> Unit,
    onToggleConsole: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var viewportMenuExpanded by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val isLoading = loadState is PreviewLoadState.LoadingPage ||
        loadState is PreviewLoadState.PreparingProject ||
        loadState is PreviewLoadState.Rendering

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("preview_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (canGoBack) "WebView Back" else "Navigate Back"
                    )
                }

                IconButton(
                    onClick = onForward,
                    enabled = canGoForward,
                    modifier = Modifier.testTag("preview_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "WebView Forward"
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = currentFileLabel.ifBlank { "Live Preview" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("preview_current_file")
                    )
                    val subtitle = when (loadState) {
                        is PreviewLoadState.PreparingProject -> "$projectName • Preparing..."
                        is PreviewLoadState.LoadingPage -> "$projectName • Loading ${loadState.progress}%"
                        is PreviewLoadState.Rendering -> "$projectName • Rendering..."
                        is PreviewLoadState.Loaded -> "$projectName • Loaded"
                        is PreviewLoadState.Failed -> "$projectName • Failed"
                        is PreviewLoadState.Cancelled -> "$projectName • Cancelled"
                        is PreviewLoadState.Idle -> projectName
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (loadState is PreviewLoadState.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onRefreshOrStop,
                    modifier = Modifier.testTag("preview_refresh_stop_button")
                ) {
                    Icon(
                        imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (isLoading) "Stop loading" else "Refresh preview"
                    )
                }

                Box {
                    IconButton(
                        onClick = { viewportMenuExpanded = true },
                        modifier = Modifier.testTag("preview_viewport_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = "Viewport mode: ${selectedViewport.label}",
                            tint = if (selectedViewport != ViewportPreset.RESPONSIVE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = viewportMenuExpanded,
                        onDismissRequest = { viewportMenuExpanded = false }
                    ) {
                        ViewportPreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = preset.label,
                                        fontWeight = if (preset == selectedViewport) FontWeight.Bold else FontWeight.Normal,
                                        color = if (preset == selectedViewport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewportMenuExpanded = false
                                    onSelectViewport(preset)
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleConsole,
                    modifier = Modifier.testTag("preview_console_button")
                ) {
                    Box {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Console & Error Monitor",
                            tint = if (errorCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        if (errorCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { moreMenuExpanded = true },
                        modifier = Modifier.testTag("preview_more_menu_button")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More preview options")
                    }
                    DropdownMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = { moreMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Forward") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                            enabled = canGoForward,
                            onClick = {
                                moreMenuExpanded = false
                                onForward()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reload Entry Point") },
                            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                            onClick = {
                                moreMenuExpanded = false
                                onReloadEntryPoint()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Full-Screen Mode") },
                            leadingIcon = { Icon(Icons.Default.Fullscreen, contentDescription = null) },
                            onClick = {
                                moreMenuExpanded = false
                                onToggleFullScreen()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Preview Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                moreMenuExpanded = false
                                onOpenSettings()
                            }
                        )
                    }
                }
            }

            if (isLoading) {
                val progressVal = (loadState as? PreviewLoadState.LoadingPage)?.progress?.div(100f)
                if (progressVal != null && progressVal > 0f) {
                    LinearProgressIndicator(
                        progress = { progressVal },
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                    )
                }
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }
    }
}

@Composable
fun PreviewViewportZoomBar(
    selectedViewport: ViewportPreset,
    zoomPercent: Int,
    zoomControlsEnabled: Boolean,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onZoomIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val dimLabel = if (selectedViewport.widthDp != null && selectedViewport.heightDp != null) {
                "${selectedViewport.label} CSS"
            } else {
                "Responsive (Full Width)"
            }
            Text(
                text = dimLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("preview_viewport_label")
            )

            if (zoomControlsEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onZoomOut,
                        enabled = zoomPercent > 50,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("preview_zoom_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = "Zoom out",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    TextButton(
                        onClick = onResetZoom,
                        modifier = Modifier.testTag("preview_zoom_reset_button")
                    ) {
                        Text(
                            text = "${zoomPercent}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = onZoomIn,
                        enabled = zoomPercent < 200,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("preview_zoom_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Zoom in",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PreviewConsolePanel(
    logs: List<PreviewLogEntry>,
    isExpanded: Boolean,
    errorCount: Int,
    warningCount: Int,
    onToggleExpand: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("preview_console_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (errorCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Console ($errorCount err, $warningCount warn)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isExpanded && logs.isNotEmpty()) {
                        IconButton(
                            onClick = onCopyLogs,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("preview_copy_logs_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy error details", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onClearLogs,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("preview_clear_logs_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear errors", modifier = Modifier.size(18.dp))
                        }
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = if (isExpanded) "Collapse console" else "Expand console"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No console messages or errors recorded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(logs, key = { it.id }) { log ->
                            val tint = when (log.severity) {
                                ConsoleLogSeverity.ERROR -> MaterialTheme.colorScheme.error
                                ConsoleLogSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                                ConsoleLogSeverity.INFO -> MaterialTheme.colorScheme.secondary
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(0.5.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${log.type.label} • ${log.safeResourceRef}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = tint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = dateFormat.format(Date(log.timestampMs)),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = log.message,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreviewSettingsDialog(
    settings: PreviewSettings,
    onUpdateSettings: ((PreviewSettings) -> PreviewSettings) -> Unit,
    onRequestClearProjectStorage: () -> Unit,
    onDismiss: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Live Preview Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PreviewToggleRow(
                    title = "Enable JavaScript",
                    subtitle = "Required for interactive web applications",
                    checked = settings.javaScriptEnabled,
                    testTag = "preview_setting_js",
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(javaScriptEnabled = checked) } }
                )

                PreviewToggleRow(
                    title = "Allow External Network Access",
                    subtitle = "WARNING: Allows remote CDNs/scripts to contact external servers. Disabled by default for security.",
                    checked = settings.externalNetworkEnabled,
                    testTag = "preview_setting_external_network",
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(externalNetworkEnabled = checked) } }
                )

                PreviewToggleRow(
                    title = "Desktop User-Agent Mode",
                    subtitle = "Request desktop-style layouts from responsive scripts",
                    checked = settings.desktopViewportMode,
                    testTag = "preview_setting_desktop_ua",
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(desktopViewportMode = checked) } }
                )

                PreviewToggleRow(
                    title = "Pinch-to-Zoom Controls",
                    subtitle = "Allow zooming inside the preview viewport",
                    checked = settings.zoomControlsEnabled,
                    testTag = "preview_setting_zoom",
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(zoomControlsEnabled = checked) } }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Text(
                    text = "Default Viewport Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Column {
                    ViewportPreset.entries.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onUpdateSettings { it.copy(defaultViewportPreset = preset) } }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.defaultViewportPreset == preset,
                                onClick = { onUpdateSettings { it.copy(defaultViewportPreset = preset) } }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = preset.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("preview_clear_storage_button")
                ) {
                    Text("Clear This Project's Web Storage")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Project Web Storage?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This clears localStorage and sessionStorage saved by this project's isolated preview origin. Other projects' storage and your project source files are unaffected."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onRequestClearProjectStorage()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Clear Storage")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PreviewToggleRow(
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
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun ExternalNavigationDialog(
    pending: PendingExternalNavigation,
    onConfirmOpen: (PendingExternalNavigation) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text("External Destination", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (pending.isSpecialScheme) {
                        "This page requested to open an external ${pending.scheme.uppercase()} handler:"
                    } else {
                        "This link points to an external website outside your MFB project:"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = pending.url,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmOpen(pending) }) {
                Text("Open Externally")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
