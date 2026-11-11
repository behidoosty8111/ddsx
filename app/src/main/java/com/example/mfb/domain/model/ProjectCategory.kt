package com.example.mfb.domain.model

enum class ProjectCategory(
    val displayName: String,
    val badgeLabel: String,
    val supportsLivePreview: Boolean,
    val requiresNodeBuild: Boolean
) {
    ANDROID_GRADLE_KOTLIN(
        displayName = "Android Kotlin (Gradle)",
        badgeLabel = "Kotlin",
        supportsLivePreview = false,
        requiresNodeBuild = false
    ),
    ANDROID_GRADLE_JAVA(
        displayName = "Android Java (Gradle)",
        badgeLabel = "Java",
        supportsLivePreview = false,
        requiresNodeBuild = false
    ),
    ANDROID_GRADLE(
        displayName = "Android Gradle Project",
        badgeLabel = "Gradle",
        supportsLivePreview = false,
        requiresNodeBuild = false
    ),
    CAPACITOR(
        displayName = "Capacitor Hybrid App",
        badgeLabel = "Capacitor",
        supportsLivePreview = true,
        requiresNodeBuild = true
    ),
    REACT_VITE(
        displayName = "React + Vite App",
        badgeLabel = "React+Vite",
        supportsLivePreview = true,
        requiresNodeBuild = true
    ),
    REACT(
        displayName = "React Web App",
        badgeLabel = "React",
        supportsLivePreview = true,
        requiresNodeBuild = true
    ),
    VITE(
        displayName = "Vite Web App",
        badgeLabel = "Vite",
        supportsLivePreview = true,
        requiresNodeBuild = true
    ),
    WEB_HTML(
        displayName = "HTML / CSS / JS",
        badgeLabel = "HTML5",
        supportsLivePreview = true,
        requiresNodeBuild = false
    ),
    UNKNOWN(
        displayName = "Unrecognized Archive",
        badgeLabel = "Archive",
        supportsLivePreview = false,
        requiresNodeBuild = false
    )
}

data class ProjectAnalysisReport(
    val category: ProjectCategory,
    val entryPointPath: String,
    val htmlCandidates: List<String> = emptyList(),
    val detectedPackageId: String? = null,
    val detectedAppName: String? = null,
    val detectedVersionName: String? = null,
    val detectedVersionCode: Int? = null,
    val hasGradleBuild: Boolean = false,
    val hasGradleWrapper: Boolean = false,
    val hasAndroidManifest: Boolean = false,
    val hasPackageJson: Boolean = false,
    val hasCapacitorConfig: Boolean = false,
    val hasPrebuiltWebDist: Boolean = false,
    val kotlinFileCount: Int = 0,
    val javaFileCount: Int = 0,
    val htmlFileCount: Int = 0,
    val totalFiles: Int = 0,
    val techSummary: String = "",
    val buildReadinessNotes: List<String> = emptyList()
)
