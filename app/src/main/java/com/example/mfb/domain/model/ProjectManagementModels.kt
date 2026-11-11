package com.example.mfb.domain.model

enum class ProjectSortOption(val label: String) {
    RECENTLY_MODIFIED("Recently modified"),
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
    LARGEST("Largest first"),
    SMALLEST("Smallest first")
}

enum class ProjectFilterOption(val label: String) {
    ALL("All"),
    HTML("HTML"),
    ZIP("ZIP"),
    HEALTHY("Healthy"),
    WARNINGS("Warnings"),
    RECENTLY_MODIFIED("Recent"),
    FAVORITES("Favorites")
}

enum class ProjectViewMode {
    LIST,
    GRID
}

data class ProjectStatistics(
    val totalProjects: Int = 0,
    val htmlCount: Int = 0,
    val zipCount: Int = 0,
    val warningCount: Int = 0,
    val recentlyModifiedCount: Int = 0,
    val totalFiles: Int = 0,
    val totalSizeBytes: Long = 0L,
    val largestProject: Project? = null,
    val mostRecentlyModifiedProject: Project? = null,
    val missingDirectoriesCount: Int = 0,
    val isUnavailable: Boolean = false
)

data class ProjectHealthReport(
    val status: ProjectHealthStatus,
    val directoryExists: Boolean,
    val entryPointExists: Boolean,
    val entryPointReadable: Boolean,
    val metadataMatchesFiles: Boolean,
    val actualFileCount: Int,
    val actualSizeBytes: Long,
    val missingLocalAssets: List<String> = emptyList(),
    val diagnostics: List<String> = emptyList(),
    val checkedAt: Long = System.currentTimeMillis()
)

sealed class FilePreviewContent {
    data class TextPreview(
        val name: String,
        val relativePath: String,
        val content: String,
        val truncated: Boolean
    ) : FilePreviewContent()

    data class ImagePreview(
        val name: String,
        val relativePath: String,
        val absolutePath: String,
        val width: Int,
        val height: Int
    ) : FilePreviewContent()

    data class Unsupported(
        val name: String,
        val reason: String
    ) : FilePreviewContent()
}

sealed class ProjectOperationState {
    data object Idle : ProjectOperationState()
    data class InProgress(val message: String) : ProjectOperationState()
    data class Success(val message: String, val deletedProjectId: Long? = null) : ProjectOperationState()
    data class Error(val message: String) : ProjectOperationState()
}

data class FileExplorerUiState(
    val currentRelativePath: String = "",
    val items: List<ProjectFileItem> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: FileCategory = FileCategory.ALL,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
