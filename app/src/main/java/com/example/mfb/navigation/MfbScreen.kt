package com.example.mfb.navigation

import android.net.Uri

sealed class MfbScreen(val route: String) {
    data object Splash : MfbScreen("splash")
    data object Dashboard : MfbScreen("dashboard")
    data object Projects : MfbScreen("projects")
    data object Templates : MfbScreen("templates")
    data object Settings : MfbScreen("settings")
    data object Import : MfbScreen("import")
    data object ImportZip : MfbScreen("import_zip")
    data object ImportHtml : MfbScreen("import_html")
    data object About : MfbScreen("about")
    data object UpdateLog : MfbScreen("update_log")
    data object ProjectDetails : MfbScreen("project_details/{projectId}") {
        fun createRoute(projectId: Long): String = "project_details/$projectId"
    }
    data object CodeEditor : MfbScreen("code_editor/{projectId}?filePath={filePath}") {
        fun createRoute(projectId: Long, filePath: String? = null): String {
            return if (filePath.isNullOrBlank()) {
                "code_editor/$projectId"
            } else {
                "code_editor/$projectId?filePath=${Uri.encode(filePath)}"
            }
        }
    }
    data object LivePreview : MfbScreen("live_preview/{projectId}?entryPath={entryPath}") {
        fun createRoute(projectId: Long, entryPath: String? = null): String {
            return if (entryPath.isNullOrBlank()) {
                "live_preview/$projectId"
            } else {
                "live_preview/$projectId?entryPath=${Uri.encode(entryPath)}"
            }
        }
    }
    data object BuildPipeline : MfbScreen("build_pipeline/{projectId}") {
        fun createRoute(projectId: Long): String = "build_pipeline/$projectId"
    }
}
