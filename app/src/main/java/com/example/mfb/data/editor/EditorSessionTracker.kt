package com.example.mfb.data.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lightweight in-memory tracker so ProjectFileExplorerTab can display Open / Modified badges
 * and warn before deleting or renaming a file that currently has unsaved changes in the editor.
 */
object EditorSessionTracker {
    private val _openFilesByProject = MutableStateFlow<Map<Long, Set<String>>>(emptyMap())
    val openFilesByProject: StateFlow<Map<Long, Set<String>>> = _openFilesByProject.asStateFlow()

    private val _dirtyFilesByProject = MutableStateFlow<Map<Long, Set<String>>>(emptyMap())
    val dirtyFilesByProject: StateFlow<Map<Long, Set<String>>> = _dirtyFilesByProject.asStateFlow()

    fun updateProjectState(projectId: Long, openPaths: Set<String>, dirtyPaths: Set<String>) {
        _openFilesByProject.update { current ->
            if (openPaths.isEmpty()) current - projectId else current + (projectId to openPaths)
        }
        _dirtyFilesByProject.update { current ->
            if (dirtyPaths.isEmpty()) current - projectId else current + (projectId to dirtyPaths)
        }
    }

    fun isFileDirty(projectId: Long, relativePath: String): Boolean {
        return _dirtyFilesByProject.value[projectId]?.contains(relativePath) == true
    }

    fun isFileOpen(projectId: Long, relativePath: String): Boolean {
        return _openFilesByProject.value[projectId]?.contains(relativePath) == true
    }

    fun clearAll() {
        _openFilesByProject.value = emptyMap()
        _dirtyFilesByProject.value = emptyMap()
    }
}
