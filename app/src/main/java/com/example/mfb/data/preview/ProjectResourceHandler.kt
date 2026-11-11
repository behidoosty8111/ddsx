package com.example.mfb.data.preview

import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import com.example.mfb.data.engine.ProjectFileManager
import com.example.mfb.data.engine.ProjectStorageManager
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection

object PreviewMimeTypes {
    private val MIME_MAP = mapOf(
        "html" to "text/html",
        "htm" to "text/html",
        "xhtml" to "application/xhtml+xml",
        "css" to "text/css",
        "js" to "application/javascript",
        "mjs" to "application/javascript",
        "json" to "application/json",
        "webmanifest" to "application/manifest+json",
        "map" to "application/json",
        "xml" to "application/xml",
        "txt" to "text/plain",
        "md" to "text/markdown",
        "svg" to "image/svg+xml",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "webp" to "image/webp",
        "avif" to "image/avif",
        "gif" to "image/gif",
        "bmp" to "image/bmp",
        "ico" to "image/x-icon",
        "woff2" to "font/woff2",
        "woff" to "font/woff",
        "ttf" to "font/ttf",
        "otf" to "font/otf",
        "eot" to "application/vnd.ms-fontobject",
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "mp4" to "video/mp4",
        "webm" to "video/webm",
        "wasm" to "application/wasm"
    )

    private val TEXT_MIME_PREFIXES = listOf(
        "text/",
        "application/javascript",
        "application/json",
        "application/manifest+json",
        "application/xml",
        "application/xhtml+xml",
        "image/svg+xml"
    )

    fun resolveMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MIME_MAP[ext]
            ?: URLConnection.guessContentTypeFromName(fileName)
            ?: "application/octet-stream"
    }

    fun resolveEncoding(mimeType: String): String? {
        return if (TEXT_MIME_PREFIXES.any { mimeType.startsWith(it, ignoreCase = true) }) {
            "utf-8"
        } else {
            null
        }
    }
}

/**
 * Serves files strictly from the selected project's internal sandbox directory via a per-project
 * synthetic HTTPS origin (`https://project-{uuid}.mfb.local/`), enforcing canonical path checks
 * and preventing path traversal or cross-project access.
 */
class ProjectResourceHandler(
    private val projectDir: File,
    val projectHost: String,
    private val onMissingResource: (String) -> Unit = {},
    private val onSecurityViolation: (String, String) -> Unit = { _, _ -> }
) : WebViewAssetLoader.PathHandler {

    val projectOrigin: String = "https://$projectHost"

    fun buildEntryUrl(entryRelativePath: String): String {
        val clean = entryRelativePath.replace('\\', '/').trimStart('/')
        val builder = Uri.Builder()
            .scheme("https")
            .authority(projectHost)
        clean.split('/').filter { it.isNotEmpty() && it != "." }.forEach { segment ->
            builder.appendPath(segment)
        }
        return builder.build().toString()
    }

    fun buildAssetLoader(): WebViewAssetLoader {
        return WebViewAssetLoader.Builder()
            .setDomain(projectHost)
            .setHttpAllowed(false)
            .addPathHandler("/", this)
            .build()
    }

    override fun handle(path: String): WebResourceResponse {
        val withoutQueryOrFragment = path
            .substringBefore('?')
            .substringBefore('#')
        val decodedPath = try {
            Uri.decode(withoutQueryOrFragment) ?: withoutQueryOrFragment
        } catch (_: Exception) {
            withoutQueryOrFragment
        }
        val cleanRelPath = decodedPath
            .replace('\\', '/')
            .trimStart('/')

        if (cleanRelPath.isEmpty() || cleanRelPath == ProjectStorageManager.METADATA_FILE_NAME) {
            onSecurityViolation("Blocked access to restricted path.", cleanRelPath.ifEmpty { "/" })
            return emptyResponse(403, "Forbidden")
        }

        val resolved = ProjectFileManager.resolveSafePath(projectDir, cleanRelPath)
        val target = resolved.getOrElse { err ->
            onSecurityViolation(err.message ?: "Path traversal rejected.", cleanRelPath)
            return emptyResponse(403, "Forbidden")
        }

        val rootCanonical = try {
            projectDir.canonicalPath
        } catch (_: Exception) {
            return emptyResponse(500, "Internal Error")
        }

        val targetCanonical = try {
            target.canonicalPath
        } catch (_: Exception) {
            return emptyResponse(403, "Forbidden")
        }

        if (!targetCanonical.startsWith(rootCanonical + File.separator)) {
            onSecurityViolation("Attempted access outside project sandbox.", cleanRelPath)
            return emptyResponse(403, "Forbidden")
        }

        if (!target.exists() || !target.isFile || !target.canRead()) {
            onMissingResource(cleanRelPath)
            return emptyResponse(404, "Not Found")
        }

        return try {
            val mimeType = PreviewMimeTypes.resolveMimeType(target.name)
            val encoding = PreviewMimeTypes.resolveEncoding(mimeType)
            val stream = BufferedInputStream(FileInputStream(target))
            WebResourceResponse(
                mimeType,
                encoding,
                200,
                "OK",
                mapOf(
                    "Cache-Control" to "no-store, no-cache, must-revalidate",
                    "Pragma" to "no-cache",
                    "Access-Control-Allow-Origin" to "*"
                ),
                stream
            )
        } catch (_: Exception) {
            emptyResponse(500, "Read Error")
        }
    }

    fun toSafeRelativeRef(url: Uri?): String {
        if (url == null) return ""
        return if (url.host.equals(projectHost, ignoreCase = true)) {
            url.path?.trimStart('/') ?: ""
        } else {
            url.host ?: url.scheme ?: "external"
        }
    }

    private fun emptyResponse(statusCode: Int, reasonPhrase: String): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            statusCode,
            reasonPhrase,
            emptyMap(),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    companion object {
        fun buildProjectHost(projectUuid: String): String {
            val sanitized = projectUuid.lowercase().replace(Regex("[^a-z0-9-]"), "")
                .ifBlank { "default" }
            return "project-$sanitized.mfb.local"
        }

        fun forbiddenResponse(): WebResourceResponse {
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                403,
                "Forbidden",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0))
            )
        }
    }
}
