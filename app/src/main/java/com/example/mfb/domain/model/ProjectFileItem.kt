package com.example.mfb.domain.model

enum class FileCategory(val label: String) {
    ALL("All"),
    HTML("HTML"),
    STYLES("CSS"),
    SCRIPTS("JS"),
    IMAGES("Images"),
    FONTS("Fonts"),
    DATA("Data"),
    TEXT("Text"),
    OTHER("Other");

    companion object {
        fun fromExtension(ext: String): FileCategory {
            val lower = ext.lowercase()
            return when (lower) {
                "html", "htm" -> HTML
                "css" -> STYLES
                "js", "mjs", "ts" -> SCRIPTS
                "png", "jpg", "jpeg", "svg", "gif", "webp", "ico" -> IMAGES
                "ttf", "otf", "woff", "woff2", "eot" -> FONTS
                "json", "xml" -> DATA
                "txt", "md" -> TEXT
                else -> OTHER
            }
        }
    }
}

data class ProjectFileItem(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val childCount: Int = 0,
    val category: FileCategory = if (isDirectory) FileCategory.ALL else FileCategory.fromExtension(extension)
)
