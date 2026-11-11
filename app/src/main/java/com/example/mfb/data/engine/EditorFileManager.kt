package com.example.mfb.data.engine

import android.os.StatFs
import com.example.mfb.domain.model.EditorLanguage
import com.example.mfb.domain.model.LineEnding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.UUID

class UnsupportedEditorFileException(message: String) : IllegalArgumentException(message)
class InvalidEncodingException(message: String) : IOException(message)

data class LoadedEditorFile(
    val relativePath: String,
    val fileName: String,
    val extension: String,
    val language: EditorLanguage,
    val content: String,
    val encodingName: String,
    val hasBom: Boolean,
    val lineEnding: LineEnding,
    val lastModified: Long,
    val sizeBytes: Long,
    val isLargeFile: Boolean,
    val warningMessage: String? = null
)

sealed class SaveFileResult {
    data class Success(val newLastModified: Long, val newSizeBytes: Long) : SaveFileResult()
    data class Conflict(val diskLastModified: Long, val reason: String) : SaveFileResult()
    data class Failure(val message: String) : SaveFileResult()
}

object EditorFileManager {

    const val MAX_EDITABLE_FILE_BYTES: Long = 2L * 1024L * 1024L // 2 MB hard safety cap
    const val LARGE_FILE_THRESHOLD_BYTES: Long = 150L * 1024L // 150 KB: pause syntax highlighting for responsiveness
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun normalizeRelativePath(path: String): String {
        return path.trim().replace('\\', '/').trimStart('/')
    }

    fun isEditableFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext.isNotEmpty() && EditorLanguage.isEditableExtension(ext)
    }

    fun readFileForEditor(projectDir: File, relativePath: String): Result<LoadedEditorFile> {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return Result.failure(IOException("Project directory does not exist."))
        }
        val normalizedPath = normalizeRelativePath(relativePath)
        if (normalizedPath.isEmpty()) {
            return Result.failure(IllegalArgumentException("File path cannot be empty."))
        }
        val target = ProjectFileManager.resolveSafePath(projectDir, normalizedPath)
            .getOrElse { return Result.failure(it) }

        if (!target.exists() || !target.isFile) {
            return Result.failure(IOException("File not found: $normalizedPath"))
        }
        if (target.parentFile?.canonicalPath == projectDir.canonicalPath &&
            target.name == ProjectStorageManager.METADATA_FILE_NAME
        ) {
            return Result.failure(SecurityException("Protected project metadata cannot be edited directly."))
        }

        val ext = target.extension.lowercase()
        val language = EditorLanguage.fromExtension(ext)
        if (language == EditorLanguage.UNSUPPORTED) {
            return Result.failure(
                UnsupportedEditorFileException(
                    "Editing '.${ext.ifEmpty { "unknown" }}' files is not supported. Supported: .html, .htm, .css, .js, .json, .txt, .xml, .md"
                )
            )
        }

        val fileSize = target.length()
        if (fileSize > MAX_EDITABLE_FILE_BYTES) {
            return Result.failure(
                IOException(
                    "File '${target.name}' (${fileSize / 1024} KB) exceeds the ${MAX_EDITABLE_FILE_BYTES / (1024 * 1024)} MB editor safety limit."
                )
            )
        }

        val rawBytes = try {
            target.readBytes()
        } catch (e: IOException) {
            return Result.failure(IOException("Failed to read '${target.name}': ${e.message}", e))
        }

        if (rawBytes.any { it == 0.toByte() }) {
            return Result.failure(
                InvalidEncodingException("File '${target.name}' contains binary data or unsupported encoding.")
            )
        }

        val hasBom = rawBytes.size >= 3 &&
            rawBytes[0] == UTF8_BOM[0] &&
            rawBytes[1] == UTF8_BOM[1] &&
            rawBytes[2] == UTF8_BOM[2]

        val payloadBytes = if (hasBom) rawBytes.copyOfRange(3, rawBytes.size) else rawBytes

        val decodedText = try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(payloadBytes)).toString()
        } catch (_: CharacterCodingException) {
            return Result.failure(
                InvalidEncodingException("Unsupported or invalid UTF-8 encoding in '${target.name}'.")
            )
        }

        val crlfCount = Regex("\r\n").findAll(decodedText).count()
        val lfOnlyCount = decodedText.count { it == '\n' } - crlfCount
        val lineEnding = if (crlfCount > lfOnlyCount && crlfCount > 0) LineEnding.CRLF else LineEnding.LF

        val normalizedContent = decodedText.replace("\r\n", "\n").replace("\r", "\n")
        val isLargeFile = fileSize > LARGE_FILE_THRESHOLD_BYTES
        val warningMessage = if (isLargeFile) {
            "Large file (${fileSize / 1024} KB): syntax highlighting paused to keep editing smooth."
        } else {
            null
        }

        return Result.success(
            LoadedEditorFile(
                relativePath = normalizedPath,
                fileName = target.name,
                extension = ext,
                language = language,
                content = normalizedContent,
                encodingName = if (hasBom) "UTF-8 BOM" else "UTF-8",
                hasBom = hasBom,
                lineEnding = lineEnding,
                lastModified = target.lastModified(),
                sizeBytes = fileSize,
                isLargeFile = isLargeFile,
                warningMessage = warningMessage
            )
        )
    }

    fun saveFileAtomically(
        projectDir: File,
        relativePath: String,
        content: String,
        hasBom: Boolean,
        lineEnding: LineEnding,
        expectedLastModified: Long?,
        forceOverwrite: Boolean = false
    ): SaveFileResult {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return SaveFileResult.Failure("Project directory does not exist.")
        }
        val normalizedPath = normalizeRelativePath(relativePath)
        val target = ProjectFileManager.resolveSafePath(projectDir, normalizedPath)
            .getOrElse { return SaveFileResult.Failure(it.message ?: "Invalid file path.") }

        if (target.parentFile?.canonicalPath == projectDir.canonicalPath &&
            target.name == ProjectStorageManager.METADATA_FILE_NAME
        ) {
            return SaveFileResult.Failure("Protected project metadata cannot be overwritten.")
        }

        if (!forceOverwrite && expectedLastModified != null) {
            if (!target.exists()) {
                return SaveFileResult.Conflict(
                    diskLastModified = 0L,
                    reason = "The file '${target.name}' was deleted or moved outside the editor."
                )
            }
            val currentDiskModified = target.lastModified()
            if (currentDiskModified != expectedLastModified) {
                return SaveFileResult.Conflict(
                    diskLastModified = currentDiskModified,
                    reason = "The file '${target.name}' was modified on disk outside the editor."
                )
            }
        }

        val serializedText = if (lineEnding == LineEnding.CRLF) {
            content.replace("\n", "\r\n")
        } else {
            content
        }

        val encodedBody = serializedText.toByteArray(Charsets.UTF_8)
        val finalBytes = if (hasBom) UTF8_BOM + encodedBody else encodedBody

        // Check available storage safely (guarded for Robolectric where totalBytes may be 0)
        try {
            val statFs = StatFs(projectDir.absolutePath)
            val totalFsBytes = statFs.totalBytes
            val availableBytes = statFs.availableBytes
            if (totalFsBytes > 0L && availableBytes < finalBytes.size + 64L * 1024L) {
                return SaveFileResult.Failure("Insufficient storage space to save '${target.name}'.")
            }
        } catch (_: Exception) {
            // StatFs may not be supported on virtual test filesystems
        }

        val parentDir = target.parentFile
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            return SaveFileResult.Failure("Failed to create parent directory for '${target.name}'.")
        }

        val tempFile = File(parentDir ?: projectDir, ".${target.name}.${UUID.randomUUID()}.mfb.tmp")
        return try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(finalBytes)
                fos.flush()
                try {
                    fos.fd.sync()
                } catch (_: Exception) {
                    // Some virtual filesystems do not support fsync
                }
            }

            if (!tempFile.exists() || tempFile.length() != finalBytes.size.toLong()) {
                return SaveFileResult.Failure("Incomplete temporary write for '${target.name}'.")
            }

            if (target.exists() && !target.canWrite()) {
                return SaveFileResult.Failure("File '${target.name}' is read-only.")
            }

            // Atomic rename on POSIX/Android; fallback with backup if renameTo fails across filesystems
            val replaced = if (tempFile.renameTo(target)) {
                true
            } else {
                val backupFile = if (target.exists()) {
                    File(parentDir ?: projectDir, ".${target.name}.${UUID.randomUUID()}.mfb.bak").also { bak ->
                        target.copyTo(bak, overwrite = true)
                    }
                } else null
                try {
                    tempFile.copyTo(target, overwrite = true)
                    backupFile?.delete()
                    true
                } catch (e: Exception) {
                    if (backupFile != null && backupFile.exists()) {
                        runCatching { backupFile.copyTo(target, overwrite = true) }
                        backupFile.delete()
                    }
                    throw e
                }
            }

            if (!replaced) {
                SaveFileResult.Failure("Failed to replace '${target.name}' safely.")
            } else {
                SaveFileResult.Success(
                    newLastModified = target.lastModified(),
                    newSizeBytes = target.length()
                )
            }
        } catch (e: IOException) {
            SaveFileResult.Failure("Failed to save '${target.name}': ${e.message}")
        } catch (e: SecurityException) {
            SaveFileResult.Failure("Permission denied while saving '${target.name}'.")
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
