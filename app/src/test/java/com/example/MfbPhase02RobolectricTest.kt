package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mfb.core.security.ImportSecurityException
import com.example.mfb.core.security.ZipSecurityValidator
import com.example.mfb.data.engine.CountingInputStream
import com.example.mfb.data.engine.HtmlDetectionResult
import com.example.mfb.data.engine.HtmlEntryDetector
import com.example.mfb.data.engine.HtmlImportEngine
import com.example.mfb.data.engine.HtmlImportResult
import com.example.mfb.data.engine.ProjectStorageManager
import com.example.mfb.data.engine.ZipImportEngine
import com.example.mfb.data.engine.ZipImportResult
import com.example.mfb.data.local.room.MfbDatabase
import com.example.mfb.data.preview.ProjectResourceHandler
import com.example.mfb.data.repository.ProjectRepositoryImpl
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MfbPhase02RobolectricTest {

    private lateinit var context: Context
    private lateinit var database: MfbDatabase
    private lateinit var projectRepository: ProjectRepositoryImpl
    private lateinit var tempTestDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MfbDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectRepository = ProjectRepositoryImpl(database.projectDao())
        tempTestDir = File(context.cacheDir, "test_fixtures_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        tempTestDir.deleteRecursively()
        File(context.filesDir, "projects").deleteRecursively()
    }

    private fun createZipArchive(fileName: String, entries: Map<String, String>): File {
        val zipFile = File(tempTestDir, fileName)
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            entries.forEach { (name, content) ->
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return zipFile
    }

    @Test
    fun testValidZipExtractionAndPersistence() = runBlocking {
        val entries = mapOf(
            "index.html" to "<!DOCTYPE html><html><head><title>App</title></head><body><h1>Hello MFB</h1></body></html>",
            "css/style.css" to "body { background: #000; color: #fff; }",
            "js/app.js" to "console.log('MFB App Running');"
        )
        val zipFile = createZipArchive("valid_app.zip", entries)
        val uri = Uri.fromFile(zipFile)

        val result = ZipImportEngine.importFromUri(context, uri, customProjectName = "Valid WebApp")
        assertTrue("Import should succeed", result is ZipImportResult.Success)

        val project = (result as ZipImportResult.Success).project
        assertEquals("Valid WebApp", project.name)
        assertEquals("index.html", project.mainHtmlRelativePath)
        assertEquals(3, project.fileCount)
        assertEquals(SourceType.ZIP, project.sourceType)

        // Verify stored in private internal storage
        val projectDir = File(project.internalProjectPath)
        assertTrue("Project dir must exist", projectDir.exists())
        assertTrue("index.html must exist", File(projectDir, "index.html").exists())
        assertTrue("css/style.css must exist", File(projectDir, "css/style.css").exists())
        assertTrue("metadata must exist", File(projectDir, ProjectStorageManager.METADATA_FILE_NAME).exists())

        // Verify database persistence
        val insertedId = projectRepository.insertProject(project)
        val fromDb = projectRepository.getProjectById(insertedId)
        assertNotNull(fromDb)
        assertEquals("Valid WebApp", fromDb?.name)
        assertEquals("index.html", fromDb?.mainHtmlRelativePath)
        assertEquals(3, fromDb?.fileCount)
    }

    @Test
    fun testNestedIndexHtmlDetection() = runBlocking {
        val entries = mapOf(
            "dist/public/index.html" to "<html><body><h1>Nested</h1></body></html>",
            "dist/public/bundle.js" to "console.log('bundle');"
        )
        val zipFile = createZipArchive("nested_app.zip", entries)
        val uri = Uri.fromFile(zipFile)

        val result = ZipImportEngine.importFromUri(context, uri)
        assertTrue(result is ZipImportResult.Success)
        val project = (result as ZipImportResult.Success).project
        assertEquals("dist/public/index.html", project.mainHtmlRelativePath)
    }

    @Test
    fun testIndexHtmFallbackDetection() = runBlocking {
        val entries = mapOf(
            "index.htm" to "<html><body><h1>Htm Fallback</h1></body></html>",
            "styles.css" to "h1 { color: red; }"
        )
        val zipFile = createZipArchive("htm_app.zip", entries)
        val uri = Uri.fromFile(zipFile)

        val result = ZipImportEngine.importFromUri(context, uri)
        assertTrue(result is ZipImportResult.Success)
        val project = (result as ZipImportResult.Success).project
        assertEquals("index.htm", project.mainHtmlRelativePath)
    }

    @Test
    fun testSingleNonIndexHtmlDetection() = runBlocking {
        val entries = mapOf(
            "main.html" to "<html><body><h1>Single Page</h1></body></html>",
            "assets/data.json" to "{}"
        )
        val zipFile = createZipArchive("single_main.zip", entries)
        val uri = Uri.fromFile(zipFile)

        val result = ZipImportEngine.importFromUri(context, uri)
        assertTrue(result is ZipImportResult.Success)
        val project = (result as ZipImportResult.Success).project
        assertEquals("main.html", project.mainHtmlRelativePath)
    }

    @Test
    fun testMultipleHtmlCandidatesRequiresSelection() = runBlocking {
        val entries = mapOf(
            "page1.html" to "<html><body><h1>Page 1</h1></body></html>",
            "page2.html" to "<html><body><h1>Page 2</h1></body></html>",
            "page3.html" to "<html><body><h1>Page 3</h1></body></html>"
        )
        val zipFile = createZipArchive("multi_html.zip", entries)
        val uri = Uri.fromFile(zipFile)

        val result = ZipImportEngine.importFromUri(context, uri)
        assertTrue("Should require candidate selection", result is ZipImportResult.NeedsEntryPointChoice)

        val choice = result as ZipImportResult.NeedsEntryPointChoice
        assertEquals(3, choice.candidates.size)
        assertTrue(choice.candidates.contains("page1.html"))
        assertTrue(choice.candidates.contains("page2.html"))
        assertTrue(choice.candidates.contains("page3.html"))

        // Complete with chosen entry point
        val completed = ZipImportEngine.completeWithSelectedEntryPoint(
            context = context,
            tempDirName = choice.tempDirName,
            selectedEntryPoint = "page2.html",
            projectName = choice.suggestedName,
            originalFileName = choice.originalFileName
        )
        assertTrue(completed is ZipImportResult.Success)
        val project = (completed as ZipImportResult.Success).project
        assertEquals("page2.html", project.mainHtmlRelativePath)
    }

    @Test
    fun testArchiveWithoutHtmlIsRejected() = runBlocking {
        val entries = mapOf(
            "image.png" to "fake-image-bytes",
            "script.js" to "console.log('no html');",
            "styles.css" to "body { color: blue; }"
        )
        val zipFile = createZipArchive("no_html.zip", entries)
        val uri = Uri.fromFile(zipFile)

        val result = ZipImportEngine.importFromUri(context, uri)
        assertTrue("Archive without HTML must fail", result is ZipImportResult.Error)
        val error = result as ZipImportResult.Error
        assertTrue(error.exception is ImportSecurityException.NoHtmlEntryPointException)
    }

    @Test
    fun testZipSlipDirectoryTraversalRejected() = runBlocking {
        val zipFile = File(tempTestDir, "zip_slip.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // Malicious traversal entry
            val slipEntry = ZipEntry("../../dangerous_slip.txt")
            zos.putNextEntry(slipEntry)
            zos.write("ATTACK".toByteArray())
            zos.closeEntry()

            val normalEntry = ZipEntry("index.html")
            zos.putNextEntry(normalEntry)
            zos.write("<html><body>Legit</body></html>".toByteArray())
            zos.closeEntry()
        }

        val result = ZipImportEngine.importFromUri(context, Uri.fromFile(zipFile))
        assertTrue("Zip Slip attempt must be blocked", result is ZipImportResult.Error)
        val error = result as ZipImportResult.Error
        assertTrue(error.exception is ImportSecurityException.ZipSlipException)

        // Verify malicious file was NOT extracted outside
        val outsideFile = File(context.cacheDir.parentFile, "dangerous_slip.txt")
        assertFalse("Zip slip file must not exist on filesystem", outsideFile.exists())
    }

    @Test
    fun testHtmlFileImportWithMissingLocalAssetDetection() = runBlocking {
        val htmlContent = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <link rel="stylesheet" href="css/style.css">
                <script src="js/bundle.js"></script>
                <script src="https://cdn.example.com/lib.js"></script>
            </head>
            <body>
                <img src="images/logo.png" alt="Logo">
                <h1>Standalone Web App</h1>
            </body>
            </html>
        """.trimIndent()

        val htmlFile = File(tempTestDir, "index.html").apply { writeText(htmlContent) }
        val uri = Uri.fromFile(htmlFile)

        val result = HtmlImportEngine.importFromUri(context, uri, customProjectName = "Standalone HTML")
        assertTrue(result is HtmlImportResult.Success)

        val success = result as HtmlImportResult.Success
        assertEquals("Standalone HTML", success.project.name)
        assertEquals(1, success.project.fileCount)
        assertEquals("index.html", success.project.mainHtmlRelativePath)
        assertEquals(SourceType.HTML, success.project.sourceType)

        // Verify referenced relative local assets are detected as warnings
        assertEquals(3, success.warnings.size)
        assertTrue(success.warnings.contains("css/style.css"))
        assertTrue(success.warnings.contains("js/bundle.js"))
        assertTrue(success.warnings.contains("images/logo.png"))
        assertFalse("External CDN scripts must not be flagged as missing local assets", success.warnings.contains("https://cdn.example.com/lib.js"))
    }

    @Test
    fun testInvalidHtmlFileRejected() = runBlocking {
        val nonHtmlFile = File(tempTestDir, "plain.txt").apply { writeText("Plain text content without html tags") }
        val result = HtmlImportEngine.importFromUri(context, Uri.fromFile(nonHtmlFile))
        assertTrue(result is HtmlImportResult.Error)
    }

    @Test
    fun testProjectStorageManagerRenameAndDelete() = runBlocking {
        val entries = mapOf(
            "index.html" to "<html><body><h1>Delete Me</h1></body></html>"
        )
        val zipFile = createZipArchive("to_delete.zip", entries)
        val result = ZipImportEngine.importFromUri(context, Uri.fromFile(zipFile), customProjectName = "Original Name")
        assertTrue(result is ZipImportResult.Success)
        val project = (result as ZipImportResult.Success).project

        val id = projectRepository.insertProject(project)

        // 1. Rename
        projectRepository.updateProjectName(id, "Renamed WebApp")
        val renamed = projectRepository.getProjectById(id)
        assertEquals("Renamed WebApp", renamed?.name)

        // 2. Storage & Database Deletion
        val projectDir = File(project.internalProjectPath)
        assertTrue("Dir exists before delete", projectDir.exists())

        ProjectStorageManager.deleteProject(context, project.projectUuid)
        projectRepository.deleteProjectById(id)

        assertFalse("Dir deleted recursively", projectDir.exists())
        assertEquals(null, projectRepository.getProjectById(id))
    }

    @Test
    fun testNestedWebsiteFolderAndAssetsExtraction() = runBlocking {
        val entries = mapOf(
            "website/index.html" to "<!DOCTYPE html><html><head><link rel=\"stylesheet\" href=\"assets/css/style.css\"></head><body><h1>Website</h1><script src=\"assets/js/app.js\"></script></body></html>",
            "website/assets/css/style.css" to "body { margin: 0; }",
            "website/assets/js/app.js" to "console.log('ok');"
        )
        val zipFile = createZipArchive("website_bundle.zip", entries)
        val result = ZipImportEngine.importFromUri(context, Uri.fromFile(zipFile))
        assertTrue("Expected Success, got $result", result is ZipImportResult.Success)
        val project = (result as ZipImportResult.Success).project
        assertEquals("website/index.html", project.mainHtmlRelativePath)
        assertEquals(3, project.fileCount)
        assertTrue(File(project.internalProjectPath, "website/assets/css/style.css").exists())
        assertTrue(File(project.internalProjectPath, "website/assets/js/app.js").exists())
    }

    @Test
    fun testProjectNameFolderAutoSelection() = runBlocking {
        val entries = mapOf(
            "project-name/index.html" to "<html><body><h1>Project</h1></body></html>",
            "project-name/images/logo.png" to "png-bytes"
        )
        val zipFile = createZipArchive("project_name.zip", entries)
        val result = ZipImportEngine.importFromUri(context, Uri.fromFile(zipFile))
        assertTrue("Expected Success, got $result", result is ZipImportResult.Success)
        val project = (result as ZipImportResult.Success).project
        assertEquals("project-name/index.html", project.mainHtmlRelativePath)
    }

    @Test
    fun testWindowsBackslashZipEntriesNormalized() = runBlocking {
        val entries = mapOf(
            "website\\index.html" to "<html><body><link rel=\"stylesheet\" href=\"css/style.css\"><h1>WinZip</h1></body></html>",
            "website\\css\\style.css" to "h1 { color: green; }"
        )
        val zipFile = createZipArchive("windows_paths.zip", entries)
        val result = ZipImportEngine.importFromUri(context, Uri.fromFile(zipFile))
        assertTrue("Expected Success, got $result", result is ZipImportResult.Success)
        val project = (result as ZipImportResult.Success).project
        assertEquals("website/index.html", project.mainHtmlRelativePath)
        assertTrue(
            "Nested CSS file must exist under website/css/style.css",
            File(project.internalProjectPath, "website/css/style.css").exists()
        )
    }

    @Test
    fun testUnicodeAndSpacesInFilenames() = runBlocking {
        val entries = mapOf(
            "پروژه من/index.html" to "<!DOCTYPE html><html><body><h1>سلام دنیا</h1></body></html>",
            "پروژه من/css/استایل من.css" to "body { direction: rtl; }",
            "پروژه من/js/app script.js" to "console.log('unicode');"
        )
        val zipFile = createZipArchive("پروژه تست.zip", entries)
        val result = ZipImportEngine.importFromUri(context, Uri.fromFile(zipFile))
        assertTrue("Expected Success, got $result", result is ZipImportResult.Success)
        val project = (result as ZipImportResult.Success).project
        assertEquals("پروژه من/index.html", project.mainHtmlRelativePath)
        assertTrue(File(project.internalProjectPath, "پروژه من/css/استایل من.css").exists())
        assertTrue(File(project.internalProjectPath, "پروژه من/js/app script.js").exists())
    }

    @Test
    fun testMultipleIndexHtmlAtSameShallowestDepthRequiresChoice() = runBlocking {
        val entries = mapOf(
            "site1/index.html" to "<html><body>Site 1</body></html>",
            "site2/index.html" to "<html><body>Site 2</body></html>"
        )
        val zipFile = createZipArchive("two_sites.zip", entries)
        val result = ZipImportEngine.importFromUri(context, Uri.fromFile(zipFile))
        assertTrue("Expected NeedsEntryPointChoice, got $result", result is ZipImportResult.NeedsEntryPointChoice)
        val choice = result as ZipImportResult.NeedsEntryPointChoice
        assertEquals(listOf("site1/index.html", "site2/index.html"), choice.candidates)

        val completed = ZipImportEngine.completeWithSelectedEntryPoint(
            context = context,
            tempDirName = choice.tempDirName,
            selectedEntryPoint = "site2/index.html",
            projectName = choice.suggestedName,
            originalFileName = choice.originalFileName
        )
        assertTrue(completed is ZipImportResult.Success)
        assertEquals("site2/index.html", (completed as ZipImportResult.Success).project.mainHtmlRelativePath)
    }

    @Test
    fun testEmptyAndCorruptedZipsRejectedWithClearError() = runBlocking {
        // 1. 0-byte file
        val zeroByteFile = File(tempTestDir, "empty_zero.zip").apply { writeBytes(ByteArray(0)) }
        val zeroResult = ZipImportEngine.importFromUri(context, Uri.fromFile(zeroByteFile))
        assertTrue("0-byte file must fail", zeroResult is ZipImportResult.Error)
        assertTrue((zeroResult as ZipImportResult.Error).message.isNotBlank())

        // 2. Empty valid ZIP (no entries)
        val emptyZip = createZipArchive("empty_archive.zip", emptyMap())
        val emptyArchiveResult = ZipImportEngine.importFromUri(context, Uri.fromFile(emptyZip))
        assertTrue("Empty ZIP must fail", emptyArchiveResult is ZipImportResult.Error)

        // 3. Corrupted / non-ZIP bytes renamed to .zip
        val corruptedZip = File(tempTestDir, "corrupted.zip").apply {
            writeText("This is not a valid PKZIP archive at all.")
        }
        val corruptedResult = ZipImportEngine.importFromUri(context, Uri.fromFile(corruptedZip))
        assertTrue("Corrupted ZIP must fail", corruptedResult is ZipImportResult.Error)
        assertTrue((corruptedResult as ZipImportResult.Error).message.isNotBlank())
    }

    @Test
    fun testDuplicateZipImportCreatesIndependentProjects() = runBlocking {
        val entries = mapOf("index.html" to "<html><body><h1>Same</h1></body></html>")
        val zipFile = createZipArchive("duplicate.zip", entries)
        val uri = Uri.fromFile(zipFile)

        val first = ZipImportEngine.importFromUri(context, uri) as ZipImportResult.Success
        val second = ZipImportEngine.importFromUri(context, uri) as ZipImportResult.Success

        assertFalse(first.project.projectUuid == second.project.projectUuid)
        assertFalse(first.project.internalProjectPath == second.project.internalProjectPath)

        val id1 = projectRepository.insertProject(first.project)
        val id2 = projectRepository.insertProject(second.project)
        assertFalse(id1 == id2)
        assertNotNull(projectRepository.getProjectById(id1))
        assertNotNull(projectRepository.getProjectById(id2))
    }

    @Test
    fun testLargeZipWithinPermittedLimitsSucceeds() = runBlocking {
        val largePayload = "a".repeat(256 * 1024) // 256 KB per file x 6 = 1.5 MB
        val entries = buildMap {
            put("index.html", "<html><body><h1>Large</h1></body></html>")
            for (i in 1..6) {
                put("data/chunk_$i.txt", largePayload)
            }
        }
        val zipFile = createZipArchive("large_valid.zip", entries)
        val result = ZipImportEngine.importFromUri(context, Uri.fromFile(zipFile))
        assertTrue("Large valid ZIP should succeed", result is ZipImportResult.Success)
        assertEquals(7, (result as ZipImportResult.Success).project.fileCount)
    }

    @Test
    fun testImportedZipOpensInLivePreviewResourceHandler() = runBlocking {
        val entries = mapOf(
            "app/index.html" to "<!DOCTYPE html><html><head><link rel=\"stylesheet\" href=\"css/main.css\"></head><body><h1>Live</h1><script src=\"js/app.js\"></script></body></html>",
            "app/css/main.css" to "body { color: #222; }",
            "app/js/app.js" to "window.__ready = true;"
        )
        val zipFile = createZipArchive("preview_ready.zip", entries)
        val importResult = ZipImportEngine.importFromUri(context, Uri.fromFile(zipFile))
        assertTrue(importResult is ZipImportResult.Success)
        val insertedId = projectRepository.insertProject((importResult as ZipImportResult.Success).project)
        val savedProject = projectRepository.getProjectById(insertedId)!!

        val handler = ProjectResourceHandler(
            projectDir = File(savedProject.internalProjectPath),
            projectHost = "project-${savedProject.projectUuid}.mfb.local"
        )
        val htmlResp = handler.handle(savedProject.mainHtmlRelativePath)
        assertEquals(200, htmlResp.statusCode)
        assertEquals("text/html", htmlResp.mimeType)

        val cssResp = handler.handle("app/css/main.css")
        assertEquals(200, cssResp.statusCode)
        assertEquals("text/css", cssResp.mimeType)

        val jsResp = handler.handle("app/js/app.js")
        assertEquals(200, jsResp.statusCode)
        assertEquals("application/javascript", jsResp.mimeType)
    }
}
