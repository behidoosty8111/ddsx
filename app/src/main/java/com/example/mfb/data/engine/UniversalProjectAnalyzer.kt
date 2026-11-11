package com.example.mfb.data.engine

import com.example.mfb.domain.model.ProjectAnalysisReport
import com.example.mfb.domain.model.ProjectCategory
import org.json.JSONObject
import java.io.File
import java.util.regex.Pattern

object UniversalProjectAnalyzer {

    private val APP_ID_PATTERN = Pattern.compile("""applicationId\s*=?\s*["']([^"']+)["']""")
    private val NAMESPACE_PATTERN = Pattern.compile("""namespace\s*=?\s*["']([^"']+)["']""")
    private val VERSION_NAME_PATTERN = Pattern.compile("""versionName\s*=?\s*["']([^"']+)["']""")
    private val VERSION_CODE_PATTERN = Pattern.compile("""versionCode\s*=?\s*(\d+)""")
    private val MANIFEST_PKG_PATTERN = Pattern.compile("""package\s*=\s*["']([^"']+)["']""")
    private val ROOT_PROJECT_NAME_PATTERN = Pattern.compile("""rootProject\.name\s*=\s*["']([^"']+)["']""")

    fun analyze(projectDir: File): ProjectAnalysisReport {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return ProjectAnalysisReport(
                category = ProjectCategory.UNKNOWN,
                entryPointPath = "",
                techSummary = "Missing directory",
                buildReadinessNotes = listOf("Project directory does not exist.")
            )
        }

        val allFiles = projectDir.walkTopDown()
            .filter { it.isFile && it.name != ProjectStorageManager.METADATA_FILE_NAME }
            .toList()

        val relativeFiles = allFiles.map { file ->
            file to file.relativeTo(projectDir).invariantSeparatorsPath
        }

        val gradleFiles = relativeFiles.filter { (f, _) ->
            f.name == "build.gradle" || f.name == "build.gradle.kts"
        }
        val settingsFiles = relativeFiles.filter { (f, _) ->
            f.name == "settings.gradle" || f.name == "settings.gradle.kts"
        }
        val hasGradleWrapper = relativeFiles.any { (f, _) ->
            f.name == "gradlew" || f.name == "gradlew.bat" || f.name == "gradle-wrapper.properties"
        }
        val manifestFiles = relativeFiles.filter { (f, _) ->
            f.name == "AndroidManifest.xml"
        }
        val packageJsonFiles = relativeFiles.filter { (f, rel) ->
            f.name == "package.json" && !rel.contains("node_modules/")
        }.sortedBy { it.second.count { c -> c == '/' } }

        val capacitorConfigs = relativeFiles.filter { (f, _) ->
            f.name == "capacitor.config.json" || f.name == "capacitor.config.ts" || f.name == "capacitor.config.js"
        }
        val viteConfigs = relativeFiles.filter { (f, _) ->
            f.name.startsWith("vite.config.")
        }

        val kotlinFiles = relativeFiles.filter { (f, rel) ->
            f.extension.equals("kt", ignoreCase = true) && !rel.contains("/build/")
        }
        val javaFiles = relativeFiles.filter { (f, rel) ->
            f.extension.equals("java", ignoreCase = true) && !rel.contains("/build/")
        }

        val htmlDetection = HtmlEntryDetector.detectEntryPoint(projectDir)
        val htmlCount = relativeFiles.count { (f, rel) ->
            (f.extension.equals("html", ignoreCase = true) || f.extension.equals("htm", ignoreCase = true)) &&
                !rel.contains("node_modules/")
        }

        // Parse package.json metadata if present
        var pkgJsonName: String? = null
        var pkgJsonVersion: String? = null
        var hasReactDep = false
        var hasViteDep = false
        var hasCapacitorDep = false

        packageJsonFiles.firstOrNull()?.first?.let { pkgFile ->
            runCatching {
                val json = JSONObject(pkgFile.readText(Charsets.UTF_8))
                pkgJsonName = json.optString("name").takeIf { it.isNotBlank() }
                pkgJsonVersion = json.optString("version").takeIf { it.isNotBlank() }
                val deps = json.optJSONObject("dependencies")
                val devDeps = json.optJSONObject("devDependencies")
                hasReactDep = deps?.has("react") == true || devDeps?.has("react") == true ||
                    deps?.has("react-dom") == true || devDeps?.has("react-dom") == true
                hasViteDep = deps?.has("vite") == true || devDeps?.has("vite") == true
                hasCapacitorDep = deps?.has("@capacitor/core") == true || devDeps?.has("@capacitor/core") == true ||
                    deps?.has("@capacitor/android") == true
            }
        }

        val hasGradleBuild = gradleFiles.isNotEmpty() || settingsFiles.isNotEmpty()
        val hasAndroidManifest = manifestFiles.isNotEmpty()
        val hasPackageJson = packageJsonFiles.isNotEmpty()
        val hasCapacitorConfig = capacitorConfigs.isNotEmpty() || hasCapacitorDep

        // Classify project category across the entire archive structure
        val category = when {
            hasCapacitorConfig -> ProjectCategory.CAPACITOR
            hasGradleBuild && (hasAndroidManifest || kotlinFiles.isNotEmpty() || javaFiles.isNotEmpty()) -> {
                when {
                    kotlinFiles.isNotEmpty() -> ProjectCategory.ANDROID_GRADLE_KOTLIN
                    javaFiles.isNotEmpty() -> ProjectCategory.ANDROID_GRADLE_JAVA
                    else -> ProjectCategory.ANDROID_GRADLE
                }
            }
            hasAndroidManifest && (kotlinFiles.isNotEmpty() || javaFiles.isNotEmpty()) -> {
                if (kotlinFiles.isNotEmpty()) ProjectCategory.ANDROID_GRADLE_KOTLIN else ProjectCategory.ANDROID_GRADLE_JAVA
            }
            hasPackageJson && hasReactDep && (hasViteDep || viteConfigs.isNotEmpty()) -> ProjectCategory.REACT_VITE
            hasPackageJson && hasReactDep -> ProjectCategory.REACT
            hasPackageJson && (hasViteDep || viteConfigs.isNotEmpty()) -> ProjectCategory.VITE
            viteConfigs.isNotEmpty() -> ProjectCategory.VITE
            htmlCount > 0 -> ProjectCategory.WEB_HTML
            hasGradleBuild -> ProjectCategory.ANDROID_GRADLE
            else -> ProjectCategory.UNKNOWN
        }

        // Extract Android metadata from build.gradle / AndroidManifest / settings.gradle
        var detectedPackageId: String? = null
        var detectedVersionName: String? = pkgJsonVersion
        var detectedVersionCode: Int? = null
        var detectedAppName: String? = pkgJsonName

        for ((file, _) in gradleFiles.sortedByDescending { it.second.contains("app/") }) {
            val content = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
            if (detectedPackageId == null) {
                detectedPackageId = matchFirst(APP_ID_PATTERN, content)
                    ?: matchFirst(NAMESPACE_PATTERN, content)
            }
            if (detectedVersionName == null) {
                detectedVersionName = matchFirst(VERSION_NAME_PATTERN, content)
            }
            if (detectedVersionCode == null) {
                detectedVersionCode = matchFirst(VERSION_CODE_PATTERN, content)?.toIntOrNull()
            }
        }

        if (detectedPackageId == null) {
            for ((file, _) in manifestFiles) {
                val content = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
                detectedPackageId = matchFirst(MANIFEST_PKG_PATTERN, content)
                if (detectedPackageId != null) break
            }
        }

        if (detectedAppName == null) {
            for ((file, _) in settingsFiles) {
                val content = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
                detectedAppName = matchFirst(ROOT_PROJECT_NAME_PATTERN, content)
                if (detectedAppName != null) break
            }
        }

        // Prebuilt web dist detection for React/Vite/Capacitor
        val prebuiltDistHtml = relativeFiles.firstOrNull { (_, rel) ->
            rel.equals("dist/index.html", ignoreCase = true) ||
                rel.endsWith("/dist/index.html", ignoreCase = true) ||
                rel.equals("build/index.html", ignoreCase = true) ||
                rel.equals("www/index.html", ignoreCase = true)
        }?.second

        val htmlCandidates = when (htmlDetection) {
            is HtmlDetectionResult.AutoSelected -> listOf(htmlDetection.relativePath)
            is HtmlDetectionResult.MultipleCandidates -> htmlDetection.candidates
            HtmlDetectionResult.None -> emptyList()
        }

        val entryPointPath = when {
            category == ProjectCategory.WEB_HTML -> {
                when (htmlDetection) {
                    is HtmlDetectionResult.AutoSelected -> htmlDetection.relativePath
                    is HtmlDetectionResult.MultipleCandidates -> htmlDetection.candidates.first()
                    HtmlDetectionResult.None -> ""
                }
            }
            category.supportsLivePreview -> {
                prebuiltDistHtml
                    ?: when (htmlDetection) {
                        is HtmlDetectionResult.AutoSelected -> htmlDetection.relativePath
                        is HtmlDetectionResult.MultipleCandidates -> htmlDetection.candidates.first()
                        HtmlDetectionResult.None -> packageJsonFiles.firstOrNull()?.second
                            ?: capacitorConfigs.firstOrNull()?.second
                            ?: ""
                    }
            }
            else -> {
                manifestFiles.firstOrNull()?.second
                    ?: gradleFiles.firstOrNull()?.second
                    ?: kotlinFiles.firstOrNull()?.second
                    ?: javaFiles.firstOrNull()?.second
                    ?: ""
            }
        }

        val techParts = buildList {
            if (kotlinFiles.isNotEmpty()) add("Kotlin (${kotlinFiles.size})")
            if (javaFiles.isNotEmpty()) add("Java (${javaFiles.size})")
            if (hasGradleBuild) add("Gradle")
            if (hasAndroidManifest) add("AndroidManifest")
            if (hasCapacitorConfig) add("Capacitor")
            if (hasReactDep) add("React")
            if (hasViteDep || viteConfigs.isNotEmpty()) add("Vite")
            if (htmlCount > 0) add("HTML ($htmlCount)")
        }

        val readinessNotes = buildList {
            if (category == ProjectCategory.ANDROID_GRADLE_KOTLIN ||
                category == ProjectCategory.ANDROID_GRADLE_JAVA ||
                category == ProjectCategory.ANDROID_GRADLE
            ) {
                if (!hasGradleBuild) add("Missing build.gradle or build.gradle.kts.")
                if (!hasAndroidManifest) add("Missing AndroidManifest.xml.")
                if (!hasGradleWrapper) add("Gradle wrapper (gradlew) not included; build server will use system Gradle.")
                if (hasGradleBuild && hasAndroidManifest) {
                    add("Native Android Gradle project structure verified.")
                }
            } else if (category == ProjectCategory.WEB_HTML) {
                if (entryPointPath.isNotBlank()) {
                    add("Ready for Live Preview and Android WebView APK wrapping.")
                } else {
                    add("No HTML entry point found.")
                }
            } else if (category.requiresNodeBuild) {
                if (prebuiltDistHtml != null) {
                    add("Prebuilt web bundle detected at $prebuiltDistHtml.")
                } else {
                    add("Source project requires npm install & build (or remote build server) before APK packaging.")
                }
            }
        }

        return ProjectAnalysisReport(
            category = category,
            entryPointPath = entryPointPath,
            htmlCandidates = htmlCandidates,
            detectedPackageId = detectedPackageId,
            detectedAppName = detectedAppName,
            detectedVersionName = detectedVersionName,
            detectedVersionCode = detectedVersionCode,
            hasGradleBuild = hasGradleBuild,
            hasGradleWrapper = hasGradleWrapper,
            hasAndroidManifest = hasAndroidManifest,
            hasPackageJson = hasPackageJson,
            hasCapacitorConfig = hasCapacitorConfig,
            hasPrebuiltWebDist = prebuiltDistHtml != null,
            kotlinFileCount = kotlinFiles.size,
            javaFileCount = javaFiles.size,
            htmlFileCount = htmlCount,
            totalFiles = allFiles.size,
            techSummary = techParts.joinToString(" • ").ifBlank { category.displayName },
            buildReadinessNotes = readinessNotes
        )
    }

    private fun matchFirst(pattern: Pattern, input: String): String? {
        val matcher = pattern.matcher(input)
        return if (matcher.find()) matcher.group(1)?.trim()?.takeIf { it.isNotBlank() } else null
    }
}
