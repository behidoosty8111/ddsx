package com.example.mfb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.ProjectHealthStatus
import com.example.mfb.domain.model.SourceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HealthStatusBadge(
    status: ProjectHealthStatus,
    modifier: Modifier = Modifier
) {
    val (container, content) = when (status) {
        ProjectHealthStatus.HEALTHY -> Pair(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.secondary
        )
        ProjectHealthStatus.WARNING, ProjectHealthStatus.CHECK_REQUIRED -> Pair(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.tertiary
        )
        ProjectHealthStatus.MISSING_FILES,
        ProjectHealthStatus.INVALID_ENTRY_POINT,
        ProjectHealthStatus.STORAGE_ERROR -> Pair(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error
        )
    }
    TechBadge(
        text = status.label,
        containerColor = container,
        contentColor = content,
        modifier = modifier
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (actionText != null) {
            TextButton(
                onClick = onActionClick,
                modifier = Modifier.testTag("action_${title.lowercase().replace(" ", "_")}")
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun TechBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.secondary
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (() -> Unit)? = null
) {
    val isZip = project.sourceType == SourceType.ZIP
    val badgeLabel = if (isZip) "ZIP" else "HTML"
    val icon = if (isZip) Icons.Default.FolderZip else Icons.Default.Code
    val createdFormatted = formatShortDate(project.createdAt)
    val modifiedFormatted = formatShortDate(project.updatedAt)
    val sizeText = formatBytes(project.totalSizeBytes)

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
            .testTag("project_card_${project.id}")
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isZip) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isZip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        TechBadge(
                            text = badgeLabel,
                            containerColor = if (isZip) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (isZip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                        HealthStatusBadge(status = project.healthStatus)
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "${project.mainHtmlRelativePath} • ${project.fileCount} file${if (project.fileCount != 1) "s" else ""} • $sizeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onToggleFavorite != null) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("favorite_toggle_${project.id}")
                    ) {
                        Icon(
                            imageVector = if (project.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (project.isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (project.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Project",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (project.description.isNotBlank()) {
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (project.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    project.tags.take(4).forEach { tag ->
                        TechBadge(
                            text = "#$tag",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Created: $createdFormatted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Modified: $modifiedFormatted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun ProjectGridCard(
    project: Project,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isZip = project.sourceType == SourceType.ZIP
    val badgeLabel = if (isZip) "ZIP" else "HTML"
    val icon = if (isZip) Icons.Default.FolderZip else Icons.Default.Code
    val sizeText = formatBytes(project.totalSizeBytes)
    val modifiedFormatted = formatShortDate(project.updatedAt)

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
            .testTag("project_grid_card_${project.id}")
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isZip) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isZip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (project.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (project.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (project.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = project.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            TechBadge(
                text = badgeLabel,
                containerColor = if (isZip) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isZip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )

            Text(
                text = project.mainHtmlRelativePath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${project.fileCount} files • $sizeText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = modifiedFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun formatShortDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Never"
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups >= units.size) digitGroups = units.size - 1
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}
