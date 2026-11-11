package com.example.mfb.presentation.changelog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.mfb.domain.model.ChangeItem
import com.example.mfb.domain.model.ReleaseInfo
import com.example.mfb.presentation.viewmodel.MfbViewModel
import com.example.mfb.ui.components.MfbEmptyState
import com.example.mfb.ui.components.MfbTopBar
import com.example.mfb.ui.components.TechBadge

@Composable
fun UpdateLogScreen(
    viewModel: MfbViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val releases = remember(viewModel) { viewModel.releases }

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            MfbTopBar(
                title = "Update Log",
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
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .testTag("update_log_screen"),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Installed Version Info
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "What's New in MFB",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Installed: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (releases.isEmpty()) {
                    item {
                        MfbEmptyState(
                            icon = Icons.Default.History,
                            title = "No Release Records",
                            supportingText = "No update history entries are currently available."
                        )
                    }
                } else {
                    itemsIndexed(releases, key = { _, rel -> rel.id }) { index, release ->
                        ReleaseExpandableCard(
                            release = release,
                            initiallyExpanded = index == 0
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseExpandableCard(
    release: ReleaseInfo,
    initiallyExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val isCurrentInstalled = release.versionCode == BuildConfig.VERSION_CODE

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (isCurrentInstalled) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { expanded = !expanded }
            .testTag("release_card_${release.id}")
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TechBadge(
                        text = "v${release.versionName}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                    TechBadge(
                        text = "Phase ${release.phaseNumber.toString().padStart(2, '0')}",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                    TechBadge(
                        text = release.releaseType.displayName,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse release" else "Expand release",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = release.releaseTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${release.releaseDate} • Build ${release.versionCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = release.releaseDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    if (release.addedFeatures.isNotEmpty()) {
                        ChangeCategorySection(
                            heading = "NEW FEATURES",
                            subtitle = "What was added",
                            icon = Icons.Default.AutoAwesome,
                            tint = MaterialTheme.colorScheme.primary,
                            items = release.addedFeatures
                        )
                    }

                    if (release.improvedFeatures.isNotEmpty()) {
                        ChangeCategorySection(
                            heading = "IMPROVEMENTS",
                            subtitle = "What was improved",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            tint = MaterialTheme.colorScheme.secondary,
                            items = release.improvedFeatures
                        )
                    }

                    if (release.bugFixes.isNotEmpty()) {
                        ChangeCategorySection(
                            heading = "BUG FIXES",
                            subtitle = "Problems resolved",
                            icon = Icons.Default.BugReport,
                            tint = MaterialTheme.colorScheme.tertiary,
                            items = release.bugFixes
                        )
                    }

                    if (release.securityImprovements.isNotEmpty()) {
                        ChangeCategorySection(
                            heading = "SECURITY",
                            subtitle = "Security protections",
                            icon = Icons.Default.Security,
                            tint = MaterialTheme.colorScheme.secondary,
                            items = release.securityImprovements
                        )
                    }

                    if (release.removedFeatures.isNotEmpty()) {
                        ChangeCategorySection(
                            heading = "REMOVED",
                            subtitle = "Deprecated or removed items",
                            icon = Icons.Default.RemoveCircleOutline,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            items = release.removedFeatures
                        )
                    }

                    if (release.knownIssues.isNotEmpty()) {
                        ChangeCategorySection(
                            heading = "KNOWN ISSUES",
                            subtitle = "Unresolved items",
                            icon = Icons.Default.WarningAmber,
                            tint = MaterialTheme.colorScheme.error,
                            items = release.knownIssues
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeCategorySection(
    heading: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    items: List<ChangeItem>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = heading,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = tint
            )
            Text(
                text = "• $subtitle",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items.forEach { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                item.technicalNote?.let { note ->
                    Text(
                        text = "Note: $note",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
