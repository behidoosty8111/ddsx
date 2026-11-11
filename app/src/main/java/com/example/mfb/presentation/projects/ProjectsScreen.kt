package com.example.mfb.presentation.projects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.mfb.domain.model.ProjectFilterOption
import com.example.mfb.domain.model.ProjectSortOption
import com.example.mfb.domain.model.ProjectStatistics
import com.example.mfb.domain.model.ProjectViewMode
import com.example.mfb.presentation.viewmodel.MfbViewModel
import com.example.mfb.ui.components.MfbEmptyState
import com.example.mfb.ui.components.MfbTopBar
import com.example.mfb.ui.components.ProjectCard
import com.example.mfb.ui.components.ProjectGridCard
import com.example.mfb.ui.components.formatBytes
import com.example.mfb.ui.components.formatShortDate

@Composable
fun ProjectsScreen(
    viewModel: MfbViewModel,
    onNavigateToImport: () -> Unit,
    onNavigateToProjectDetails: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allProjects by viewModel.projects.collectAsStateWithLifecycle()
    val filteredProjects by viewModel.filteredProjects.collectAsStateWithLifecycle()
    val searchQuery by viewModel.projectSearchQuery.collectAsStateWithLifecycle()
    val sortOption by viewModel.projectSortOption.collectAsStateWithLifecycle()
    val filterOption by viewModel.projectFilterOption.collectAsStateWithLifecycle()
    val viewMode by viewModel.projectViewMode.collectAsStateWithLifecycle()
    val stats by viewModel.projectStatistics.collectAsStateWithLifecycle()
    val isLoading by viewModel.projectsLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.projectsError.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    var showStatsExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MfbTopBar(
                title = "My Projects",
                showLogo = true
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
                    .testTag("projects_screen"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Project Workspace",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Search, organize, and manage your HTML & ZIP projects",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onNavigateToImport,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("projects_new_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "New", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Statistics Summary Card (only when projects exist or unavailable)
                if (allProjects.isNotEmpty() || stats.isUnavailable) {
                    item {
                        ProjectStatisticsCard(
                            stats = stats,
                            expanded = showStatsExpanded,
                            onToggleExpand = { showStatsExpanded = !showStatsExpanded }
                        )
                    }
                }

                // Search, Filter, Sort & View Mode Controls
                if (allProjects.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setProjectSearchQuery(it) },
                                placeholder = { Text("Search by name, entry file, or tag...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search projects"
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setProjectSearchQuery("") }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear search"
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("projects_search_input")
                            )

                            // Filter Chips Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProjectFilterOption.entries.forEach { option ->
                                    val selected = filterOption == option
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.setProjectFilterOption(option) },
                                        label = { Text(option.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.testTag("filter_chip_${option.name.lowercase()}")
                                    )
                                }
                            }

                            // Sort + View Toggle + Result Count
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${filteredProjects.size} of ${allProjects.size} project${if (allProjects.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box {
                                        OutlinedButton(
                                            onClick = { showSortMenu = true },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("projects_sort_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = sortOption.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false }
                                        ) {
                                            ProjectSortOption.entries.forEach { option ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = option.label,
                                                            fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    onClick = {
                                                        viewModel.setProjectSortOption(option)
                                                        showSortMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            val next = if (viewMode == ProjectViewMode.LIST) ProjectViewMode.GRID else ProjectViewMode.LIST
                                            viewModel.setProjectViewMode(next)
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .testTag("projects_view_mode_toggle")
                                    ) {
                                        Icon(
                                            imageVector = if (viewMode == ProjectViewMode.LIST) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                                            contentDescription = if (viewMode == ProjectViewMode.LIST) "Switch to Grid view" else "Switch to List view",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // State Handling: Loading / Error / Empty / List or Grid
                when {
                    isLoading && allProjects.isEmpty() -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    errorMessage != null && allProjects.isEmpty() -> {
                        item {
                            MfbEmptyState(
                                icon = Icons.Default.ErrorOutline,
                                title = "Unable to load projects",
                                supportingText = errorMessage ?: "An unexpected database error occurred."
                            )
                        }
                    }

                    allProjects.isEmpty() -> {
                        item {
                            MfbEmptyState(
                                icon = Icons.Default.Folder,
                                title = stringResource(R.string.empty_projects_title),
                                supportingText = stringResource(R.string.empty_projects_desc),
                                action = {
                                    Button(
                                        onClick = onNavigateToImport,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("projects_empty_import_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.action_build_an_app),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        }
                    }

                    filteredProjects.isEmpty() -> {
                        item {
                            MfbEmptyState(
                                icon = Icons.Default.SearchOff,
                                title = "No matching projects",
                                supportingText = "Try adjusting your search query or active filter.",
                                action = {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.setProjectSearchQuery("")
                                            viewModel.setProjectFilterOption(ProjectFilterOption.ALL)
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Reset Filters")
                                    }
                                }
                            )
                        }
                    }

                    viewMode == ProjectViewMode.LIST -> {
                        items(filteredProjects, key = { it.id }) { project ->
                            ProjectCard(
                                project = project,
                                onClick = { onNavigateToProjectDetails(project.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(project) }
                            )
                        }
                    }

                    else -> {
                        val rows = filteredProjects.chunked(2)
                        items(rows, key = { row -> row.joinToString("-") { it.id.toString() } }) { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { project ->
                                    ProjectGridCard(
                                        project = project,
                                        onClick = { onNavigateToProjectDetails(project.id) },
                                        onToggleFavorite = { viewModel.toggleFavorite(project) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectStatisticsCard(
    stats: ProjectStatistics,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
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
            .clickable(onClick = onToggleExpand)
            .testTag("project_statistics_card")
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Workspace Statistics",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (stats.isUnavailable) {
                            Text(
                                text = "Statistics temporarily unavailable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = "${stats.totalProjects} projects • ${stats.totalFiles} files • ${formatBytes(stats.totalSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse statistics" else "Expand statistics",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded && !stats.isUnavailable) {
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatMiniTile(
                            label = "HTML",
                            value = stats.htmlCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatMiniTile(
                            label = "ZIP",
                            value = stats.zipCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatMiniTile(
                            label = "Warnings",
                            value = stats.warningCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatMiniTile(
                            label = "Recent",
                            value = stats.recentlyModifiedCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    stats.largestProject?.let { largest ->
                        Text(
                            text = "Largest: ${largest.name} (${formatBytes(largest.totalSizeBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    stats.mostRecentlyModifiedProject?.let { recent ->
                        Text(
                            text = "Last Modified: ${recent.name} (${formatShortDate(recent.updatedAt)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (stats.missingDirectoriesCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${stats.missingDirectoriesCount} project(s) have missing directories on disk.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
