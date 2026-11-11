package com.example.mfb.presentation.templates

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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mfb.ui.components.MfbTopBar
import com.example.mfb.ui.components.TechBadge

private data class TemplatePreview(
    val title: String,
    val description: String,
    val category: String,
    val icon: ImageVector,
    val features: List<String>
)

private val sampleTemplates = listOf(
    TemplatePreview(
        title = "Modern Single Page Application",
        description = "Lightweight responsive mobile SPA layout with bottom navigation, theme toggling, and fast touch feedback.",
        category = "WEB APP",
        icon = Icons.Default.Web,
        features = listOf("Responsive UI", "Touch Gestures", "Fast Boot")
    ),
    TemplatePreview(
        title = "Progressive Web Scaffold (PWA)",
        description = "Full offline caching service worker template, local storage sync, and mobile app-like viewport meta setup.",
        category = "OFFLINE",
        icon = Icons.Default.PhoneAndroid,
        features = listOf("Service Worker", "Cache API", "Offline First")
    ),
    TemplatePreview(
        title = "HTML5 Canvas Interactive App",
        description = "High-performance hardware-accelerated 2D canvas wrapper optimized for games, graphs, and interactive simulations.",
        category = "GRAPHICS",
        icon = Icons.Default.Speed,
        features = listOf("60 FPS Canvas", "Touch Events", "Audio API")
    ),
    TemplatePreview(
        title = "Executive Analytics Portal",
        description = "Enterprise dashboard scaffold with responsive metric cards, chart containers, and dark-mode styling.",
        category = "DASHBOARD",
        icon = Icons.Default.Dashboard,
        features = listOf("Data Grids", "Chart Containers", "Modern Tokens")
    )
)

@Composable
fun TemplatesScreen(
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            MfbTopBar(
                title = "Templates Library",
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
                    .testTag("templates_screen"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Starter Architectures",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TechBadge(text = "PHASE 01 PREVIEW")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reference starter scaffolds engineered specifically for native Android packaging. Template downloads will be enabled in upcoming releases.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(sampleTemplates.size) { index ->
                    val template = sampleTemplates[index]
                    TemplatePreviewCard(template = template)
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Template System Roadmap",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "In future phases, templates can be cloned directly into your workspace with pre-configured AndroidManifest, application icons, and build settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun TemplatePreviewCard(
    template: TemplatePreview,
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
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = template.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = template.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    TechBadge(
                        text = template.category,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = template.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                template.features.forEach { feature ->
                    TechBadge(
                        text = feature,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
