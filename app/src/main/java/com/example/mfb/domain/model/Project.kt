package com.example.mfb.domain.model

enum class SourceType {
    ZIP,
    HTML
}

enum class ImportStatus {
    SUCCESS,
    FAILED,
    PENDING
}

enum class ProjectHealthStatus(val label: String) {
    HEALTHY("Healthy"),
    WARNING("Warning"),
    MISSING_FILES("Missing Files"),
    INVALID_ENTRY_POINT("Invalid Entry Point"),
    STORAGE_ERROR("Storage Error"),
    CHECK_REQUIRED("Check Required")
}

data class Project(
    val id: Long = 0,
    val projectUuid: String = "",
    val name: String,
    val description: String = "",
    val packageId: String = "com.mfb.app",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val originalFileName: String = "",
    val sourceType: SourceType = SourceType.ZIP,
    val mainHtmlRelativePath: String = "index.html",
    val internalProjectPath: String = "",
    val fileCount: Int = 1,
    val totalSizeBytes: Long = 0,
    val importStatus: ImportStatus = ImportStatus.SUCCESS,
    val healthStatus: ProjectHealthStatus = ProjectHealthStatus.CHECK_REQUIRED,
    val healthMessage: String = "",
    val thumbnailPath: String? = null,
    val isZipArchive: Boolean = (sourceType == SourceType.ZIP),
    val sourcePath: String = internalProjectPath,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val iconUri: String? = null,
    val missingLocalAssets: List<String> = emptyList(),
    val projectCategory: ProjectCategory = ProjectCategory.WEB_HTML,
    val detectedTechSummary: String = ""
)
