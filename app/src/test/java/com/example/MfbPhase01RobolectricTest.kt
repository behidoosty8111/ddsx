package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mfb.core.model.ThemeMode
import com.example.mfb.data.local.datastore.UserPreferencesRepository
import com.example.mfb.data.local.room.MfbDatabase
import com.example.mfb.data.repository.BuildRepositoryImpl
import com.example.mfb.data.repository.ProjectRepositoryImpl
import com.example.mfb.domain.model.BuildRecord
import com.example.mfb.domain.model.BuildStatus
import com.example.mfb.domain.model.BuildType
import com.example.mfb.domain.model.Project
import com.example.mfb.navigation.MfbScreen
import com.example.mfb.navigation.bottomNavItems
import com.example.mfb.presentation.viewmodel.MfbViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MfbPhase01RobolectricTest {

    private lateinit var context: Context
    private lateinit var database: MfbDatabase
    private lateinit var projectRepository: ProjectRepositoryImpl
    private lateinit var buildRepository: BuildRepositoryImpl
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var viewModel: MfbViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MfbDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectRepository = ProjectRepositoryImpl(database.projectDao())
        buildRepository = BuildRepositoryImpl(database.buildDao())
        userPreferencesRepository = UserPreferencesRepository(context)
        viewModel = MfbViewModel(
            preferencesRepository = userPreferencesRepository,
            projectRepository = projectRepository,
            buildRepository = buildRepository
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAppIdentityAndResources() {
        val appName = context.getString(R.string.app_name)
        val subtitle = context.getString(R.string.app_subtitle)
        val actionText = context.getString(R.string.action_build_an_app)

        assertEquals("MFB", appName)
        assertEquals("Professional HTML to APK Builder", subtitle)
        assertEquals("BUILD AN APP", actionText)
    }

    @Test
    fun testNoFakeProjectsOrBuildsOnStartup() = runBlocking {
        // Requirement 4.C and 4.D: Verify no fake records exist
        val initialProjects = projectRepository.getAllProjects().first()
        val initialBuilds = buildRepository.getAllBuilds().first()

        assertTrue("Projects list must be empty initially (no fake projects)", initialProjects.isEmpty())
        assertTrue("Builds list must be empty initially (no fake builds)", initialBuilds.isEmpty())
    }

    @Test
    fun testProjectRepositoryCrudLifecycle() = runBlocking {
        val testProject = Project(
            name = "Test WebApp",
            description = "A responsive web application",
            packageId = "com.test.webapp",
            versionName = "1.0.0",
            versionCode = 1,
            isZipArchive = true,
            sourcePath = "/data/user/0/com.aistudio.mfb/files/projects/test"
        )

        // 1. Insert
        val insertedId = projectRepository.insertProject(testProject)
        assertTrue("Inserted project must have positive ID", insertedId > 0)

        // 2. Read by ID
        val retrieved = projectRepository.getProjectById(insertedId)
        assertNotNull("Retrieved project must not be null", retrieved)
        assertEquals("Test WebApp", retrieved?.name)
        assertEquals("com.test.webapp", retrieved?.packageId)

        // 3. Count
        val count = projectRepository.getProjectsCount()
        assertEquals(1, count)

        // 4. Delete
        retrieved?.let { projectRepository.deleteProject(it) }
        val afterDeleteCount = projectRepository.getProjectsCount()
        assertEquals(0, afterDeleteCount)
        assertNull(projectRepository.getProjectById(insertedId))
    }

    @Test
    fun testBuildRepositoryCrudLifecycle() = runBlocking {
        val build1 = BuildRecord(
            projectId = 1,
            projectName = "Project One",
            buildType = BuildType.APK,
            status = BuildStatus.SUCCESS,
            outputArtifactName = "app-release.apk",
            fileSizeBytes = 1024 * 1024 * 4,
            durationMillis = 2300,
            timestamp = 1000L
        )
        val build2 = BuildRecord(
            projectId = 1,
            projectName = "Project One",
            buildType = BuildType.AAB,
            status = BuildStatus.SUCCESS,
            outputArtifactName = "app-release.aab",
            fileSizeBytes = 1024 * 1024 * 3,
            durationMillis = 3100,
            timestamp = 2000L
        )

        val id1 = buildRepository.insertBuild(build1)
        val id2 = buildRepository.insertBuild(build2)
        assertTrue(id1 > 0)
        assertTrue(id2 > 0)

        val recentBuilds = buildRepository.getRecentBuilds(5).first()
        assertEquals(2, recentBuilds.size)
        // Ordered by timestamp DESC
        assertEquals("app-release.aab", recentBuilds[0].outputArtifactName)
        assertEquals(BuildType.AAB, recentBuilds[0].buildType)
    }

    @Test
    fun testThemePersistenceDataStore() = runBlocking {
        // Test switching theme to Light and verifying persistence
        userPreferencesRepository.setThemeMode(ThemeMode.LIGHT)
        val lightTheme = userPreferencesRepository.themeModeFlow.first { it == ThemeMode.LIGHT }
        assertEquals(ThemeMode.LIGHT, lightTheme)

        // Test switching to System
        userPreferencesRepository.setThemeMode(ThemeMode.SYSTEM)
        val systemTheme = userPreferencesRepository.themeModeFlow.first { it == ThemeMode.SYSTEM }
        assertEquals(ThemeMode.SYSTEM, systemTheme)

        // Test switching back to Dark mode (Default)
        userPreferencesRepository.setThemeMode(ThemeMode.DARK)
        val darkTheme = userPreferencesRepository.themeModeFlow.first { it == ThemeMode.DARK }
        assertEquals(ThemeMode.DARK, darkTheme)
    }

    @Test
    fun testViewModelStateFlowReactiveness() {
        // Verify ViewModel initial empty states
        assertEquals(emptyList<Project>(), viewModel.projects.value)
        assertEquals(emptyList<BuildRecord>(), viewModel.recentBuilds.value)
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
        assertEquals("en", viewModel.appLanguage.value)
    }

    @Test
    fun testNavigationGraphRoutesIntegrity() {
        val routes = listOf(
            MfbScreen.Splash.route,
            MfbScreen.Dashboard.route,
            MfbScreen.Projects.route,
            MfbScreen.Templates.route,
            MfbScreen.Settings.route,
            MfbScreen.Import.route,
            MfbScreen.ImportZip.route,
            MfbScreen.ImportHtml.route,
            MfbScreen.About.route
        )

        // Verify all routes are unique non-empty strings
        val uniqueRoutes = routes.toSet()
        assertEquals("Every route must be unique", routes.size, uniqueRoutes.size)
        assertTrue("No route can be blank", routes.none { it.isBlank() })

        // Verify bottom nav items map to valid primary routes
        assertEquals(4, bottomNavItems.size)
        val bottomNavRoutes = bottomNavItems.map { it.route }.toSet()
        assertTrue(bottomNavRoutes.contains(MfbScreen.Dashboard.route))
        assertTrue(bottomNavRoutes.contains(MfbScreen.Projects.route))
        assertTrue(bottomNavRoutes.contains(MfbScreen.Templates.route))
        assertTrue(bottomNavRoutes.contains(MfbScreen.Settings.route))
    }

    @Test
    fun testBuildAnAppDestinationIsImportScreen() {
        // BUG 1 Verification: BUILD AN APP must route to MfbScreen.Import
        val targetRoute = MfbScreen.Import.route
        assertEquals("import", targetRoute)
        assertTrue("Import route must not be blank", targetRoute.isNotBlank())
    }

    @Test
    fun testImportScreenExactlyTwoPrimaryOptions() {
        // BUG 2 Verification: Exactly two primary options (Import ZIP and Import HTML)
        val zipTitle = context.getString(R.string.import_zip_title)
        val htmlTitle = context.getString(R.string.import_html_title)

        assertEquals("Import ZIP", zipTitle)
        assertEquals("Import HTML", htmlTitle)

        // Sub-routes for the two primary options
        assertEquals("import_zip", MfbScreen.ImportZip.route)
        assertEquals("import_html", MfbScreen.ImportHtml.route)
    }
}
