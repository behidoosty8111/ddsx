package com.example.mfb.core.security

sealed class ImportSecurityException(message: String) : Exception(message) {
    class ZipSlipException(path: String) : 
        ImportSecurityException("Malicious path detected in archive (Zip Slip attempt): $path")
        
    class ZipBombException(ratio: Double, limit: Double) : 
        ImportSecurityException("Suspected compression bomb: compression ratio ${String.format("%.1f", ratio)}:1 exceeds safety limit of ${limit.toInt()}:1")
        
    class MaxArchiveSizeExceededException(sizeBytes: Long, limitBytes: Long) : 
        ImportSecurityException("Archive size (${sizeBytes / (1024 * 1024)} MB) exceeds maximum allowed size of ${limitBytes / (1024 * 1024)} MB")
        
    class MaxExtractedSizeExceededException(sizeBytes: Long, limitBytes: Long) : 
        ImportSecurityException("Extracted size (${sizeBytes / (1024 * 1024)} MB) exceeds safety threshold of ${limitBytes / (1024 * 1024)} MB")
        
    class MaxEntryCountExceededException(count: Int, limit: Int) : 
        ImportSecurityException("Archive contains $count files, exceeding maximum limit of $limit entries")
        
    class MaxFileSizeExceededException(fileName: String, sizeBytes: Long, limitBytes: Long) : 
        ImportSecurityException("File '$fileName' size (${sizeBytes / (1024 * 1024)} MB) exceeds safety limit of ${limitBytes / (1024 * 1024)} MB")

    class CorruptedArchiveException(causeMessage: String) :
        ImportSecurityException("Archive is corrupted or invalid: $causeMessage")

    class NoHtmlEntryPointException :
        ImportSecurityException("No valid HTML entry point (index.html or HTML files) found in the project")

    class InvalidHtmlFileException(reason: String) :
        ImportSecurityException("Invalid HTML file: $reason")
}
