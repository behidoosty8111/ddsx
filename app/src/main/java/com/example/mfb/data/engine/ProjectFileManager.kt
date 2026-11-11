package com.example.mfb.data.engine

import android.graphics.BitmapFactory
import android.os.StatFs
import com.example.mfb.domain.model.FileCategory
import com.example.mfb.domain.model.FilePreviewContent
import com.example.mfb.domain.model.ProjectFileItem
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ProjectFileManager {

    val ALLOWED_CREATE_EXTENSIONS = setOf("html", "htm", "css", "js", "json", "txt", "xml", "md")
    private val INVALID_NAME_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

    fun validateItemName(name: String): Result<String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Name cannot be empty."))
        }
        if (trimmed == "." || trimmed == "..") {
            return Result.failure(IllegalArgumentException("Invalid name: '$trimmed'."))
        }
        if (trimmed.length > 255) {
            return Result.failure(IllegalArgumentException("Name exceeds 255 characters."))
        }
        if (trimmed.any { it.code < 32 || it in INVALID_NAME_CHARS }) {
            return Result.failure(IllegalArgumentException("Name contains invalid characters."))
        }
        if (trimmed == ProjectStorageManager.METADATA_FILE_NAME) {
            return Result.failure(IllegalArgumentException("Reserved system filename."))
        }
        return Result.success(trimmed)
    }

    fun resolveSafePath(projectDir: File, relativePath: String): Result<File> {
        if (relativePath.contains('\u0000')) {
            return Result.failure(SecurityException("Null byte in path."))
        }
        val normalized = relativePath.replace('\\', '/').trimStart('/')
        if (normalized.isEmpty()) {
            return Result.success(projectDir)
        }
        val rawSegments = normalized.split('/').filter { it.isNotEmpty() }
        if (rawSegments.any { it == ".." }) {
            return Result.failure(SecurityException("Path traversal is not allowed."))
        }
        val segments = rawSegments.filter { it != "." }
        if (segments.isEmpty()) {
            return Result.success(projectDir)
        }
        return try {
            val rootCanonical = projectDir.canonicalPath
            val target = File(projectDir, segments.joinToString(File.separator))
            val targetCanonical = target.canonicalPath
            if (targetCanonical != rootCanonical && !targetCanonical.startsWith(rootCanonical + File.separator)) {
                Result.failure(SecurityException("Access outside project directory is forbidden."))
            } else {
                Result.success(target)
            }
        } catch (e: IOException) {
            Result.failure(SecurityException("Failed to validate path: ${e.message}"))
        }
    }

    private fun toRelativePath(projectDir: File, target: File): String {
        val rootPath = projectDir.canonicalPath
        val targetPath = target.canonicalPath
        if (targetPath == rootPath) return ""
        return targetPath.removePrefix(rootPath + File.separator).replace(File.separatorChar, '/')
    }

    private fun toProjectFileItem(projectDir: File, file: File): ProjectFileItem {
        val relPath = toRelativePath(projectDir, file)
        val isDir = file.isDirectory
        val ext = if (isDir) "" else file.extension.lowercase()
        val childCount = if (isDir) {
            file.listFiles()?.count { it.name != ProjectStorageManager.METADATA_FILE_NAME } ?: 0
        } else 0
        return ProjectFileItem(
            name = file.name,
            relativePath = relPath,
            isDirectory = isDir,
            sizeBytes = if (isDir) 0L else file.length(),
            lastModified = file.lastModified(),
            extension = ext,
            childCount = childCount,
            category = if (isDir) FileCategory.ALL else FileCategory.fromExtension(ext)
        )
    }

    fun listDirectory(projectDir: File, relativePath: String = ""): Result<List<ProjectFileItem>> {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return Result.failure(IOException("Project directory does not exist."))
        }
        val targetDir = resolveSafePath(projectDir, relativePath).getOrElse { return Result.failure(it) }
        if (!targetDir.exists() || !targetDir.isDirectory) {
            return Result.failure(IOException("Directory not found: $relativePath"))
        }
        val children = targetDir.listFiles() ?: emptyArray()
        val items = children
            .filter { !(targetDir.canonicalPath == projectDir.canonicalPath && it.name == ProjectStorageManager.METADATA_FILE_NAME) }
            .map { toProjectFileItem(projectDir, it) }
            .sortedWith(compareByDescending<ProjectFileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
        return Result.success(items)
    }

    fun searchFiles(
        projectDir: File,
        query: String,
        category: FileCategory = FileCategory.ALL
    ): Result<List<ProjectFileItem>> {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return Result.failure(IOException("Project directory does not exist."))
        }
        val trimmedQuery = query.trim().lowercase()
        val results = mutableListOf<ProjectFileItem>()
        projectDir.walkTopDown().forEach { file ->
            if (file.canonicalPath == projectDir.canonicalPath) return@forEach
            if (file.parentFile?.canonicalPath == projectDir.canonicalPath && file.name == ProjectStorageManager.METADATA_FILE_NAME) {
                return@forEach
            }
            val item = toProjectFileItem(projectDir, file)
            val matchesQuery = trimmedQuery.isEmpty() ||
                item.name.lowercase().contains(trimmedQuery) ||
                item.relativePath.lowercase().contains(trimmedQuery)
            val matchesCategory = category == FileCategory.ALL ||
                (!item.isDirectory && item.category == category)
            if (matchesQuery && matchesCategory) {
                results.add(item)
            }
        }
        return Result.success(
            results.sortedWith(compareByDescending<ProjectFileItem> { it.isDirectory }.thenBy { it.relativePath.lowercase() })
        )
    }

    fun createFolder(projectDir: File, parentRelativePath: String, folderName: String): Result<ProjectFileItem> {
        val validName = validateItemName(folderName).getOrElse { return Result.failure(it) }
        val parentDir = resolveSafePath(projectDir, parentRelativePath).getOrElse { return Result.failure(it) }
        if (!parentDir.exists() || !parentDir.isDirectory) {
            return Result.failure(IOException("Parent directory does not exist."))
        }
        val newDir = resolveSafePath(
            projectDir,
            if (parentRelativePath.isBlank()) validName else "$parentRelativePath/$validName"
        ).getOrElse { return Result.failure(it) }

        if (newDir.exists()) {
            return Result.failure(IOException("A file or folder named '$validName' already exists."))
        }
        if (!newDir.mkdir()) {
            return Result.failure(IOException("Failed to create folder '$validName'."))
        }
        return Result.success(toProjectFileItem(projectDir, newDir))
    }

    fun createEmptyFile(projectDir: File, parentRelativePath: String, fileName: String): Result<ProjectFileItem> {
        val validName = validateItemName(fileName).getOrElse { return Result.failure(it) }
        val ext = validName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty() || ext !in ALLOWED_CREATE_EXTENSIONS) {
            return Result.failure(
                IllegalArgumentException(
                    "Unsupported file extension. Allowed: ${ALLOWED_CREATE_EXTENSIONS.joinToString(", ") { ".$it" }}"
                )
            )
        }
        val parentDir = resolveSafePath(projectDir, parentRelativePath).getOrElse { return Result.failure(it) }
        if (!parentDir.exists() || !parentDir.isDirectory) {
            return Result.failure(IOException("Parent directory does not exist."))
        }
        val newFile = resolveSafePath(
            projectDir,
            if (parentRelativePath.isBlank()) validName else "$parentRelativePath/$validName"
        ).getOrElse { return Result.failure(it) }

        if (newFile.exists()) {
            return Result.failure(IOException("A file or folder named '$validName' already exists."))
        }
        return try {
            if (!newFile.createNewFile()) {
                Result.failure(IOException("Failed to create file '$validName'."))
            } else {
                Result.success(toProjectFileItem(projectDir, newFile))
            }
        } catch (e: IOException) {
            Result.failure(IOException("Failed to create file: ${e.message}"))
        }
    }

    fun renameItem(projectDir: File, itemRelativePath: String, newName: String): Result<ProjectFileItem> {
        if (itemRelativePath.isBlank()) {
            return Result.failure(IllegalArgumentException("Cannot rename project root."))
        }
        val validName = validateItemName(newName).getOrElse { return Result.failure(it) }
        val source = resolveSafePath(projectDir, itemRelativePath).getOrElse { return Result.failure(it) }
        if (!source.exists()) {
            return Result.failure(IOException("Item does not exist."))
        }
        if (source.canonicalPath == projectDir.canonicalPath) {
            return Result.failure(SecurityException("Cannot rename project root."))
        }
        val parentRel = itemRelativePath.substringBeforeLast('/', "")
        val targetRel = if (parentRel.isEmpty()) validName else "$parentRel/$validName"
        val target = resolveSafePath(projectDir, targetRel).getOrElse { return Result.failure(it) }
        if (target.exists()) {
            return Result.failure(IOException("An item named '$validName' already exists in this folder."))
        }
        if (!source.renameTo(target)) {
            return Result.failure(IOException("Failed to rename '${source.name}' to '$validName'."))
        }
        return Result.success(toProjectFileItem(projectDir, target))
    }

    fun deleteItem(projectDir: File, itemRelativePath: String): Result<Unit> {
        if (itemRelativePath.isBlank()) {
            return Result.failure(SecurityException("Cannot delete project root directory."))
        }
        val target = resolveSafePath(projectDir, itemRelativePath).getOrElse { return Result.failure(it) }
        if (target.canonicalPath == projectDir.canonicalPath) {
            return Result.failure(SecurityException("Cannot delete project root directory."))
        }
        if (target.parentFile?.canonicalPath == projectDir.canonicalPath && target.name == ProjectStorageManager.METADATA_FILE_NAME) {
            return Result.failure(SecurityException("Cannot delete protected project metadata."))
        }
        if (!target.exists()) {
            return Result.failure(IOException("Item does not exist."))
        }
        val deleted = if (target.isDirectory) target.deleteRecursively() else target.delete()
        return if (deleted && !target.exists()) {
            Result.success(Unit)
        } else {
            Result.failure(IOException("Failed to completely delete '${target.name}'."))
        }
    }

    fun getFileInfo(projectDir: File, itemRelativePath: String): Result<ProjectFileItem> {
        val target = resolveSafePath(projectDir, itemRelativePath).getOrElse { return Result.failure(it) }
        if (!target.exists()) {
            return Result.failure(IOException("Item does not exist."))
        }
        return Result.success(toProjectFileItem(projectDir, target))
    }

    fun duplicateProjectFiles(sourceDir: File, targetDir: File): Result<Pair<Int, Long>> {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            return Result.failure(IOException("Source project directory does not exist."))
        }
        val (_, totalBytes) = ProjectStorageManager.calculateStats(sourceDir)
        try {
            val statFs = StatFs(sourceDir.parentFile?.absolutePath ?: sourceDir.absolutePath)
            val totalFsBytes = statFs.totalBytes
            val availableBytes = statFs.availableBytes
            if (totalFsBytes > 0L && availableBytes < totalBytes + 64 * 1024L) {
                return Result.failure(IOException("Insufficient storage space to duplicate project."))
            }
        } catch (_: Exception) {
            // StatFs may throw on non-mounted paths in some test environments; proceed with copy check
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return Result.failure(IOException("Unable to create target directory for duplicate project."))
        }

        return try {
            var copiedCount = 0
            var copiedBytes = 0L
            val rootCanonical = sourceDir.canonicalPath
            sourceDir.walkTopDown().forEach { file ->
                if (file.canonicalPath == rootCanonical) return@forEach
                if (file.parentFile?.canonicalPath == rootCanonical && file.name == ProjectStorageManager.METADATA_FILE_NAME) {
                    return@forEach
                }
                val rel = file.canonicalPath.removePrefix(rootCanonical + File.separator)
                val dest = File(targetDir, rel)
                if (file.isDirectory) {
                    if (!dest.exists() && !dest.mkdirs()) {
                        throw IOException("Failed to create directory: $rel")
                    }
                } else {
                    dest.parentFile?.let { parent ->
                        if (!parent.exists() && !parent.mkdirs()) {
                            throw IOException("Failed to create parent directory for: $rel")
                        }
                    }
                    file.inputStream().use { input ->
                        dest.outputStream().use { output ->
                            val bytes = input.copyTo(output)
                            copiedBytes += bytes
                        }
                    }
                    copiedCount++
                }
            }
            Result.success(Pair(copiedCount, copiedBytes))
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            Result.failure(IOException("Failed to duplicate project files: ${e.message}", e))
        }
    }

    fun exportProjectToZip(projectDir: File, outputStream: OutputStream): Result<Int> {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return Result.failure(IOException("Project directory does not exist."))
        }
        var exportedCount = 0
        return try {
            val rootCanonical = projectDir.canonicalPath
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
                val buffer = ByteArray(8192)
                projectDir.walkTopDown().forEach { file ->
                    if (file.canonicalPath == rootCanonical) return@forEach
                    if (file.parentFile?.canonicalPath == rootCanonical && file.name == ProjectStorageManager.METADATA_FILE_NAME) {
                        return@forEach
                    }
                    val rel = file.canonicalPath
                        .removePrefix(rootCanonical + File.separator)
                        .replace(File.separatorChar, '/')
                    if (file.isDirectory) {
                        val entryName = if (rel.endsWith("/")) rel else "$rel/"
                        zos.putNextEntry(ZipEntry(entryName))
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(rel))
                        BufferedInputStream(FileInputStream(file)).use { bis ->
                            var len: Int
                            while (bis.read(buffer).also { len = it } != -1) {
                                zos.write(buffer, 0, len)
                            }
                        }
                        zos.closeEntry()
                        exportedCount++
                    }
                }
                zos.finish()
                zos.flush()
            }
            Result.success(exportedCount)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to export project archive: ${e.message}", e))
        }
    }

    fun findHtmlEntryCandidates(projectDir: File): List<String> {
        if (!projectDir.exists() || !projectDir.isDirectory) return emptyList()
        val rootCanonical = projectDir.canonicalPath
        return projectDir.walkTopDown()
            .filter { it.isFile && (it.extension.equals("html", ignoreCase = true) || it.extension.equals("htm", ignoreCase = true)) }
            .map { file ->
                file.canonicalPath.removePrefix(rootCanonical + File.separator).replace(File.separatorChar, '/')
            }
            .sorted()
            .toList()
    }

    private val SUPPORTED_TEXT_EXTENSIONS = setOf("html", "htm", "css", "js", "mjs", "ts", "json", "xml", "txt", "md", "svg")
    private val SUPPORTED_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    fun readFilePreview(
        projectDir: File,
        relativePath: String,
        maxTextBytes: Long = 256 * 1024L
    ): Result<FilePreviewContent> {
        val target = resolveSafePath(projectDir, relativePath).getOrElse { return Result.failure(it) }
        if (!target.exists() || !target.isFile) {
            return Result.failure(IOException("File does not exist: $relativePath"))
        }
        val ext = target.extension.lowercase()
        return when {
            ext in SUPPORTED_TEXT_EXTENSIONS -> {
                try {
                    val fileLength = target.length()
                    val truncated = fileLength > maxTextBytes
                    val bytesToRead = minOf(fileLength, maxTextBytes).toInt()
                    val buffer = ByteArray(bytesToRead)
                    FileInputStream(target).use { fis ->
                        var totalRead = 0
                        while (totalRead < bytesToRead) {
                            val read = fis.read(buffer, totalRead, bytesToRead - totalRead)
                            if (read == -1) break
                            totalRead += read
                        }
                    }
                    // Reject binary content containing null bytes
                    if (buffer.any { it == 0.toByte() }) {
                        Result.success(FilePreviewContent.Unsupported(target.name, "File appears to contain binary data."))
                    } else {
                        Result.success(
                            FilePreviewContent.TextPreview(
                                name = target.name,
                                relativePath = relativePath,
                                content = String(buffer, Charsets.UTF_8),
                                truncated = truncated
                            )
                        )
                    }
                } catch (e: Exception) {
                    Result.success(FilePreviewContent.Unsupported(target.name, "Unable to decode text: ${e.message}"))
                }
            }
            ext in SUPPORTED_IMAGE_EXTENSIONS -> {
                try {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(target.absolutePath, opts)
                    if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                        Result.success(FilePreviewContent.Unsupported(target.name, "Unsupported or corrupted image format."))
                    } else {
                        Result.success(
                            FilePreviewContent.ImagePreview(
                                name = target.name,
                                relativePath = relativePath,
                                absolutePath = target.absolutePath,
                                width = opts.outWidth,
                                height = opts.outHeight
                            )
                        )
                    }
                } catch (e: Exception) {
                    Result.success(FilePreviewContent.Unsupported(target.name, "Unable to inspect image: ${e.message}"))
                }
            }
            else -> Result.success(
                FilePreviewContent.Unsupported(target.name, "Preview is not supported for .${ext.ifEmpty { "unknown" }} files.")
            )
        }
    }
}
