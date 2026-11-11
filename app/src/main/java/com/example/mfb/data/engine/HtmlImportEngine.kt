package com.example.mfb.data.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.mfb.core.security.ImportSecurityException
import com.example.mfb.domain.model.ImportStatus
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.regex.Pattern

sealed class HtmlImportResult {
    data class Success(val project: Project, val warnings: List<String> = emptyList()) : HtmlImportResult()
    data class Error(val message: String, val exception: Exception? = null) : HtmlImportResult()
}

object HtmlImportEngine {
    const val MAX_HTML_FILE_SIZE = 25 * 1024 * 1024L // 25 MB

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        customProjectName: String? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): HtmlImportResult = withContext(Dispatchers.IO) {
        val (originalFileName, fileSize) = getFileInfo(context, uri)

        if (fileSize > MAX_HTML_FILE_SIZE) {
            return@withContext HtmlImportResult.Error(
                "HTML file size exceeds 25 MB limit",
                ImportSecurityException.MaxFileSizeExceededException(originalFileName, fileSize, MAX_HTML_FILE_SIZE)
            )
        }

        try {
            onProgress(0.15f, "Reading HTML file...")
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: return@withContext HtmlImportResult.Error("Unable to open HTML file stream")

            val htmlContent = inputStream.bufferedReader().use { it.readText() }

            onProgress(0.40f, "Validating HTML structure & markup...")
            if (htmlContent.isBlank()) {
                return@withContext HtmlImportResult.Error(
                    "HTML file is empty",
                    ImportSecurityException.InvalidHtmlFileException("The file is empty")
                )
            }

            if (!isValidHtml(htmlContent)) {
                return@withContext HtmlImportResult.Error(
                    "Selected file does not appear to be valid HTML",
                    ImportSecurityException.InvalidHtmlFileException("Missing HTML tags or structure")
                )
            }

            onProgress(0.70f, "Checking asset dependencies...")
            val missingAssets = detectExternalLocalAssetReferences(htmlContent)

            onProgress(0.85f, "Creating project workspace...")
            val projectUuid = UUID.randomUUID().toString()
            val projectDir = ProjectStorageManager.getProjectDir(context, projectUuid)
            val indexFile = File(projectDir, "index.html")

            FileOutputStream(indexFile).use { fos ->
                fos.write(htmlContent.toByteArray(Charsets.UTF_8))
            }

            val projectName = customProjectName?.takeIf { it.isNotBlank() }
                ?: originalFileName.substringBeforeLast('.').ifBlank { "HTML Project" }

            val project = Project(
                projectUuid = projectUuid,
                name = projectName,
                description = "Single HTML project from $originalFileName",
                packageId = "com.mfb.app.${projectName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "app" }}",
                versionName = "1.0.0",
                versionCode = 1,
                originalFileName = originalFileName,
                sourceType = SourceType.HTML,
                mainHtmlRelativePath = "index.html",
                internalProjectPath = projectDir.absolutePath,
                fileCount = 1,
                totalSizeBytes = indexFile.length(),
                importStatus = ImportStatus.SUCCESS,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                missingLocalAssets = missingAssets
            )

            ProjectStorageManager.writeMetadata(projectDir, project)
            onProgress(1.0f, "HTML project successfully imported!")
            HtmlImportResult.Success(project, missingAssets)
        } catch (e: Exception) {
            HtmlImportResult.Error("Failed to import HTML: ${e.localizedMessage ?: "Unknown error"}", e)
        }
    }

    private fun isValidHtml(content: String): Boolean {
        val lower = content.lowercase().trimStart()
        return lower.contains("<!doctype html") ||
                lower.contains("<html") ||
                lower.contains("<body") ||
                lower.contains("<head") ||
                lower.contains("<div") ||
                lower.contains("<script") ||
                lower.contains("<p>") ||
                lower.contains("<h1") ||
                (lower.startsWith("<") && lower.contains(">"))
    }

    private fun detectExternalLocalAssetReferences(html: String): List<String> {
        val missing = mutableListOf<String>()

        // Find href and src attributes
        val pattern = Pattern.compile("""(?:src|href)\s*=\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)

        while (matcher.find()) {
            val url = matcher.group(1)?.trim() ?: continue
            // Skip http, https, data:, mailto, tel, javascript, hash links
            if (url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("//") ||
                url.startsWith("data:", ignoreCase = true) ||
                url.startsWith("mailto:", ignoreCase = true) ||
                url.startsWith("tel:", ignoreCase = true) ||
                url.startsWith("javascript:", ignoreCase = true) ||
                url.startsWith("#")
            ) {
                continue
            }
            if (url.isNotBlank() && !missing.contains(url)) {
                missing.add(url)
            }
        }
        return missing.take(15) // Limit reporting to first 15 assets
    }

    private fun getFileInfo(context: Context, uri: Uri): Pair<String, Long> {
        var name = "index.html"
        var size = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex) ?: name
                }
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return Pair(name, size)
    }
}
