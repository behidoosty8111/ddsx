package com.example.mfb.presentation.about

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.mfb.ui.components.MfbTopBar
import com.example.mfb.ui.components.TechBadge

private data class RoadmapPhase(
    val phaseNumber: String,
    val title: String,
    val description: String,
    val status: PhaseStatus
)

private enum class PhaseStatus {
    ACTIVE,
    UPCOMING,
    PLANNED
}

private val roadmapPhases = listOf(
    RoadmapPhase("PHASE 01", "Foundation, Architecture & Dashboard", "Clean Architecture, Jetpack Compose M3, Room SQLite foundation, DataStore theme persistence, and responsive dashboard.", PhaseStatus.ACTIVE),
    RoadmapPhase("PHASE 02", "HTML and ZIP Import Engine", "Safe streaming archive extraction, single-file HTML parsing, asset validation, and sandbox creation.", PhaseStatus.ACTIVE),
    RoadmapPhase("PHASE 03", "Project Workspace, File Manager & Update Log", "Multi-project search/sort/filter, dedicated workspace, internal file explorer, duplication, SAF ZIP export, and Update Log.", PhaseStatus.ACTIVE),
    RoadmapPhase("PHASE 04", "Professional Code Editor", "Syntax-highlighted HTML/CSS/JS editor with line numbering, search, and local file tree navigation.", PhaseStatus.UPCOMING),
    RoadmapPhase("PHASE 05", "Live Preview Engine", "Real-time WebView rendering engine with console inspection and viewport switching.", PhaseStatus.PLANNED),
    RoadmapPhase("PHASE 06", "Android App Configuration", "Manifest package ID, app icon generator, splash screens, permissions, and orientation settings.", PhaseStatus.PLANNED),
    RoadmapPhase("PHASE 07", "Android SDK & Gradle Build Engine", "Native asset packaging, ProGuard rules optimization, and offline Gradle compilation pipelines.", PhaseStatus.PLANNED),
    RoadmapPhase("PHASE 08", "APK/AAB Export & Keystore Manager", "V1/V2 APK signing, release key generator, and Google Play Store App Bundle (AAB) export.", PhaseStatus.PLANNED),
    RoadmapPhase("PHASE 09", "Final Testing & Optimization", "End-to-end device testing, performance audits, APK size compression, and final release validation.", PhaseStatus.PLANNED)
)

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToUpdateLog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            MfbTopBar(
                title = "About MFB",
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
                    .testTag("about_screen"),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Banner
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
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_mfb_logo),
                                        contentDescription = "MFB Logo",
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "MFB",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TechBadge(
                                            text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Professional HTML to APK Builder",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "MFB is a native Android development platform that transforms web applications into production-ready Android application packages (APK and AAB) with zero reliance on cloud compilers.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = onNavigateToUpdateLog,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("about_update_log_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("View Update Log & Release History", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Section Title: 9-Phase Roadmap
                item {
                    Text(
                        text = "9-Phase Architecture Roadmap",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(roadmapPhases.size) { index ->
                    val phase = roadmapPhases[index]
                    RoadmapPhaseCard(phase = phase)
                }
            }
        }
    }
}

@Composable
private fun RoadmapPhaseCard(
    phase: RoadmapPhase,
    modifier: Modifier = Modifier
) {
    val (statusBadge, badgeContainer, badgeContent, statusIcon) = when (phase.status) {
        PhaseStatus.ACTIVE -> Quadruple(
            "COMPLETED & ACTIVE",
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.secondary,
            Icons.Default.CheckCircle
        )
        PhaseStatus.UPCOMING -> Quadruple(
            "NEXT: PHASE 04",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            Icons.Default.HourglassEmpty
        )
        PhaseStatus.PLANNED -> Quadruple(
            "PLANNED",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Schedule
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (phase.status == PhaseStatus.ACTIVE) {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                },
                shape = RoundedCornerShape(14.dp)
            )
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = badgeContent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = phase.phaseNumber,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TechBadge(
                    text = statusBadge,
                    containerColor = badgeContainer,
                    contentColor = badgeContent
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = phase.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = phase.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
