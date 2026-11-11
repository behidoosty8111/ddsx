package com.example.mfb.data.engine

import android.content.Context
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.ProjectHealthReport
import com.example.mfb.domain.model.ProjectHealthStatus
import java.io.File
import java.util.regex.Pattern

object ProjectHealthChecker {

    private val LINK_PATTERN = Pattern.compile(
        """(?:src|href)\s*=\s*["']([^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )

    fun checkProjectHealth(context: Context?, project: Project): ProjectHealthReport {
        val projectDir = resolveDirectory(context, project)
        if (projectDir == null || !projectDir.exists() || !projectDir.isDirectory) {
            return ProjectHealthReport(
                status = ProjectHealthStatus.STORAGE_ERROR,
                directoryExists = false,
                entryPointExists = false,
                entryPointReadable = false,
                metadataMatchesFiles = false,
                actualFileCount = 0,
                actualSizeBytes = 0L,
                diagnostics = listOf("Project directory is missing on internal storage.")
            )
        }

        val (actualCount, actualBytes) = ProjectStorageManager.calculateStats(projectDir)
        val diagnostics = mutableListOf<String>()

        if (actualCount == 0) {
            return ProjectHealthReport(
                status = ProjectHealthStatus.MISSING_FILES,
                directoryExists = true,
                entryPointExists = false,
                entryPointReadable = false,
                metadataMatchesFiles = false,
                actualFileCount = 0,
                actualSizeBytes = 0L,
                diagnostics = listOf("Project directory contains no files.")
            )
        }

        val entryResult = ProjectFileManager.resolveSafePath(projectDir, project.mainHtmlRelativePath)
        val entryFile = entryResult.getOrNull()
        val entryExists = entryFile != null && entryFile.exists() && entryFile.isFile
        val entryReadable = entryExists && entryFile!!.canRead()

        if (!entryExists) {
            diagnostics.add("Entry point '${project.mainHtmlRelativePath}' is missing.")
            return ProjectHealthReport(
                status = ProjectHealthStatus.INVALID_ENTRY_POINT,
                directoryExists = true,
                entryPointExists = false,
                entryPointReadable = false,
                metadataMatchesFiles = actualCount == project.fileCount && actualBytes == project.totalSizeBytes,
                actualFileCount = actualCount,
                actualSizeBytes = actualBytes,
                diagnostics = diagnostics
            )
        }

        if (!entryReadable) {
            diagnostics.add("Entry point '${project.mainHtmlRelativePath}' cannot be read.")
            return ProjectHealthReport(
                status = ProjectHealthStatus.INVALID_ENTRY_POINT,
                directoryExists = true,
                entryPointExists = true,
                entryPointReadable = false,
                metadataMatchesFiles = actualCount == project.fileCount && actualBytes == project.totalSizeBytes,
                actualFileCount = actualCount,
                actualSizeBytes = actualBytes,
                diagnostics = diagnostics
            )
        }

        val metadataMatches = (actualCount == project.fileCount && actualBytes == project.totalSizeBytes)
        if (!metadataMatches) {
            diagnostics.add("Metadata mismatch: database records ${project.fileCount} file(s), disk has $actualCount file(s).")
        }

        val isWebHtmlEntry = project.mainHtmlRelativePath.endsWith(".html", ignoreCase = true) ||
            project.mainHtmlRelativePath.endsWith(".htm", ignoreCase = true)

        val missingLocalAssets = if (isWebHtmlEntry && project.projectCategory.supportsLivePreview) {
            detectMissingLocalAssets(projectDir, entryFile!!)
        } else {
            emptyList()
        }
        if (missingLocalAssets.isNotEmpty()) {
            diagnostics.add("${missingLocalAssets.size} referenced local asset(s) not found on disk.")
        }

        val status = if (!metadataMatches || missingLocalAssets.isNotEmpty()) {
            ProjectHealthStatus.WARNING
        } else {
            ProjectHealthStatus.HEALTHY
        }

        if (status == ProjectHealthStatus.HEALTHY) {
            diagnostics.add("All project files and entry point verified (${project.projectCategory.displayName}).")
        }

        return ProjectHealthReport(
            status = status,
            directoryExists = true,
            entryPointExists = true,
            entryPointReadable = true,
            metadataMatchesFiles = metadataMatches,
            actualFileCount = actualCount,
            actualSizeBytes = actualBytes,
            missingLocalAssets = missingLocalAssets,
            diagnostics = diagnostics
        )
    }

    fun findOrphanDirectories(context: Context, knownUuids: Set<String>): List<String> {
        val root = ProjectStorageManager.getProjectsRoot(context)
        return root.listFiles()
            ?.filter { it.isDirectory && it.name !in knownUuids }
            ?.map { it.name }
            ?: emptyList()
    }

    private fun detectMissingLocalAssets(projectDir: File, entryFile: File): List<String> {
        val html = try {
            if (entryFile.length() > 5 * 1024 * 1024L) return emptyList()
            entryFile.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return emptyList()
        }

        val missing = mutableListOf<String>()
        val matcher = LINK_PATTERN.matcher(html)
        val entryParent = entryFile.parentFile ?: projectDir

        while (matcher.find()) {
            val rawUrl = matcher.group(1)?.trim() ?: continue
            if (rawUrl.isEmpty() ||
                rawUrl.startsWith("http://", ignoreCase = true) ||
                rawUrl.startsWith("https://", ignoreCase = true) ||
                rawUrl.startsWith("//") ||
                rawUrl.startsWith("data:", ignoreCase = true) ||
                rawUrl.startsWith("mailto:", ignoreCase = true) ||
                rawUrl.startsWith("tel:", ignoreCase = true) ||
                rawUrl.startsWith("javascript:", ignoreCase = true) ||
                rawUrl.startsWith("#")
            ) {
                continue
            }
            val cleanRef = rawUrl.substringBefore('?').substringBefore('#').trim()
            if (cleanRef.isEmpty()) continue

            val candidate = if (cleanRef.startsWith("/")) {
                File(projectDir, cleanRef.trimStart('/'))
            } else {
                File(entryParent, cleanRef)
            }

            val isInsideProject = try {
                val rootCanon = projectDir.canonicalPath
                val candCanon = candidate.canonicalPath
                candCanon == rootCanon || candCanon.startsWith(rootCanon + File.separator)
            } catch (_: Exception) {
                false
            }

            if (!isInsideProject || !candidate.exists()) {
                if (!missing.contains(cleanRef)) {
                    missing.add(cleanRef)
                }
            }
        }
        return missing.take(15)
    }

    private fun resolveDirectory(context: Context?, project: Project): File? {
        if (project.internalProjectPath.isNotBlank()) {
            val dir = File(project.internalProjectPath)
            if (dir.exists() && dir.isDirectory) return dir
        }
        if (context != null && project.projectUuid.isNotBlank()) {
            val fallback = File(ProjectStorageManager.getProjectsRoot(context), project.projectUuid)
            if (fallback.exists() && fallback.isDirectory) return fallback
        }
        return null
    }
}
