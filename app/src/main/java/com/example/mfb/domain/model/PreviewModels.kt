package com.example.mfb.domain.model

enum class ViewportPreset(
    val label: String,
    val widthDp: Int?,
    val heightDp: Int?
) {
    RESPONSIVE("Responsive", null, null),
    MOBILE("Mobile (390×844)", 390, 844),
    TABLET("Tablet (768×1024)", 768, 1024),
    DESKTOP("Desktop (1366×768)", 1366, 768)
}

sealed class PreviewLoadState {
    data object Idle : PreviewLoadState()
    data object PreparingProject : PreviewLoadState()
    data class LoadingPage(val progress: Int) : PreviewLoadState()
    data object Rendering : PreviewLoadState()
    data class Loaded(val currentUrl: String, val pageTitle: String) : PreviewLoadState()
    data class Failed(val message: String, val isEntryMissing: Boolean = false) : PreviewLoadState()
    data object Cancelled : PreviewLoadState()
}

data class PreviewSettings(
    val javaScriptEnabled: Boolean = true,
    val externalNetworkEnabled: Boolean = false,
    val desktopViewportMode: Boolean = false,
    val defaultViewportPreset: ViewportPreset = ViewportPreset.RESPONSIVE,
    val zoomControlsEnabled: Boolean = true
)

enum class ConsoleLogSeverity {
    INFO,
    WARNING,
    ERROR
}

enum class ConsoleLogType(val label: String) {
    CONSOLE_ERROR("JS Error"),
    CONSOLE_WARNING("JS Warning"),
    CONSOLE_INFO("Console"),
    PAGE_LOAD_ERROR("Page Error"),
    RESOURCE_ERROR("Resource Missing"),
    HTTP_ERROR("HTTP Error"),
    SSL_ERROR("SSL Error"),
    NAVIGATION_BLOCKED("Blocked"),
    SECURITY_POLICY("Security")
}

data class PreviewLogEntry(
    val id: String,
    val timestampMs: Long,
    val severity: ConsoleLogSeverity,
    val type: ConsoleLogType,
    val message: String,
    val safeResourceRef: String,
    val lineNumber: Int? = null
)

data class PendingExternalNavigation(
    val url: String,
    val scheme: String,
    val isSpecialScheme: Boolean
)

data class PreviewHistoryEntry(
    val projectId: Long,
    val entryPointRelativePath: String,
    val viewportPreset: ViewportPreset,
    val zoomPercent: Int = 100,
    val lastPreviewedAt: Long
)
