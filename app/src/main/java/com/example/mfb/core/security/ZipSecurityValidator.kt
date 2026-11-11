package com.example.mfb.core.security

import java.io.File
import java.io.IOException

object ZipSecurityValidator {
    const val MAX_ARCHIVE_SIZE = 100 * 1024 * 1024L // 100 MB
    const val MAX_EXTRACTED_SIZE = 250 * 1024 * 1024L // 250 MB
    const val MAX_ENTRY_COUNT = 5_000
    const val MAX_INDIVIDUAL_FILE_SIZE = 50 * 1024 * 1024L // 50 MB
    const val MAX_COMPRESSION_RATIO = 100.0

    /**
     * Prevents Zip Slip directory traversal vulnerability.
     * Ensures destination file path is strictly under targetDir canonical path.
     */
    @Throws(ImportSecurityException.ZipSlipException::class)
    fun resolveAndValidateDestination(targetDir: File, entryName: String): File {
        if (entryName.contains('\u0000')) {
            throw ImportSecurityException.ZipSlipException(entryName)
        }
        val normalized = entryName
            .replace('\\', '/')
            .trimStart('/')
        val rawSegments = normalized.split('/').filter { it.isNotEmpty() }
        if (rawSegments.any { it == ".." }) {
            throw ImportSecurityException.ZipSlipException(entryName)
        }
        val safeSegments = rawSegments.filter { it != "." }
        val destinationFile = if (safeSegments.isEmpty()) {
            targetDir
        } else {
            File(targetDir, safeSegments.joinToString(File.separator))
        }
        val canonicalDestinationPath = try {
            destinationFile.canonicalPath
        } catch (e: IOException) {
            throw ImportSecurityException.ZipSlipException(entryName)
        }
        val canonicalTargetPath = targetDir.canonicalPath

        if (!canonicalDestinationPath.startsWith(canonicalTargetPath + File.separator) &&
            canonicalDestinationPath != canonicalTargetPath
        ) {
            throw ImportSecurityException.ZipSlipException(entryName)
        }

        return destinationFile
    }

    /**
     * Validates running extraction metrics against zip bomb thresholds.
     */
    @Throws(ImportSecurityException::class)
    fun checkMetrics(
        entryCount: Int,
        totalExtractedBytes: Long,
        compressedBytesRead: Long,
        currentFileBytes: Long,
        currentFileName: String
    ) {
        if (entryCount > MAX_ENTRY_COUNT) {
            throw ImportSecurityException.MaxEntryCountExceededException(entryCount, MAX_ENTRY_COUNT)
        }

        if (totalExtractedBytes > MAX_EXTRACTED_SIZE) {
            throw ImportSecurityException.MaxExtractedSizeExceededException(totalExtractedBytes, MAX_EXTRACTED_SIZE)
        }

        if (currentFileBytes > MAX_INDIVIDUAL_FILE_SIZE) {
            throw ImportSecurityException.MaxFileSizeExceededException(
                currentFileName,
                currentFileBytes,
                MAX_INDIVIDUAL_FILE_SIZE
            )
        }

        // Zip bomb detection: only check ratio after a minimal read threshold (e.g. 50 KB) to avoid false positives on small headers
        if (compressedBytesRead > 50 * 1024 && totalExtractedBytes > 500 * 1024) {
            val ratio = totalExtractedBytes.toDouble() / compressedBytesRead.toDouble()
            if (ratio > MAX_COMPRESSION_RATIO) {
                throw ImportSecurityException.ZipBombException(ratio, MAX_COMPRESSION_RATIO)
            }
        }
    }
}
