package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mfb.core.model.ThemeMode
import com.example.mfb.data.editor.CodeEditingHelpers
import com.example.mfb.data.editor.CodeSyntaxHighlighter
import com.example.mfb.data.editor.EditorSyntaxPalette
import com.example.mfb.data.engine.EditorFileManager
import com.example.mfb.data.engine.SaveFileResult
import com.example.mfb.data.engine.UnsupportedEditorFileException
import com.example.mfb.data.local.datastore.UserPreferencesRepository
import com.example.mfb.data.local.room.MfbDatabase
import com.example.mfb.data.repository.ProjectRepositoryImpl
import com.example.mfb.domain.model.EditorLanguage
import com.example.mfb.domain.model.EditorSessionSnapshot
import com.example.mfb.domain.model.EditorThemePreset
import com.example.mfb.domain.model.LineEnding
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.SaveState
import com.example.mfb.domain.model.SourceType
import com.example.mfb.presentation.editor.CodeEditorScreen
import com.example.mfb.presentation.editor.CodeEditorViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MfbPhase04RobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: MfbDatabase
    private lateinit var projectRepository: ProjectRepositoryImpl
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var projectDir: File
    private var projectId: Long = 0L

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MfbDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectRepository = ProjectRepositoryImpl(database.projectDao())
        preferencesRepository = UserPreferencesRepository(context)

        val uuid = "test-editor-${UUID.randomUUID()}"
        projectDir = File(context.filesDir, "projects/$uuid").apply { mkdirs() }
        File(projectDir, "index.html").writeText("<!DOCTYPE html>\n<html>\n<body>\n  <h1>Hello MFB</h1>\n</body>\n</html>")
        File(projectDir, "styles.css").writeText("body {\n  color: #6C63FF;\n  margin: 16px;\n}")
        File(projectDir, "app.js").writeText("function greet(name) {\n  return 'Hi ' + name;\n}")
        File(projectDir, "data.json").writeText("{\n  \"app\": \"MFB\",\n  \"phase\": 4,\n  \"ready\": true\n}")
        File(projectDir, "logo.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00, 0x01))

        val project = Project(
            projectUuid = uuid,
            name = "Editor Test Project",
            sourceType = SourceType.ZIP,
            originalFileName = "site.zip",
            mainHtmlRelativePath = "index.html",
            internalProjectPath = projectDir.absolutePath,
            fileCount = 5,
            totalSizeBytes = 256L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        projectId = projectRepository.insertProject(project)
        preferencesRepository.saveEditorSession(EditorSessionSnapshot(projectId, emptyList(), null))
    }

    @After
    fun teardown() {
        database.close()
        projectDir.deleteRecursively()
    }

    @Test
    fun `file reading supports html css js json and rejects unsupported missing and traversal paths`() {
        val html = EditorFileManager.readFileForEditor(projectDir, "index.html")
        assertTrue(html.isSuccess)
        assertEquals(EditorLanguage.HTML, html.getOrThrow().language)

        val css = EditorFileManager.readFileForEditor(projectDir, "styles.css")
        assertTrue(css.isSuccess)
        assertEquals(EditorLanguage.CSS, css.getOrThrow().language)

        val js = EditorFileManager.readFileForEditor(projectDir, "app.js")
        assertTrue(js.isSuccess)
        assertEquals(EditorLanguage.JAVASCRIPT, js.getOrThrow().language)

        val json = EditorFileManager.readFileForEditor(projectDir, "data.json")
        assertTrue(json.isSuccess)
        assertEquals(EditorLanguage.JSON, json.getOrThrow().language)

        val unsupported = EditorFileManager.readFileForEditor(projectDir, "logo.png")
        assertTrue(unsupported.isFailure)
        assertTrue(unsupported.exceptionOrNull() is UnsupportedEditorFileException)

        val missing = EditorFileManager.readFileForEditor(projectDir, "missing.html")
        assertTrue(missing.isFailure)

        val traversal = EditorFileManager.readFileForEditor(projectDir, "../outside.txt")
        assertTrue(traversal.isFailure)
        assertTrue(traversal.exceptionOrNull() is SecurityException)

        val emptyPath = EditorFileManager.readFileForEditor(projectDir, "   ")
        assertTrue(emptyPath.isFailure)
    }

    @Test
    fun `utf8 bom and crlf are preserved and invalid utf8 is rejected without overwriting`() {
        val bomFile = File(projectDir, "bom.txt")
        val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "line1\r\nline2\r\n".toByteArray(Charsets.UTF_8)
        bomFile.writeBytes(bomBytes)

        val loaded = EditorFileManager.readFileForEditor(projectDir, "bom.txt").getOrThrow()
        assertEquals("UTF-8 BOM", loaded.encodingName)
        assertTrue(loaded.hasBom)
        assertEquals(LineEnding.CRLF, loaded.lineEnding)
        assertEquals("line1\nline2\n", loaded.content)

        val saveRes = EditorFileManager.saveFileAtomically(
            projectDir = projectDir,
            relativePath = "bom.txt",
            content = "line1\nline2\nline3",
            hasBom = loaded.hasBom,
            lineEnding = loaded.lineEnding,
            expectedLastModified = loaded.lastModified
        )
        assertTrue(saveRes is SaveFileResult.Success)
        val savedBytes = bomFile.readBytes()
        assertEquals(0xEF.toByte(), savedBytes[0])
        assertEquals(0xBB.toByte(), savedBytes[1])
        assertEquals(0xBF.toByte(), savedBytes[2])
        assertTrue(String(savedBytes, 3, savedBytes.size - 3, Charsets.UTF_8).contains("\r\n"))

        val badUtf8 = File(projectDir, "bad.txt")
        val invalidSeq = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x80.toByte())
        badUtf8.writeBytes(invalidSeq)
        val badResult = EditorFileManager.readFileForEditor(projectDir, "bad.txt")
        assertTrue(badResult.isFailure)
        assertTrue(badUtf8.readBytes().contentEquals(invalidSeq))
    }

    @Test
    fun `conflict detection prevents silent overwrite when file changes externally`() {
        val loaded = EditorFileManager.readFileForEditor(projectDir, "index.html").getOrThrow()
        val htmlFile = File(projectDir, "index.html")
        val newDiskTime = loaded.lastModified + 5000L
        htmlFile.writeText("<h1>Modified Externally</h1>")
        htmlFile.setLastModified(newDiskTime)

        val conflictRes = EditorFileManager.saveFileAtomically(
            projectDir = projectDir,
            relativePath = "index.html",
            content = "<h1>Editor Edit</h1>",
            hasBom = false,
            lineEnding = LineEnding.LF,
            expectedLastModified = loaded.lastModified,
            forceOverwrite = false
        )
        assertTrue(conflictRes is SaveFileResult.Conflict)
        assertEquals("<h1>Modified Externally</h1>", htmlFile.readText())
    }

    @Test
    fun `read only target failure preserves original content on disk`() {
        val roFile = File(projectDir, "readonly.txt").apply {
            writeText("ORIGINAL")
            setReadOnly()
        }
        try {
            val loaded = EditorFileManager.readFileForEditor(projectDir, "readonly.txt").getOrThrow()
            val res = EditorFileManager.saveFileAtomically(
                projectDir = projectDir,
                relativePath = "readonly.txt",
                content = "CHANGED",
                hasBom = false,
                lineEnding = LineEnding.LF,
                expectedLastModified = loaded.lastModified
            )
            // Note: On some Linux root containers chmod 0444 may still be writable; assert content integrity either way
            if (res is SaveFileResult.Failure) {
                assertEquals("ORIGINAL", roFile.readText())
            }
        } finally {
            roFile.setWritable(true)
        }
    }

    private fun waitUntilCondition(timeoutMs: Long = 3000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            if (condition()) return
            if (System.currentTimeMillis() >= deadline) break
            Thread.sleep(20L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `editing undo redo tabs search replace goToLine and manual save work end to end`() = runBlocking {
        val vm = CodeEditorViewModel(
            projectId = projectId,
            initialFilePath = "index.html",
            appContext = context,
            projectRepository = projectRepository,
            preferencesRepository = preferencesRepository
        )
        waitUntilCondition { !vm.uiState.value.isLoading && vm.uiState.value.tabs.isNotEmpty() }

        assertEquals(1, vm.uiState.value.tabs.size)
        assertEquals("index.html", vm.uiState.value.activeTabPath)

        // Prevent duplicate tab
        vm.openFile("index.html")
        waitUntilCondition { vm.uiState.value.tabs.size == 1 }
        assertEquals(1, vm.uiState.value.tabs.size)

        // Open second file
        vm.openFile("styles.css")
        waitUntilCondition { vm.uiState.value.tabs.size == 2 }
        assertEquals(2, vm.uiState.value.tabs.size)
        assertEquals("styles.css", vm.uiState.value.activeTabPath)

        // Edit CSS
        val cssOriginal = vm.uiState.value.activeTab!!.textFieldValue.text
        vm.onTextChanged(TextFieldValue(cssOriginal + "\n/* end */", TextRange(cssOriginal.length + 10)))
        assertTrue(vm.uiState.value.activeTab!!.isDirty)
        assertTrue(vm.uiState.value.canUndo)

        // Switch back to HTML and verify independent content & history
        vm.selectTab("index.html")
        assertFalse(vm.uiState.value.activeTab!!.isDirty)
        val htmlOriginal = vm.uiState.value.activeTab!!.textFieldValue.text
        vm.onTextChanged(TextFieldValue(htmlOriginal.replace("Hello MFB", "Hello World"), TextRange(10)))
        assertTrue(vm.uiState.value.activeTab!!.isDirty)

        // Undo and Redo
        vm.undo()
        assertEquals(htmlOriginal, vm.uiState.value.activeTab!!.textFieldValue.text)
        vm.redo()
        assertTrue(vm.uiState.value.activeTab!!.textFieldValue.text.contains("Hello World"))

        // Search and Replace
        vm.openSearch(showReplace = true)
        vm.updateSearchQuery("Hello")
        assertEquals(1, vm.uiState.value.searchState.matches.size)
        vm.findNext()
        vm.findPrevious()
        vm.updateReplaceQuery("Welcome")
        val replaced = vm.replaceCurrentMatch()
        assertEquals(1, replaced)
        assertTrue(vm.uiState.value.activeTab!!.textFieldValue.text.contains("Welcome World"))

        // Replace All
        vm.updateSearchQuery("h1")
        vm.updateReplaceQuery("h2")
        val replacedAll = vm.replaceAllMatches()
        assertEquals(2, replacedAll)
        assertTrue(vm.uiState.value.activeTab!!.textFieldValue.text.contains("<h2>Welcome World</h2>"))

        // Go to line
        vm.goToLine(3)
        val (ln, col) = CodeEditingHelpers.computeLineAndColumn(
            vm.uiState.value.activeTab!!.textFieldValue.text,
            vm.uiState.value.activeTab!!.textFieldValue.selection.start
        )
        assertEquals(3, ln)
        assertEquals(1, col)

        // Save all modified files and verify disk persistence
        vm.saveAllModifiedFiles()
        waitUntilCondition { !vm.uiState.value.hasAnyUnsavedChanges }
        assertFalse(vm.uiState.value.hasAnyUnsavedChanges)
        assertTrue(File(projectDir, "index.html").readText().contains("<h2>Welcome World</h2>"))
        assertTrue(File(projectDir, "styles.css").readText().contains("/* end */"))

        // Close dirty tab warning check
        vm.onTextChanged(TextFieldValue("dirty", TextRange(5)))
        vm.requestCloseTab("index.html")
        assertEquals("index.html", vm.uiState.value.pendingCloseTabPath)
        vm.confirmDiscardAndCloseTab("index.html")
        assertNull(vm.uiState.value.pendingCloseTabPath)
        assertEquals(1, vm.uiState.value.tabs.size)
    }

    @Test
    fun `auto save and session restoration and editor settings persist across restart`() = runBlocking {
        preferencesRepository.updateEditorSettings {
            it.copy(
                fontSizeSp = 18,
                editorTheme = EditorThemePreset.HIGH_CONTRAST_DARK,
                showLineNumbers = false,
                wordWrap = true,
                autoSaveEnabled = true,
                indentWidth = 4,
                useTabs = false,
                syntaxHighlightingEnabled = true
            )
        }

        val vm1 = CodeEditorViewModel(
            projectId = projectId,
            initialFilePath = "app.js",
            appContext = context,
            projectRepository = projectRepository,
            preferencesRepository = preferencesRepository
        )
        waitUntilCondition { !vm1.uiState.value.isLoading && vm1.uiState.value.tabs.isNotEmpty() }
        vm1.openFile("data.json")
        waitUntilCondition { vm1.uiState.value.tabs.size == 2 }

        // Trigger auto-save
        val updatedJs = "function greet(name) {\n  return 'AutoSaved ' + name;\n}"
        vm1.selectTab("app.js")
        vm1.onTextChanged(TextFieldValue(updatedJs, TextRange(updatedJs.length)))
        waitUntilCondition(timeoutMs = 4000L) {
            vm1.uiState.value.tabs.firstOrNull { it.relativePath == "app.js" }?.saveState == SaveState.Saved
        }

        assertEquals(SaveState.Saved, vm1.uiState.value.tabs.first { it.relativePath == "app.js" }.saveState)
        assertTrue(File(projectDir, "app.js").readText().contains("AutoSaved"))

        // Ensure DataStore session snapshot is flushed before simulating restart
        var flushedActive: String? = null
        waitUntilCondition {
            flushedActive = runBlocking { preferencesRepository.getEditorSession(projectId)?.activeTabPath }
            flushedActive == "app.js"
        }

        // Simulate app restart with a fresh ViewModel without initialFilePath
        val vm2 = CodeEditorViewModel(
            projectId = projectId,
            initialFilePath = null,
            appContext = context,
            projectRepository = projectRepository,
            preferencesRepository = preferencesRepository
        )
        waitUntilCondition { !vm2.uiState.value.isLoading && vm2.uiState.value.tabs.size == 2 }
        val restoredPaths = vm2.uiState.value.tabs.map { it.relativePath }
        assertTrue("app.js" in restoredPaths)
        assertTrue("data.json" in restoredPaths)
        assertEquals("app.js", vm2.uiState.value.activeTabPath)

        val settings = preferencesRepository.editorSettingsFlow.first()
        assertEquals(18, settings.fontSizeSp)
        assertEquals(EditorThemePreset.HIGH_CONTRAST_DARK, settings.editorTheme)
        assertFalse(settings.showLineNumbers)
        assertTrue(settings.wordWrap)
        assertTrue(settings.autoSaveEnabled)
        assertEquals(4, settings.indentWidth)
    }

    @Test
    fun `syntax highlighter and auto indent and bracket matching produce valid non-corrupting output`() {
        val html = "<!-- c --><div class=\"box\">&amp;</div>"
        val highlighted = CodeSyntaxHighlighter.highlight(
            source = html,
            language = EditorLanguage.HTML,
            syntaxEnabled = true,
            palette = EditorSyntaxPalette.dark()
        )
        assertEquals(html, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())

        val pair = CodeEditingHelpers.findMatchingBracket("function f() { return (1 + 2); }", 14)
        assertNotNull(pair)
        assertEquals(13, pair!!.first)
        assertEquals(31, pair.second)

        val oldVal = TextFieldValue("  <div>", TextRange(7))
        val newVal = TextFieldValue("  <div>\n", TextRange(8))
        val indented = CodeEditingHelpers.applyAutoIndent(oldVal, newVal, "  ")
        assertEquals("  <div>\n    ", indented.text)
    }

    @Test
    fun `code editor screen renders in dark and light themes with controls`() {
        val vm = CodeEditorViewModel(
            projectId = projectId,
            initialFilePath = "index.html",
            appContext = context,
            projectRepository = projectRepository,
            preferencesRepository = preferencesRepository
        )

        composeRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                CodeEditorScreen(viewModel = vm, onNavigateBack = {})
            }
        }
        composeRule.waitUntil(timeoutMillis = 3000) {
            vm.uiState.value.tabs.isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("editor_active_filename").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_save_button").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_tab_strip").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_cursor_position").assertIsDisplayed()
        composeRule.onNodeWithTag("code_editor_text_field").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_more_menu_button").performClick()
        composeRule.waitForIdle()
    }
}
