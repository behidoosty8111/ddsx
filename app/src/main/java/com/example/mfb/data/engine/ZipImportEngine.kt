package com.example.mfb.data.engine

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.example.mfb.core.security.ImportSecurityException
import com.example.mfb.core.security.ZipSecurityValidator
import com.example.mfb.domain.model.ImportStatus
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.ProjectAnalysisReport
import com.example.mfb.domain.model.ProjectCategory
import com.example.mfb.domain.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

sealed class ZipImportResult {
    data class Success(val project: Project) : ZipImportResult()
    data class NeedsEntryPointChoice(
        val tempDirName: String,
        val candidates: List<String>,
        val originalFileName: String,
        val suggestedName: String
    ) : ZipImportResult()
    data class Error(val message: String, val exception: Exception? = null) : ZipImportResult()
}

class CountingInputStream(private val wrapped: InputStream) : InputStream() {
    var bytesRead: Long = 0
        private set

    override fun read(): Int {
        val b = wrapped.read()
        if (b != -1) bytesRead++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = wrapped.read(b, off, len)
        if (n != -1) bytesRead += n
        return n
    }

    override fun close() {
        wrapped.close()
    }
}

object ZipImportEngine {

    private const val TAG = "MfbZipImport"

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        customProjectName: String? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ZipImportResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "ZIP selected: uri=$uri")
        onProgress(0.06f, "Reading selected ZIP metadata...")

        val (originalFileName, archiveSize) = getFileInfo(context, uri)
        Log.d(TAG, "Metadata resolved: name=$originalFileName, size=$archiveSize bytes")

        if (archiveSize > ZipSecurityValidator.MAX_ARCHIVE_SIZE) {
            val err = ImportSecurityException.MaxArchiveSizeExceededException(
                archiveSize,
                ZipSecurityValidator.MAX_ARCHIVE_SIZE
            )
            Log.e(TAG, "Archive exceeds size limit: $archiveSize", err)
            return@withContext ZipImportResult.Error(
                "Archive size exceeds safety limit of ${ZipSecurityValidator.MAX_ARCHIVE_SIZE / (1024 * 1024)} MB",
                err
            )
        }

        val tempUuid = UUID.randomUUID().toString()
        val tempDir = File(context.cacheDir, "temp_zip_$tempUuid")
        if (!tempDir.mkdirs()) {
            Log.e(TAG, "Failed to create staging directory: ${tempDir.absolutePath}")
            return@withContext ZipImportResult.Error("Failed to initialize staging directory")
        }

        try {
            onProgress(0.10f, "Validating ZIP archive...")
            Log.d(TAG, "Validating archive header for $originalFileName")

            validateZipSignature(context, uri)

            onProgress(0.15f, "Extracting files...")
            Log.d(TAG, "Starting extraction into ${tempDir.name}")

            val entryCount = try {
                extractArchive(context, uri, tempDir, archiveSize, StandardCharsets.UTF_8, onProgress)
            } catch (malformed: IllegalArgumentException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Log.w(TAG, "UTF-8 ZipInputStream failed, retrying with ISO-8859-1", malformed)
                    tempDir.deleteRecursively()
                    tempDir.mkdirs()
                    extractArchive(context, uri, tempDir, archiveSize, StandardCharsets.ISO_8859_1, onProgress)
                } else {
                    throw malformed
                }
            }

            if (entryCount == 0) {
                tempDir.deleteRecursively()
                Log.w(TAG, "Archive has 0 entries: $originalFileName")
                return@withContext ZipImportResult.Error("ZIP archive is empty (contains no files).")
            }

            onProgress(0.88f, "Analyzing project structure...")
            Log.d(TAG, "Extracted $entryCount entries. Running UniversalProjectAnalyzer...")

            val analysis = UniversalProjectAnalyzer.analyze(tempDir)
            Log.d(TAG, "Detected category=${analysis.category}, tech=${analysis.techSummary}, entry=${analysis.entryPointPath}")

            val suggestedName = customProjectName?.takeIf { it.isNotBlank() }
                ?: originalFileName.substringBeforeLast('.').ifBlank { null }
                ?: analysis.detectedAppName
                ?: "Imported Project"

            if (analysis.category == ProjectCategory.UNKNOWN) {
                tempDir.deleteRecursively()
                val err = ImportSecurityException.NoHtmlEntryPointException()
                Log.w(TAG, "No recognized project structure found in $originalFileName", err)
                ZipImportResult.Error(
                    "Unrecognized project archive. Expected Android (Kotlin/Java/Gradle), React, Vite, Capacitor, or HTML files.",
                    err
                )
            } else if (analysis.category == ProjectCategory.WEB_HTML && analysis.htmlCandidates.size > 1) {
                // Re-check HtmlEntryDetector for exact shallowest-depth multiple-candidate prompt
                when (val detection = HtmlEntryDetector.detectEntryPoint(tempDir)) {
                    is HtmlDetectionResult.MultipleCandidates -> {
                        Log.d(TAG, "Multiple HTML entry candidates found: ${detection.candidates}")
                        ZipImportResult.NeedsEntryPointChoice(
                            tempDirName = tempDir.name,
                            candidates = detection.candidates,
                            originalFileName = originalFileName,
                            suggestedName = suggestedName
                        )
                    }
                    else -> {
                        finalizeProject(
                            context = context,
                            stagedDir = tempDir,
                            projectName = suggestedName,
                            originalFileName = originalFileName,
                            mainHtmlPath = analysis.entryPointPath,
                            analysis = analysis,
                            onProgress = onProgress
                        )
                    }
                }
            } else {
                Log.d(TAG, "Auto-finalizing ${analysis.category} project with entry: ${analysis.entryPointPath}")
                finalizeProject(
                    context = context,
                    stagedDir = tempDir,
                    projectName = suggestedName,
                    originalFileName = originalFileName,
                    mainHtmlPath = analysis.entryPointPath,
                    analysis = analysis,
                    onProgress = onProgress
                )
            }
        } catch (e: ImportSecurityException) {
            tempDir.deleteRecursively()
            Log.e(TAG, "Security validation failed: ${e.message}", e)
            ZipImportResult.Error(e.message ?: "Security validation error", e)
        } catch (e: ZipException) {
            tempDir.deleteRecursively()
            Log.e(TAG, "Corrupted ZIP archive: ${e.message}", e)
            ZipImportResult.Error("Corrupted or invalid ZIP archive: ${e.localizedMessage ?: "Malformed ZIP"}", e)
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            Log.e(TAG, "ZIP extraction failed: ${e.message}", e)
            ZipImportResult.Error("Extraction failed: ${e.localizedMessage ?: e.javaClass.simpleName}", e)
        }
    }

    private fun validateZipSignature(context: Context, uri: Uri) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw ImportSecurityException.CorruptedArchiveException("Unable to open selected ZIP stream.")
        stream.use { raw ->
            val header = ByteArray(4)
            var totalRead = 0
            while (totalRead < 4) {
                val r = raw.read(header, totalRead, 4 - totalRead)
                if (r == -1) break
                totalRead += r
            }
            if (totalRead < 4) {
                throw ImportSecurityException.CorruptedArchiveException("Selected file is empty or truncated.")
            }
            val isPkSignature = header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                (
                    (header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) || // Local file header
                        (header[2] == 0x05.toByte() && header[3] == 0x06.toByte()) || // Empty archive EOCD
                        (header[2] == 0x07.toByte() && header[3] == 0x08.toByte()) // Spanned archive
                    )
            if (!isPkSignature) {
                throw ImportSecurityException.CorruptedArchiveException("Selected file is not a valid ZIP archive.")
            }
        }
    }

    private fun extractArchive(
        context: Context,
        uri: Uri,
        tempDir: File,
        archiveSize: Long,
        charset: Charset,
        onProgress: (Float, String) -> Unit
    ): Int {
        val rawStream = context.contentResolver.openInputStream(uri)
            ?: throw ImportSecurityException.CorruptedArchiveException("Unable to open archive stream")

        val countingStream = CountingInputStream(BufferedInputStream(rawStream))
        val zipInputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ZipInputStream(countingStream, charset)
        } else {
            ZipInputStream(countingStream)
        }

        var entryCount = 0
        var totalExtractedBytes = 0L
        val buffer = ByteArray(8192)

        zipInputStream.use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entryCount++
                val entryName = entry.name

                // Security check: Zip Slip defense & Windows/leading separator normalization
                val destFile = ZipSecurityValidator.resolveAndValidateDestination(tempDir, entryName)

                if (destFile == tempDir || entry.isDirectory) {
                    if (!destFile.exists()) {
                        destFile.mkdirs()
                    }
                } else {
                    destFile.parentFile?.let { parent ->
                        if (!parent.exists()) parent.mkdirs()
                    }
                    var currentFileBytes = 0L

                    FileOutputStream(destFile).use { fos ->
                        var readBytes: Int
                        while (zis.read(buffer).also { readBytes = it } != -1) {
                            currentFileBytes += readBytes
                            totalExtractedBytes += readBytes

                            ZipSecurityValidator.checkMetrics(
                                entryCount = entryCount,
                                totalExtractedBytes = totalExtractedBytes,
                                compressedBytesRead = countingStream.bytesRead,
                                currentFileBytes = currentFileBytes,
                                currentFileName = entryName
                            )

                            fos.write(buffer, 0, readBytes)
                        }
                    }
                }

                val progressFraction = if (archiveSize > 0) {
                    (countingStream.bytesRead.toFloat() / archiveSize.toFloat()).coerceIn(0.15f, 0.85f)
                } else {
                    0.5f
                }
                val displayEntry = entryName.replace('\\', '/').substringAfterLast('/')
                onProgress(progressFraction, "Extracting: $displayEntry")

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return entryCount
    }

    suspend fun completeWithSelectedEntryPoint(
        context: Context,
        tempDirName: String,
        selectedEntryPoint: String,
        projectName: String,
        originalFileName: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ZipImportResult = withContext(Dispatchers.IO) {
        val stagedDir = File(context.cacheDir, tempDirName)
        if (!stagedDir.exists()) {
            return@withContext ZipImportResult.Error("Temporary import session expired. Please re-import the file.")
        }

        try {
            val chosenFile = ZipSecurityValidator.resolveAndValidateDestination(stagedDir, selectedEntryPoint)
            if (!chosenFile.exists() || !chosenFile.isFile) {
                return@withContext ZipImportResult.Error("Selected entry point '$selectedEntryPoint' not found in archive")
            }

            val analysis = UniversalProjectAnalyzer.analyze(stagedDir)
            finalizeProject(
                context = context,
                stagedDir = stagedDir,
                projectName = projectName,
                originalFileName = originalFileName,
                mainHtmlPath = selectedEntryPoint.replace('\\', '/').trimStart('/'),
                analysis = analysis,
                onProgress = onProgress
            )
        } catch (e: Exception) {
            stagedDir.deleteRecursively()
            Log.e(TAG, "Finalizing project failed: ${e.message}", e)
            ZipImportResult.Error("Finalizing project failed: ${e.localizedMessage ?: e.javaClass.simpleName}", e)
        }
    }

    private fun finalizeProject(
        context: Context,
        stagedDir: File,
        projectName: String,
        originalFileName: String,
        mainHtmlPath: String,
        analysis: ProjectAnalysisReport,
        onProgress: (Float, String) -> Unit
    ): ZipImportResult {
        onProgress(0.93f, "Saving project files to internal storage...")
        Log.d(TAG, "Committing staged directory to internal storage...")

        val projectUuid = UUID.randomUUID().toString()
        val projectsRoot = ProjectStorageManager.getProjectsRoot(context)
        val targetProjectDir = File(projectsRoot, projectUuid)

        try {
            val movedAtomically = stagedDir.renameTo(targetProjectDir)
            if (!movedAtomically) {
                if (!targetProjectDir.exists() && !targetProjectDir.mkdirs()) {
                    throw IllegalStateException("Unable to create project directory.")
                }
                stagedDir.copyRecursively(targetProjectDir, overwrite = true)
                stagedDir.deleteRecursively()
            }
        } catch (e: Exception) {
            targetProjectDir.deleteRecursively()
            stagedDir.deleteRecursively()
            throw e
        }

        val (fileCount, totalSizeBytes) = ProjectStorageManager.calculateStats(targetProjectDir)
        val normalizedEntry = mainHtmlPath.replace('\\', '/').trimStart('/')

        val defaultPackageId = "com.mfb.app.${projectName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "app" }}"
        val resolvedPackageId = analysis.detectedPackageId ?: defaultPackageId
        val resolvedVersionName = analysis.detectedVersionName ?: "1.0.0"
        val resolvedVersionCode = analysis.detectedVersionCode ?: 1

        val project = Project(
            projectUuid = projectUuid,
            name = projectName,
            description = "${analysis.category.displayName} • Imported from $originalFileName",
            packageId = resolvedPackageId,
            versionName = resolvedVersionName,
            versionCode = resolvedVersionCode,
            originalFileName = originalFileName,
            sourceType = SourceType.ZIP,
            mainHtmlRelativePath = normalizedEntry,
            internalProjectPath = targetProjectDir.absolutePath,
            fileCount = fileCount,
            totalSizeBytes = totalSizeBytes,
            importStatus = ImportStatus.SUCCESS,
            projectCategory = analysis.category,
            detectedTechSummary = analysis.techSummary,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        ProjectStorageManager.writeMetadata(targetProjectDir, project)
        onProgress(0.97f, "Registering project in database...")
        Log.d(TAG, "Project staged & committed at ${targetProjectDir.absolutePath} ($fileCount files)")
        return ZipImportResult.Success(project)
    }

    private fun getFileInfo(context: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "archive.zip"
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        val queriedName = cursor.getString(nameIndex)
                        if (!queriedName.isNullOrBlank()) {
                            name = queriedName
                        }
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ContentResolver metadata query failed for $uri, using fallback name=$name", e)
        }
        return Pair(name, size)
    }
}
