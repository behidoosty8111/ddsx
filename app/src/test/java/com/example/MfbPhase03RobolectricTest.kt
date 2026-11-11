package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mfb.data.changelog.ChangelogRepositoryImpl
import com.example.mfb.data.engine.ProjectFileManager
import com.example.mfb.data.engine.ProjectHealthChecker
import com.example.mfb.data.engine.ProjectStorageManager
import com.example.mfb.data.local.datastore.UserPreferencesRepository
import com.example.mfb.data.local.room.MfbDatabase
import com.example.mfb.data.repository.ProjectRepositoryImpl
import com.example.mfb.domain.model.FileCategory
import com.example.mfb.domain.model.FilePreviewContent
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.ProjectHealthStatus
import com.example.mfb.domain.model.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MfbPhase03RobolectricTest {

    private lateinit var context: Context
    private lateinit var database: MfbDatabase
    private lateinit var projectRepository: ProjectRepositoryImpl
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var changelogRepository: ChangelogRepositoryImpl
    private lateinit var projectDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MfbDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectRepository = ProjectRepositoryImpl(database.projectDao())
        userPreferencesRepository = UserPreferencesRepository(context)
        changelogRepository = ChangelogRepositoryImpl(context)

        val uuid = UUID.randomUUID().toString()
        projectDir = ProjectStorageManager.getProjectDir(context, uuid)
        File(projectDir, "index.html").writeText("<!DOCTYPE html><html><body><h1>MFB</h1></body></html>")
        File(projectDir, ProjectStorageManager.METADATA_FILE_NAME).writeText("{}")
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "projects").deleteRecursively()
    }

    @Test
    fun testPathTraversalAndFilenameValidation() {
        assertTrue(ProjectFileManager.resolveSafePath(projectDir, "../outside.txt").isFailure)
        assertTrue(ProjectFileManager.resolveSafePath(projectDir, "sub/../../escape").isFailure)
        assertTrue(ProjectFileManager.validateItemName("..").isFailure)
        assertTrue(ProjectFileManager.validateItemName("a/b").isFailure)
        assertTrue(ProjectFileManager.validateItemName("bad*name.html").isFailure)
        assertTrue(ProjectFileManager.validateItemName(ProjectStorageManager.METADATA_FILE_NAME).isFailure)
        assertTrue(ProjectFileManager.createEmptyFile(projectDir, "", "malware.exe").isFailure)
    }

    @Test
    fun testFileAndFolderCrudOperationsAndReadOnlyPreview() {
        val folderRes = ProjectFileManager.createFolder(projectDir, "", "styles")
        assertTrue(folderRes.isSuccess)
        assertTrue(File(projectDir, "styles").isDirectory)

        val fileRes = ProjectFileManager.createEmptyFile(projectDir, "styles", "main.css")
        assertTrue(fileRes.isSuccess)
        File(projectDir, "styles/main.css").writeText("body { color: #6C63FF; }")

        assertTrue(ProjectFileManager.createEmptyFile(projectDir, "styles", "main.css").isFailure)

        val renameRes = ProjectFileManager.renameItem(projectDir, "styles/main.css", "theme.css")
        assertTrue(renameRes.isSuccess)
        assertFalse(File(projectDir, "styles/main.css").exists())
        assertTrue(File(projectDir, "styles/theme.css").exists())

        // Read-only text preview
        val preview = ProjectFileManager.readFilePreview(projectDir, "styles/theme.css").getOrThrow()
        assertTrue(preview is FilePreviewContent.TextPreview)
        assertEquals("body { color: #6C63FF; }", (preview as FilePreviewContent.TextPreview).content)
        assertFalse(preview.truncated)

        // Truncated preview when exceeding limit
        val truncatedPreview = ProjectFileManager.readFilePreview(projectDir, "styles/theme.css", maxTextBytes = 4).getOrThrow()
        assertTrue((truncatedPreview as FilePreviewContent.TextPreview).truncated)

        // List directory excludes project_metadata.json
        val rootList = ProjectFileManager.listDirectory(projectDir, "").getOrThrow()
        assertFalse(rootList.any { it.name == ProjectStorageManager.METADATA_FILE_NAME })
        assertTrue(rootList.any { it.name == "styles" && it.isDirectory })
        assertTrue(rootList.any { it.name == "index.html" && !it.isDirectory })

        // Search by category
        val cssSearch = ProjectFileManager.searchFiles(projectDir, "", FileCategory.STYLES).getOrThrow()
        assertEquals(1, cssSearch.size)
        assertEquals("styles/theme.css", cssSearch.first().relativePath)

        // Delete folder recursively
        val deleteRes = ProjectFileManager.deleteItem(projectDir, "styles")
        assertTrue(deleteRes.isSuccess)
        assertFalse(File(projectDir, "styles").exists())
    }

    @Test
    fun testProjectDuplicationAndZipExport() {
        ProjectFileManager.createFolder(projectDir, "", "js").getOrThrow()
        File(projectDir, "js/app.js").writeText("console.log('ok');")

        val targetDir = ProjectStorageManager.getProjectDir(context, UUID.randomUUID().toString())
        val dupResult = ProjectFileManager.duplicateProjectFiles(projectDir, targetDir)
        assertTrue(dupResult.isSuccess)
        val (count, _) = dupResult.getOrThrow()
        assertEquals(2, count)
        assertTrue(File(targetDir, "index.html").exists())
        assertTrue(File(targetDir, "js/app.js").exists())
        assertFalse(File(targetDir, ProjectStorageManager.METADATA_FILE_NAME).exists())
        assertNotEquals(projectDir.canonicalPath, targetDir.canonicalPath)

        val baos = ByteArrayOutputStream()
        val exportResult = ProjectFileManager.exportProjectToZip(projectDir, baos)
        assertTrue(exportResult.isSuccess)
        assertEquals(2, exportResult.getOrThrow())

        val entryNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(baos.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                entry = zis.nextEntry
            }
        }
        assertTrue(entryNames.contains("index.html"))
        assertTrue(entryNames.contains("js/app.js"))
        assertFalse(entryNames.contains(ProjectStorageManager.METADATA_FILE_NAME))
    }

    @Test
    fun testProjectSearchPersianEnglishCaseWhitespaceAndEmpty() = runBlocking {
        projectRepository.insertProject(
            Project(
                projectUuid = UUID.randomUUID().toString(),
                name = "فروشگاه آنلاین",
                originalFileName = "shop.zip",
                sourceType = SourceType.ZIP
            )
        )
        projectRepository.insertProject(
            Project(
                projectUuid = UUID.randomUUID().toString(),
                name = "Portfolio WebApp",
                originalFileName = "portfolio.html",
                sourceType = SourceType.HTML
            )
        )

        // Persian search
        val persian = projectRepository.searchProjects("فروشگاه").first()
        assertEquals(1, persian.size)
        assertEquals("فروشگاه آنلاین", persian.first().name)

        // English case-insensitive + leading/trailing spaces
        val english = projectRepository.searchProjects("  portfolio  ").first()
        assertEquals(1, english.size)
        assertEquals("Portfolio WebApp", english.first().name)

        // Empty search returns all
        val all = projectRepository.searchProjects("").first()
        assertEquals(2, all.size)

        // No-result search
        val none = projectRepository.searchProjects("nonexistent_xyz_123").first()
        assertTrue(none.isEmpty())
    }

    @Test
    fun testProjectHealthCheckerAndEntryPointManagement() = runBlocking {
        val (count, bytes) = ProjectStorageManager.calculateStats(projectDir)
        val p = Project(
            projectUuid = projectDir.name,
            name = "Healthy App",
            sourceType = SourceType.HTML,
            mainHtmlRelativePath = "index.html",
            internalProjectPath = projectDir.absolutePath,
            fileCount = count,
            totalSizeBytes = bytes
        )
        val id = projectRepository.insertProject(p)
        val saved = projectRepository.getProjectById(id)!!

        // 1. Healthy
        val healthyReport = ProjectHealthChecker.checkProjectHealth(context, saved)
        assertEquals(ProjectHealthStatus.HEALTHY, healthyReport.status)
        assertTrue(healthyReport.directoryExists)
        assertTrue(healthyReport.entryPointExists)
        assertTrue(healthyReport.metadataMatchesFiles)

        // 2. Entry point management: add another HTML file and switch to it
        File(projectDir, "app.html").writeText("<html><head><link rel=\"stylesheet\" href=\"missing.css\"><script src=\"https://cdn.example.com/a.js\"></script></head><body>App</body></html>")
        val candidates = ProjectFileManager.findHtmlEntryCandidates(projectDir)
        assertEquals(listOf("app.html", "index.html"), candidates)

        projectRepository.updateEntryPoint(id, "app.html")
        val switched = projectRepository.getProjectById(id)!!
        assertEquals("app.html", switched.mainHtmlRelativePath)

        // 3. Warning due to missing local asset + metadata mismatch (ignoring remote https:// URL)
        val warningReport = ProjectHealthChecker.checkProjectHealth(context, switched)
        assertEquals(ProjectHealthStatus.WARNING, warningReport.status)
        assertTrue(warningReport.missingLocalAssets.contains("missing.css"))
        assertFalse(warningReport.missingLocalAssets.any { it.contains("cdn.example.com") })

        // 4. Invalid entry point when deleted
        File(projectDir, "app.html").delete()
        val invalidEntryReport = ProjectHealthChecker.checkProjectHealth(context, switched)
        assertEquals(ProjectHealthStatus.INVALID_ENTRY_POINT, invalidEntryReport.status)

        // 5. Missing directory -> STORAGE_ERROR
        projectDir.deleteRecursively()
        val missingDirReport = ProjectHealthChecker.checkProjectHealth(context, switched)
        assertEquals(ProjectHealthStatus.STORAGE_ERROR, missingDirReport.status)
    }

    @Test
    fun testProjectOrganizationPersistenceAndDuplicateNameCheck() = runBlocking {
        val project = Project(
            projectUuid = UUID.randomUUID().toString(),
            name = "Workspace App",
            sourceType = SourceType.HTML,
            internalProjectPath = projectDir.absolutePath
        )
        val id = projectRepository.insertProject(project)
        val id2 = projectRepository.insertProject(project.copy(id = 0, projectUuid = UUID.randomUUID().toString(), name = "Other App"))

        assertFalse(projectRepository.existsByNameExcludingId("Workspace App", id))
        assertTrue(projectRepository.existsByNameExcludingId("Other App", id))
        assertTrue(projectRepository.existsByNameExcludingId("other app", id))

        projectRepository.toggleFavorite(id, true)
        projectRepository.recordProjectOpened(id)
        projectRepository.updateProjectMetadata(id, "Workspace App v2", "My custom description", listOf("pwa", "portfolio"))
        projectRepository.updateProjectStats(id, 4, 2048L)
        projectRepository.updateHealthStatus(id, ProjectHealthStatus.HEALTHY, "Verified", emptyList())

        val updated = projectRepository.getProjectById(id)
        assertNotNull(updated)
        assertTrue(updated!!.isFavorite)
        assertTrue(updated.lastOpenedAt > 0L)
        assertEquals("Workspace App v2", updated.name)
        assertEquals("My custom description", updated.description)
        assertEquals(listOf("pwa", "portfolio"), updated.tags)
        assertEquals(4, updated.fileCount)
        assertEquals(2048L, updated.totalSizeBytes)
        assertEquals(ProjectHealthStatus.HEALTHY, updated.healthStatus)

        // Delete one, verify unrelated remains
        projectRepository.deleteProjectById(id)
        assertEquals(1, projectRepository.getProjectsCount())
        assertNotNull(projectRepository.getProjectById(id2))
    }

    @Test
    fun testChangelogAndVersionTracking() = runBlocking {
        val releases = changelogRepository.getAllReleases()
        assertTrue("Releases must not be empty", releases.isNotEmpty())
        assertTrue("Releases must be reverse chronological", releases.first().versionCode >= releases.last().versionCode)

        val currentRelease = changelogRepository.getReleaseByVersionCode(BuildConfig.VERSION_CODE)
        assertNotNull("Current BuildConfig.VERSION_CODE must have a matching release", currentRelease)
        assertEquals(BuildConfig.VERSION_NAME, currentRelease?.versionName)

        val initialViewed = userPreferencesRepository.lastViewedVersionCodeFlow.first()
        assertEquals(0, initialViewed)

        userPreferencesRepository.setLastViewedVersionCode(BuildConfig.VERSION_CODE)
        val afterDismiss = userPreferencesRepository.lastViewedVersionCodeFlow.first()
        assertEquals(BuildConfig.VERSION_CODE, afterDismiss)
    }
}
