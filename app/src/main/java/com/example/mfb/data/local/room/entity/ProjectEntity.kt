package com.example.mfb.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.mfb.domain.model.ImportStatus
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.ProjectCategory
import com.example.mfb.domain.model.ProjectHealthStatus
import com.example.mfb.domain.model.SourceType

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectUuid: String = "",
    val name: String,
    val description: String = "",
    val packageId: String = "com.mfb.app",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val originalFileName: String = "",
    val sourceType: String = "ZIP",
    val mainHtmlRelativePath: String = "index.html",
    val internalProjectPath: String = "",
    val fileCount: Int = 1,
    val totalSizeBytes: Long = 0,
    val importStatus: String = "SUCCESS",
    val healthStatus: String = "CHECK_REQUIRED",
    val healthMessage: String = "",
    val thumbnailPath: String? = null,
    val isZipArchive: Boolean = true,
    val sourcePath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val tags: String = "",
    val iconUri: String? = null,
    val missingAssets: String = "",
    val projectCategory: String = "WEB_HTML",
    val detectedTechSummary: String = ""
) {
    fun toDomain(): Project = Project(
        id = id,
        projectUuid = projectUuid,
        name = name,
        description = description,
        packageId = packageId,
        versionName = versionName,
        versionCode = versionCode,
        originalFileName = originalFileName,
        sourceType = try {
            SourceType.valueOf(sourceType)
        } catch (e: Exception) {
            if (isZipArchive) SourceType.ZIP else SourceType.HTML
        },
        mainHtmlRelativePath = mainHtmlRelativePath,
        internalProjectPath = internalProjectPath.ifEmpty { sourcePath },
        fileCount = fileCount,
        totalSizeBytes = totalSizeBytes,
        importStatus = try {
            ImportStatus.valueOf(importStatus)
        } catch (e: Exception) {
            ImportStatus.SUCCESS
        },
        healthStatus = try {
            ProjectHealthStatus.valueOf(healthStatus)
        } catch (e: Exception) {
            ProjectHealthStatus.CHECK_REQUIRED
        },
        healthMessage = healthMessage,
        thumbnailPath = thumbnailPath,
        isZipArchive = isZipArchive,
        sourcePath = sourcePath.ifEmpty { internalProjectPath },
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
        isFavorite = isFavorite,
        tags = if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
        iconUri = iconUri,
        missingLocalAssets = if (missingAssets.isBlank()) emptyList() else missingAssets.split(";;;").filter { it.isNotBlank() },
        projectCategory = runCatching { ProjectCategory.valueOf(projectCategory) }.getOrDefault(ProjectCategory.WEB_HTML),
        detectedTechSummary = detectedTechSummary
    )

    companion object {
        fun fromDomain(project: Project): ProjectEntity = ProjectEntity(
            id = project.id,
            projectUuid = project.projectUuid,
            name = project.name,
            description = project.description,
            packageId = project.packageId,
            versionName = project.versionName,
            versionCode = project.versionCode,
            originalFileName = project.originalFileName,
            sourceType = project.sourceType.name,
            mainHtmlRelativePath = project.mainHtmlRelativePath,
            internalProjectPath = project.internalProjectPath,
            fileCount = project.fileCount,
            totalSizeBytes = project.totalSizeBytes,
            importStatus = project.importStatus.name,
            healthStatus = project.healthStatus.name,
            healthMessage = project.healthMessage,
            thumbnailPath = project.thumbnailPath,
            isZipArchive = project.isZipArchive,
            sourcePath = project.sourcePath.ifEmpty { project.internalProjectPath },
            createdAt = project.createdAt,
            updatedAt = project.updatedAt,
            lastOpenedAt = project.lastOpenedAt,
            isFavorite = project.isFavorite,
            tags = project.tags.joinToString(","),
            iconUri = project.iconUri,
            missingAssets = project.missingLocalAssets.joinToString(";;;"),
            projectCategory = project.projectCategory.name,
            detectedTechSummary = project.detectedTechSummary
        )
    }
}
