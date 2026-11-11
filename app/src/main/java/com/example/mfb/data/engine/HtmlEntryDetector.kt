package com.example.mfb.data.engine

import java.io.File

sealed class HtmlDetectionResult {
    data class AutoSelected(val relativePath: String) : HtmlDetectionResult()
    data class MultipleCandidates(val candidates: List<String>) : HtmlDetectionResult()
    object None : HtmlDetectionResult()
}

object HtmlEntryDetector {

    /**
     * Inspects a directory to find the main HTML entry point according to priority rules:
     * 1. index.html (root preferred, shallowest if nested)
     * 2. index.htm (root preferred, shallowest if nested)
     * 3. Single HTML file (e.g. app.html, main.html)
     * 4. Multiple candidates -> user selection needed
     * 5. No HTML files -> None
     */
    fun detectEntryPoint(rootDir: File): HtmlDetectionResult {
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return HtmlDetectionResult.None
        }

        val allHtmlFiles = rootDir.walkTopDown()
            .filter { it.isFile && (it.extension.equals("html", ignoreCase = true) || it.extension.equals("htm", ignoreCase = true)) }
            .toList()

        if (allHtmlFiles.isEmpty()) {
            return HtmlDetectionResult.None
        }

        // Relative path resolver
        val relativePaths = allHtmlFiles.map { file ->
            file.relativeTo(rootDir).path.replace('\\', '/')
        }

        // 1. Look for index.html (case insensitive)
        val indexHtmls = relativePaths.filter { path ->
            val fileName = path.substringAfterLast('/')
            fileName.equals("index.html", ignoreCase = true)
        }.sortedBy { it.count { ch -> ch == '/' } } // shallowest first

        if (indexHtmls.isNotEmpty()) {
            val minDepth = indexHtmls.first().count { ch -> ch == '/' }
            val shallowest = indexHtmls.filter { it.count { ch -> ch == '/' } == minDepth }
            return if (shallowest.size == 1) {
                HtmlDetectionResult.AutoSelected(shallowest.first())
            } else {
                HtmlDetectionResult.MultipleCandidates(shallowest.sorted())
            }
        }

        // 2. Look for index.htm (case insensitive)
        val indexHtms = relativePaths.filter { path ->
            val fileName = path.substringAfterLast('/')
            fileName.equals("index.htm", ignoreCase = true)
        }.sortedBy { it.count { ch -> ch == '/' } }

        if (indexHtms.isNotEmpty()) {
            val minDepth = indexHtms.first().count { ch -> ch == '/' }
            val shallowest = indexHtms.filter { it.count { ch -> ch == '/' } == minDepth }
            return if (shallowest.size == 1) {
                HtmlDetectionResult.AutoSelected(shallowest.first())
            } else {
                HtmlDetectionResult.MultipleCandidates(shallowest.sorted())
            }
        }

        // 3. Exactly one HTML file in the entire project
        if (relativePaths.size == 1) {
            return HtmlDetectionResult.AutoSelected(relativePaths.first())
        }

        // 4. Multiple HTML files without an index.html/htm
        return HtmlDetectionResult.MultipleCandidates(relativePaths.sorted())
    }
}
