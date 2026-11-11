package com.example.mfb.domain.model

import androidx.compose.ui.text.input.TextFieldValue

enum class EditorLanguage(val displayName: String, val supportsSyntaxHighlighting: Boolean) {
    HTML("HTML", true),
    CSS("CSS", true),
    JAVASCRIPT("JavaScript", true),
    JSON("JSON", true),
    XML("XML", true),
    MARKDOWN("Markdown", true),
    PLAIN_TEXT("Plain Text", false),
    UNSUPPORTED("Unsupported", false);

    companion object {
        val EDITABLE_EXTENSIONS: Set<String> = setOf(
            "html", "htm", "css", "js", "json", "txt", "xml", "md"
        )

        fun isEditableExtension(extension: String): Boolean {
            return extension.lowercase() in EDITABLE_EXTENSIONS
        }

        fun fromExtension(extension: String): EditorLanguage {
            return when (extension.lowercase()) {
                "html", "htm" -> HTML
                "css" -> CSS
                "js" -> JAVASCRIPT
                "json" -> JSON
                "xml" -> XML
                "md" -> MARKDOWN
                "txt" -> PLAIN_TEXT
                else -> UNSUPPORTED
            }
        }
    }
}

enum class EditorThemePreset(val label: String) {
    MATCH_APP("Match App Theme"),
    DARK_IDE("Dark IDE"),
    LIGHT_IDE("Light IDE"),
    HIGH_CONTRAST_DARK("High Contrast Dark")
}

enum class LineEnding(val separator: String, val label: String) {
    LF("\n", "LF"),
    CRLF("\r\n", "CRLF")
}

data class EditorSettings(
    val fontSizeSp: Int = 14,
    val editorTheme: EditorThemePreset = EditorThemePreset.MATCH_APP,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = false,
    val autoSaveEnabled: Boolean = false,
    val indentWidth: Int = 2,
    val useTabs: Boolean = false,
    val syntaxHighlightingEnabled: Boolean = true
) {
    val indentUnit: String
        get() = if (useTabs) "\t" else " ".repeat(indentWidth.coerceIn(2, 8))
}

sealed class SaveState {
    data object Saved : SaveState()
    data object Unsaved : SaveState()
    data object Saving : SaveState()
    data class SaveFailed(val message: String) : SaveState()
}

data class OpenEditorTab(
    val relativePath: String,
    val fileName: String,
    val extension: String,
    val language: EditorLanguage,
    val encodingName: String,
    val hasBom: Boolean,
    val lineEnding: LineEnding,
    val textFieldValue: TextFieldValue,
    val savedContent: String,
    val lastKnownModifiedAt: Long,
    val lastKnownSizeBytes: Long,
    val saveState: SaveState = SaveState.Saved,
    val isLargeFile: Boolean = false,
    val warningMessage: String? = null
) {
    val isDirty: Boolean
        get() = textFieldValue.text != savedContent
}

data class SearchState(
    val isVisible: Boolean = false,
    val query: String = "",
    val replaceQuery: String = "",
    val isReplaceExpanded: Boolean = false,
    val matchCase: Boolean = false,
    val matches: List<IntRange> = emptyList(),
    val currentMatchIndex: Int = -1
)

data class EditorConflictState(
    val relativePath: String,
    val fileName: String,
    val diskLastModified: Long,
    val reason: String
)

data class EditorSessionSnapshot(
    val projectId: Long,
    val openTabPaths: List<String>,
    val activeTabPath: String?
)
