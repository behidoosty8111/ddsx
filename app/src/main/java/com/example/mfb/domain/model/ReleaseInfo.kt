package com.example.mfb.domain.model

enum class ReleaseType(val displayName: String) {
    MAJOR_UPDATE("Major Update"),
    FEATURE_UPDATE("Feature Update"),
    MAINTENANCE_UPDATE("Maintenance Update"),
    BUG_FIX_UPDATE("Bug Fix Update"),
    SECURITY_UPDATE("Security Update"),
    DEVELOPMENT_MILESTONE("Development Milestone")
}

data class ChangeItem(
    val category: String,
    val title: String,
    val description: String,
    val relatedFeature: String? = null,
    val technicalNote: String? = null
)

data class ReleaseInfo(
    val id: String,
    val versionName: String,
    val versionCode: Int,
    val phaseNumber: Int,
    val releaseTitle: String,
    val releaseDate: String,
    val releaseDescription: String,
    val releaseType: ReleaseType,
    val addedFeatures: List<ChangeItem> = emptyList(),
    val improvedFeatures: List<ChangeItem> = emptyList(),
    val bugFixes: List<ChangeItem> = emptyList(),
    val securityImprovements: List<ChangeItem> = emptyList(),
    val removedFeatures: List<ChangeItem> = emptyList(),
    val knownIssues: List<ChangeItem> = emptyList()
)
