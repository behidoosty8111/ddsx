package com.example.mfb.presentation.dashboard

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.mfb.presentation.changelog.WhatsNewDialog
import com.example.mfb.presentation.viewmodel.MfbViewModel
import com.example.mfb.ui.components.MfbEmptyState
import com.example.mfb.ui.components.SectionHeader
import com.example.mfb.ui.components.TechBadge

@Composable
fun DashboardScreen(
    viewModel: MfbViewModel,
    onNavigateToImport: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToTemplates: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProjectDetails: (Long) -> Unit = {},
    onNavigateToUpdateLog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val recentBuilds by viewModel.recentBuilds.collectAsStateWithLifecycle()
    val whatsNewRelease by viewModel.whatsNewRelease.collectAsStateWithLifecycle()

    whatsNewRelease?.let { release ->
        WhatsNewDialog(
            release = release,
            onDismiss = { viewModel.dismissWhatsNew() },
            onOpenFullLog = onNavigateToUpdateLog
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .testTag("dashboard_screen"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // A. Top Header
            item {
                DashboardHeader(
                    onUpdateLogClick = onNavigateToUpdateLog,
                    onSettingsClick = onNavigateToSettings
                )
            }

            // B. Main Action Button: BUILD AN APP
            item {
                BuildAppHeroBanner(onClick = onNavigateToImport)
            }

            // C. My Projects Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.section_my_projects),
                        actionText = stringResource(R.string.action_view_all),
                        onActionClick = onNavigateToProjects
                    )

                    if (projects.isEmpty()) {
                        MfbEmptyState(
                            icon = Icons.Default.Folder,
                            title = stringResource(R.string.empty_projects_title),
                            supportingText = stringResource(R.string.empty_projects_desc),
                            action = {
                                Button(
                                    onClick = onNavigateToImport,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("empty_projects_import_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Import Project",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        )
                    } else {
                        projects.take(3).forEach { project ->
                            com.example.mfb.ui.components.ProjectCard(
                                project = project,
                                onClick = { onNavigateToProjectDetails(project.id) }
                            )
                        }
                    }
                }
            }

            // D. Recent Builds Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.section_recent_builds)
                    )

                    if (recentBuilds.isEmpty()) {
                        MfbEmptyState(
                            icon = Icons.Default.Build,
                            title = stringResource(R.string.empty_builds_title),
                            supportingText = stringResource(R.string.empty_builds_desc)
                        )
                    }
                }
            }

            // E. Templates Library Visual Entry Point
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.section_templates),
                        actionText = "Explore",
                        onActionClick = onNavigateToTemplates
                    )

                    TemplatesEntryCard(onClick = onNavigateToTemplates)
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    onUpdateLogClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_mfb_logo),
                    contentDescription = "MFB Logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MFB",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TechBadge(
                        text = "v${BuildConfig.VERSION_NAME}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onUpdateLogClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .testTag("dashboard_update_log_button")
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Update Log",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .testTag("dashboard_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun BuildAppHeroBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(22.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Native Android Builder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Package your responsive HTML, CSS and JavaScript into high-performance Android APK and AAB bundles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("build_an_app_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_build_an_app),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplatesEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .testTag("templates_entry_card")
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Dashboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Starter Architectures",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TechBadge(
                        text = "Phase 01 Catalog",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.templates_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "View Templates",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
