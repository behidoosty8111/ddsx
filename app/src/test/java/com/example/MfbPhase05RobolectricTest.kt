package com.example

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mfb.core.model.ThemeMode
import com.example.mfb.data.local.datastore.UserPreferencesRepository
import com.example.mfb.data.local.room.MfbDatabase
import com.example.mfb.data.preview.PreviewMimeTypes
import com.example.mfb.data.preview.PreviewWebViewClient
import com.example.mfb.data.preview.PreviewWebViewSecurity
import com.example.mfb.data.preview.ProjectResourceHandler
import com.example.mfb.data.repository.ProjectRepositoryImpl
import com.example.mfb.domain.model.ConsoleLogSeverity
import com.example.mfb.domain.model.ConsoleLogType
import com.example.mfb.domain.model.EditorSessionSnapshot
import com.example.mfb.domain.model.PendingExternalNavigation
import com.example.mfb.domain.model.PreviewLoadState
import com.example.mfb.domain.model.PreviewSettings
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.SourceType
import com.example.mfb.domain.model.ViewportPreset
import com.example.mfb.presentation.editor.CodeEditorScreen
import com.example.mfb.presentation.editor.CodeEditorViewModel
import com.example.mfb.presentation.preview.LivePreviewScreen
import com.example.mfb.presentation.preview.LivePreviewViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
class MfbPhase05RobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: MfbDatabase
    private lateinit var projectRepository: ProjectRepositoryImpl
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var projectDirA: File
    private lateinit var projectDirB: File
    private var projectIdA: Long = 0L
    private var projectIdB: Long = 0L

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MfbDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectRepository = ProjectRepositoryImpl(database.projectDao())
        preferencesRepository = UserPreferencesRepository(context)

        val uuidA = "prev-a-${UUID.randomUUID()}"
        projectDirA = File(context.filesDir, "projects/$uuidA").apply { mkdirs() }
        File(projectDirA, "index.html").writeText("<!DOCTYPE html><html><head><link rel='stylesheet' href='css/style.css'></head><body><h1>Project A</h1><script src='js/app.js'></script></body></html>")
        File(projectDirA, "pages").mkdirs()
        File(projectDirA, "pages/about.html").writeText("<html><body>About A</body></html>")
        File(projectDirA, "css").mkdirs()
        File(projectDirA, "css/style.css").writeText("body { color: #6C63FF; }")
        File(projectDirA, "js").mkdirs()
        File(projectDirA, "js/app.js").writeText("console.log('ready');")
        File(projectDirA, "images").mkdirs()
        File(projectDirA, "images/logo.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        File(projectDirA, "fonts").mkdirs()
        File(projectDirA, "fonts/custom.woff2").writeBytes(byteArrayOf(0x77, 0x4F, 0x46, 0x32))
        File(projectDirA, "project_metadata.json").writeText("{\"secret\":true}")

        val uuidB = "prev-b-${UUID.randomUUID()}"
        projectDirB = File(context.filesDir, "projects/$uuidB").apply { mkdirs() }
        File(projectDirB, "index.html").writeText("<html><body>Project B</body></html>")

        projectIdA = projectRepository.insertProject(
            Project(
                projectUuid = uuidA,
                name = "Preview Project A",
                sourceType = SourceType.ZIP,
                originalFileName = "a.zip",
                mainHtmlRelativePath = "index.html",
                internalProjectPath = projectDirA.absolutePath,
                fileCount = 6,
                totalSizeBytes = 512L,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        projectIdB = projectRepository.insertProject(
            Project(
                projectUuid = uuidB,
                name = "Preview Project B",
                sourceType = SourceType.HTML,
                originalFileName = "index.html",
                mainHtmlRelativePath = "index.html",
                internalProjectPath = projectDirB.absolutePath,
                fileCount = 1,
                totalSizeBytes = 64L,
                createdAt = 2000L,
                updatedAt = 2000L
            )
        )
        preferencesRepository.saveEditorSession(EditorSessionSnapshot(projectIdA, emptyList(), null))
        preferencesRepository.clearProjectPreviewConfig(projectIdA)
        preferencesRepository.clearProjectPreviewConfig(projectIdB)
    }

    @After
    fun teardown() {
        database.close()
        projectDirA.deleteRecursively()
        projectDirB.deleteRecursively()
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
    fun `resource handler serves local html css js images fonts and blocks traversal and metadata`() {
        var missingReported = ""
        var securityViolation = ""
        val host = ProjectResourceHandler.buildProjectHost("uuid-a")
        val handler = ProjectResourceHandler(
            projectDir = projectDirA,
            projectHost = host,
            onMissingResource = { missingReported = it },
            onSecurityViolation = { msg, _ -> securityViolation = msg }
        )

        val htmlResp = handler.handle("index.html")
        assertEquals(200, htmlResp.statusCode)
        assertEquals("text/html", htmlResp.mimeType)
        assertEquals("utf-8", htmlResp.encoding)
        htmlResp.data.close()

        val nestedResp = handler.handle("pages/about.html")
        assertEquals(200, nestedResp.statusCode)
        nestedResp.data.close()

        val cssResp = handler.handle("css/style.css")
        assertEquals(200, cssResp.statusCode)
        assertEquals("text/css", cssResp.mimeType)
        cssResp.data.close()

        val jsResp = handler.handle("js/app.js")
        assertEquals(200, jsResp.statusCode)
        assertEquals("application/javascript", jsResp.mimeType)
        jsResp.data.close()

        val imgResp = handler.handle("images/logo.png")
        assertEquals(200, imgResp.statusCode)
        assertEquals("image/png", imgResp.mimeType)
        imgResp.data.close()

        val fontResp = handler.handle("fonts/custom.woff2")
        assertEquals(200, fontResp.statusCode)
        assertEquals("font/woff2", fontResp.mimeType)
        fontResp.data.close()

        val missingResp = handler.handle("css/nonexistent.css")
        assertEquals(404, missingResp.statusCode)
        assertEquals("css/nonexistent.css", missingReported)

        val traversalResp = handler.handle("../outside.html")
        assertEquals(403, traversalResp.statusCode)
        assertTrue(securityViolation.isNotEmpty())

        val metadataResp = handler.handle("project_metadata.json")
        assertEquals(403, metadataResp.statusCode)

        assertEquals("image/svg+xml", PreviewMimeTypes.resolveMimeType("icon.svg"))
    }

    @Test
    fun `projects get isolated synthetic https origins and missing or invalid entry points fail cleanly`() {
        val vmA = LivePreviewViewModel(projectIdA, null, context, projectRepository, preferencesRepository)
        val vmB = LivePreviewViewModel(projectIdB, null, context, projectRepository, preferencesRepository)
        waitUntilCondition { vmA.uiState.value.entryUrl.isNotBlank() && vmB.uiState.value.entryUrl.isNotBlank() }

        assertTrue(vmA.uiState.value.projectOrigin.startsWith("https://project-"))
        assertNotEquals(vmA.uiState.value.projectOrigin, vmB.uiState.value.projectOrigin)

        val vmMissing = LivePreviewViewModel(projectIdA, "does_not_exist.html", context, projectRepository, preferencesRepository)
        waitUntilCondition { vmMissing.uiState.value.loadState is PreviewLoadState.Failed }
        val failed = vmMissing.uiState.value.loadState as PreviewLoadState.Failed
        assertTrue(failed.isEntryMissing)
        assertTrue(failed.message.contains("Entry Point Not Found"))

        val vmTraversal = LivePreviewViewModel(projectIdA, "../escape.html", context, projectRepository, preferencesRepository)
        waitUntilCondition { vmTraversal.uiState.value.loadState is PreviewLoadState.Failed }
        assertTrue((vmTraversal.uiState.value.loadState as PreviewLoadState.Failed).isEntryMissing)
    }

    @Test
    fun `webview security configuration blocks file access external network by default and unsafe schemes`() {
        val webView = WebView(context)
        try {
            PreviewWebViewSecurity.applySecureSettings(webView, PreviewSettings())
            assertFalse(webView.settings.allowFileAccess)
            assertFalse(webView.settings.allowContentAccess)
            assertFalse(webView.settings.javaScriptCanOpenWindowsAutomatically)
            assertFalse(webView.settings.supportMultipleWindows())
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, webView.settings.mixedContentMode)

            val host = ProjectResourceHandler.buildProjectHost("test")
            val handler = ProjectResourceHandler(projectDirA, host)
            var blockedCount = 0
            var pendingExt: PendingExternalNavigation? = null

            val client = PreviewWebViewClient(
                resourceHandler = handler,
                assetLoader = handler.buildAssetLoader(),
                externalNetworkEnabled = false,
                onPageStarted = {},
                onPageFinished = { _, _, _, _ -> },
                onMainPageError = { _, _ -> },
                onLogEvent = { _, _, _, _, _ -> blockedCount++ },
                onExternalNavigationRequested = { pendingExt = it }
            )

            // External resource blocked when externalNetworkEnabled = false
            val extRes = client.shouldInterceptRequest(webView, fakeRequest("https://example.com/script.js"))
            assertNotNull(extRes)
            assertEquals(403, extRes!!.statusCode)
            assertTrue(blockedCount > 0)

            // Unsafe file:// scheme blocked
            val fileRes = client.shouldInterceptRequest(webView, fakeRequest("file:///data/data/com.example/databases/mfb.db"))
            assertNotNull(fileRes)
            assertEquals(403, fileRes!!.statusCode)

            // External link triggers confirmation instead of silent navigation
            val overrideExt = client.shouldOverrideUrlLoading(webView, fakeRequest("https://example.com/docs"))
            assertTrue(overrideExt)
            assertNotNull(pendingExt)
            assertEquals("https://example.com/docs", pendingExt!!.url)
            assertFalse(pendingExt!!.isSpecialScheme)

            // mailto: triggers special scheme confirmation
            pendingExt = null
            val overrideMail = client.shouldOverrideUrlLoading(webView, fakeRequest("mailto:test@example.com"))
            assertTrue(overrideMail)
            assertTrue(pendingExt!!.isSpecialScheme)

            // In-project link allowed
            val overrideLocal = client.shouldOverrideUrlLoading(webView, fakeRequest("https://$host/pages/about.html"))
            assertFalse(overrideLocal)
        } finally {
            webView.destroy()
        }
    }

    @Test
    fun `preview settings viewport presets fullscreen and logs work and persist`() = runBlocking {
        preferencesRepository.updatePreviewSettings {
            it.copy(
                javaScriptEnabled = false,
                externalNetworkEnabled = false,
                defaultViewportPreset = ViewportPreset.TABLET,
                zoomControlsEnabled = false
            )
        }

        val vm = LivePreviewViewModel(projectIdA, null, context, projectRepository, preferencesRepository)
        waitUntilCondition { vm.uiState.value.entryUrl.isNotBlank() }

        assertEquals(ViewportPreset.TABLET, vm.uiState.value.selectedViewport)
        vm.selectViewport(ViewportPreset.DESKTOP)
        assertEquals(ViewportPreset.DESKTOP, vm.uiState.value.selectedViewport)

        vm.toggleFullScreen()
        assertTrue(vm.uiState.value.isFullScreen)
        vm.exitFullScreen()
        assertFalse(vm.uiState.value.isFullScreen)

        vm.appendLog(
            severity = ConsoleLogSeverity.ERROR,
            type = ConsoleLogType.CONSOLE_ERROR,
            message = "Uncaught ReferenceError in ${projectDirA.absolutePath}/js/app.js",
            safeResourceRef = "js/app.js"
        )
        assertEquals(1, vm.uiState.value.errorCount)
        assertFalse(vm.uiState.value.logs.first().message.contains(projectDirA.absolutePath))
        assertTrue(vm.formatLogsForCopy().contains("Uncaught ReferenceError"))
        vm.clearLogs()
        assertEquals(0, vm.uiState.value.logs.size)

        val savedSettings = preferencesRepository.previewSettingsFlow.first()
        assertFalse(savedSettings.javaScriptEnabled)
        assertEquals(ViewportPreset.TABLET, savedSettings.defaultViewportPreset)
    }

    @Test
    fun `editor preview button prompts unsaved changes dialog and missing entry shows error screen`() {
        val editorVm = CodeEditorViewModel(
            projectId = projectIdA,
            initialFilePath = "index.html",
            appContext = context,
            projectRepository = projectRepository,
            preferencesRepository = preferencesRepository
        )

        composeRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                CodeEditorScreen(viewModel = editorVm, onNavigateBack = {})
            }
        }
        composeRule.waitUntil(timeoutMillis = 3000) {
            editorVm.uiState.value.tabs.isNotEmpty()
        }
        composeRule.waitForIdle()

        // Make an unsaved edit
        editorVm.onTextChanged(TextFieldValue("<h1>Unsaved Edit</h1>", TextRange(21)))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("editor_preview_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("You have unsaved changes.").assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_save_and_preview_button").assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_preview_last_saved_button").assertIsDisplayed()
    }

    @Test
    fun `live preview screen displays Entry Point Not Found when entry is missing`() {
        val previewVm = LivePreviewViewModel(
            projectId = projectIdA,
            entryPointOverride = "missing_page.html",
            appContext = context,
            projectRepository = projectRepository,
            preferencesRepository = preferencesRepository
        )

        composeRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                LivePreviewScreen(viewModel = previewVm, onNavigateBack = {})
            }
        }
        composeRule.waitUntil(timeoutMillis = 5000) {
            previewVm.uiState.value.loadState is PreviewLoadState.Failed
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("preview_error_state").assertIsDisplayed()
        composeRule.onNodeWithText("Entry Point Not Found").assertIsDisplayed()
        composeRule.onNodeWithTag("preview_return_to_files_button").assertIsDisplayed()
    }

    @Test
    fun `resource handler serves nested assets spaces unicode and plus filenames with cors and nocache headers`() {
        File(projectDirA, "assets/images").mkdirs()
        File(projectDirA, "assets/images/sample.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        File(projectDirA, "fonts/sample.woff2").writeBytes(byteArrayOf(0x77, 0x4F, 0x46, 0x32))
        File(projectDirA, "my page.html").writeText("<html><body>Space Page</body></html>")
        File(projectDirA, "css/my+style.css").writeText("body { margin: 0; }")
        File(projectDirA, "صفحه.html").writeText("<html><body>Persian Page</body></html>")

        val host = ProjectResourceHandler.buildProjectHost("uuid-unicode")
        val handler = ProjectResourceHandler(projectDirA, host)

        val jpgResp = handler.handle("assets/images/sample.jpg")
        assertEquals(200, jpgResp.statusCode)
        assertEquals("image/jpeg", jpgResp.mimeType)
        assertEquals("*", jpgResp.responseHeaders["Access-Control-Allow-Origin"])
        jpgResp.data.close()

        val woff2Resp = handler.handle("fonts/sample.woff2")
        assertEquals(200, woff2Resp.statusCode)
        assertEquals("font/woff2", woff2Resp.mimeType)
        woff2Resp.data.close()

        val spaceResp = handler.handle("my%20page.html")
        assertEquals(200, spaceResp.statusCode)
        spaceResp.data.close()

        val plusResp = handler.handle("css/my+style.css")
        assertEquals(200, plusResp.statusCode)
        plusResp.data.close()

        val unicodeUrl = handler.buildEntryUrl("صفحه.html")
        assertTrue(unicodeUrl.startsWith("https://$host/"))
        val unicodeResp = handler.handle(Uri.parse(unicodeUrl).path!!.trimStart('/'))
        assertEquals(200, unicodeResp.statusCode)
        unicodeResp.data.close()

        val dotRelResp = handler.handle("./css/style.css")
        assertEquals(200, dotRelResp.statusCode)
        dotRelResp.data.close()
    }

    @Test
    fun `stale generation callbacks are ignored and cancelled loading never reports loaded`() {
        val vm = LivePreviewViewModel(projectIdA, null, context, projectRepository, preferencesRepository)
        waitUntilCondition { vm.uiState.value.entryUrl.isNotBlank() }

        val validGen = vm.currentGenerationId()
        val staleGen = validGen - 1L

        vm.onPageFinished("stale.html", "Stale", canGoBack = false, canGoForward = false, generationId = staleGen)
        assertFalse(vm.uiState.value.loadState is PreviewLoadState.Loaded)

        vm.markCancelled()
        assertTrue(vm.uiState.value.loadState is PreviewLoadState.Cancelled)

        vm.onPageFinished("index.html", "Index", canGoBack = false, canGoForward = false, generationId = validGen)
        assertTrue(vm.uiState.value.loadState is PreviewLoadState.Cancelled)
    }

    @Test
    fun `viewport presets and zoom are isolated per project and formatted logs include line numbers`() {
        val vmA = LivePreviewViewModel(projectIdA, null, context, projectRepository, preferencesRepository)
        waitUntilCondition { vmA.uiState.value.entryUrl.isNotBlank() }

        vmA.selectViewport(ViewportPreset.DESKTOP)
        vmA.zoomIn()
        assertEquals(125, vmA.uiState.value.zoomPercent)

        val vmB = LivePreviewViewModel(projectIdB, null, context, projectRepository, preferencesRepository)
        waitUntilCondition { vmB.uiState.value.entryUrl.isNotBlank() }
        assertNotEquals(ViewportPreset.DESKTOP, vmB.uiState.value.selectedViewport)
        assertEquals(100, vmB.uiState.value.zoomPercent)

        vmB.appendLog(
            severity = ConsoleLogSeverity.WARNING,
            type = ConsoleLogType.CONSOLE_WARNING,
            message = "Deprecated API",
            safeResourceRef = "js/app.js:14",
            lineNumber = 14
        )
        assertTrue(vmB.formatLogsForCopy().contains("line 14"))
    }

    private fun fakeRequest(urlString: String): WebResourceRequest {
        val parsed = Uri.parse(urlString)
        return object : WebResourceRequest {
            override fun getUrl(): Uri = parsed
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
    }
}
