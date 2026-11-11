package com.example.mfb.data.engine

import android.content.Context
import com.example.mfb.domain.model.Project
import org.json.JSONObject
import java.io.File
import java.io.IOException

object ProjectStorageManager {
    private const val PROJECTS_DIR_NAME = "projects"
    const val METADATA_FILE_NAME = "project_metadata.json"

    fun getProjectsRoot(context: Context): File {
        val dir = File(context.filesDir, PROJECTS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getProjectDir(context: Context, projectUuid: String): File {
        val root = getProjectsRoot(context)
        val projectDir = File(root, projectUuid)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        return projectDir
    }

    fun calculateStats(dir: File): Pair<Int, Long> {
        if (!dir.exists()) return Pair(0, 0L)
        var count = 0
        var totalBytes = 0L

        dir.walkTopDown().forEach { file ->
            if (file.isFile && file.name != METADATA_FILE_NAME) {
                count++
                totalBytes += file.length()
            }
        }
        return Pair(count, totalBytes)
    }

    fun deleteProject(context: Context, projectUuid: String): Boolean {
        if (projectUuid.isBlank()) return false
        val root = getProjectsRoot(context)
        val projectDir = File(root, projectUuid)
        return if (projectDir.exists()) {
            projectDir.deleteRecursively()
        } else {
            false
        }
    }

    fun deleteProjectSafely(context: Context, projectUuid: String): Result<Unit> {
        if (projectUuid.isBlank()) return Result.success(Unit)
        val root = getProjectsRoot(context)
        val projectDir = File(root, projectUuid)
        if (!projectDir.exists()) {
            return Result.success(Unit)
        }
        val deleted = projectDir.deleteRecursively()
        return if (deleted && !projectDir.exists()) {
            Result.success(Unit)
        } else {
            Result.failure(IOException("Failed to remove project directory: ${projectDir.name}"))
        }
    }

    fun writeMetadata(projectDir: File, project: Project) {
        val metadataFile = File(projectDir, METADATA_FILE_NAME)
        val json = JSONObject().apply {
            put("projectUuid", project.projectUuid)
            put("name", project.name)
            put("description", project.description)
            put("packageId", project.packageId)
            put("versionName", project.versionName)
            put("versionCode", project.versionCode)
            put("originalFileName", project.originalFileName)
            put("sourceType", project.sourceType.name)
            put("mainHtmlRelativePath", project.mainHtmlRelativePath)
            put("fileCount", project.fileCount)
            put("totalSizeBytes", project.totalSizeBytes)
            put("createdAt", project.createdAt)
            put("updatedAt", project.updatedAt)
            put("missingLocalAssets", project.missingLocalAssets.joinToString(","))
        }
        try {
            metadataFile.writeText(json.toString(2))
        } catch (e: IOException) {
            // Non-fatal if metadata file write fails
        }
    }
}
