package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mfb.core.model.ThemeMode
import com.example.mfb.data.build.ApkBuildVariant
import com.example.mfb.data.build.AssembledWorkspace
import com.example.mfb.data.build.BuildCapabilityDetector
import com.example.mfb.data.build.BuildEngineManager
import com.example.mfb.data.build.BuildLogManager
import com.example.mfb.data.build.BuildOutputValidator
import com.example.mfb.data.build.BuildProgressManager
import com.example.mfb.data.build.EnvironmentInspector
import com.example.mfb.data.build.LocalBuildProvider
import com.example.mfb.data.build.LocalExecutionBackend
import com.example.mfb.data.build.LocalToolchainProbe
import com.example.mfb.data.build.LocalToolchainProbeResult
import com.example.mfb.data.build.ProjectAssembler
import com.example.mfb.data.build.ProjectBuildAnalyzer
import com.example.mfb.data.build.RemoteBuildProvider
import com.example.mfb.data.engine.ProjectHealthChecker
import com.example.mfb.data.engine.ZipImportEngine
import com.example.mfb.data.engine.ZipImportResult
import com.example.mfb.data.local.room.MfbDatabase
import com.example.mfb.data.repository.ProjectRepositoryImpl
import com.example.mfb.domain.model.BuildConfiguration
import com.example.mfb.domain.model.BuildExecutionOutcome
import com.example.mfb.domain.model.BuildFailureCategory
import com.example.mfb.domain.model.BuildProviderType
import com.example.mfb.domain.model.BuildStage
import com.example.mfb.domain.model.BuildSupportLevel
import com.example.mfb.domain.model.ComponentStatus
import com.example.mfb.domain.model.DeviceEnvironmentInfo
import com.example.mfb.domain.model.LocalRuntimeState
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.ProjectCategory
import com.example.mfb.domain.model.ProjectHealthStatus
import com.example.mfb.data.local.datastore.UserPreferencesRepository
import com.example.mfb.data.repository.BuildRepositoryImpl
import com.example.mfb.presentation.build.AssemblerViewModel
import com.example.mfb.presentation.build.BuildPipelineScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h1200dp")
class MfbUniversalBuildRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: MfbDatabase
    private lateinit var projectRepository: ProjectRepositoryImpl
    private lateinit var buildRepository: BuildRepositoryImpl
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var tempTestDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MfbDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectRepository = ProjectRepositoryImpl(database.projectDao())
        buildRepository = BuildRepositoryImpl(database.buildDao())
        preferencesRepository = UserPreferencesRepository(context)
        tempTestDir = File(context.cacheDir, "universal_test_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        tempTestDir.deleteRecursively()
        File(context.filesDir, "projects").deleteRecursively()
        File(context.filesDir, "builds").deleteRecursively()
        File(context.cacheDir, "build_workspaces").deleteRecursively()
    }

    private fun createZipArchive(fileName: String, entries: Map<String, String>): File {
        val zipFile = File(tempTestDir, fileName)
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return zipFile
    }

    private fun createSignedFakeApk(fileName: String): File {
        val nonCompressibleDex = ByteArray(2048).also { Random(42).nextBytes(it) }
        val apk = File(tempTestDir, fileName)
        ZipOutputStream(FileOutputStream(apk)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write("binary-axml-placeholder".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(nonCompressibleDex)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("META-INF/CERT.SF"))
            zos.write("Signature-Version: 1.0\n".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
            zos.write("RSA-CERT-BYTES".toByteArray())
            zos.closeEntry()
        }
        return apk
    }

    private fun fakeDevice(
        isArm64: Boolean = true,
        availableRamMb: Long = 2048L,
        availableStorageMb: Long = 4096L,
        isThermalThrottled: Boolean = false
    ) = DeviceEnvironmentInfo(
        androidVersion = "13",
        sdkInt = 33,
        cpuAbis = if (isArm64) listOf("arm64-v8a") else listOf("armeabi-v7a"),
        primaryAbi = if (isArm64) "arm64-v8a" else "armeabi-v7a",
        isArm64 = isArm64,
        totalRamMb = 6144L,
        availableRamMb = availableRamMb,
        availableStorageMb = availableStorageMb,
        batteryPercent = 85,
        isPowerSaveMode = false,
        thermalStatusLabel = if (isThermalThrottled) "Severe" else "Normal",
        isThermalThrottled = isThermalThrottled,
        termuxInstalled = false,
        termuxRunCommandPermitted = false
    )

    @Test
    fun testNativeAndroidKotlinProjectImportsWithoutIndexHtml() = runBlocking {
        val entries = mapOf(
            "settings.gradle.kts" to """
                rootProject.name = "KotlinApp"
                include(":mobile")
            """.trimIndent(),
            "gradle/wrapper/gradle-wrapper.properties" to "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.7-bin.zip",
            "build.gradle.kts" to """plugins { id("com.android.application") version "8.5.2" apply false }""",
            "mobile/build.gradle.kts" to """
                plugins { id("com.android.application") }
                android {
                    namespace = "com.example.kotlinapp"
                    compileSdk = 35
                    defaultConfig {
                        applicationId = "com.example.kotlinapp"
                        minSdk = 24
                        targetSdk = 35
                        versionCode = 3
                        versionName = "1.2.0"
                    }
                }
            """.trimIndent(),
            "mobile/src/main/AndroidManifest.xml" to """<manifest package="com.example.kotlinapp"><application /></manifest>""",
            "mobile/src/main/java/com/example/kotlinapp/MainActivity.kt" to "package com.example.kotlinapp\nclass MainActivity"
        )
        val zipFile = createZipArchive("native_kotlin.zip", entries)
        val result = ZipImportEngine.importFromUri(context, Uri.fromFile(zipFile))
        assertTrue("Native Kotlin Android ZIP must succeed without index.html", result is ZipImportResult.Success)

        val project = (result as ZipImportResult.Success).project
        assertEquals(ProjectCategory.ANDROID_GRADLE_KOTLIN, project.projectCategory)
        assertEquals("com.example.kotlinapp", project.packageId)
        assertEquals("1.2.0", project.versionName)
        assertEquals(3, project.versionCode)
        assertFalse(project.projectCategory.supportsLivePreview)

        val profile = ProjectBuildAnalyzer.analyze(project)
        assertEquals("mobile", profile.applicationModule)
        assertEquals("8.7", profile.gradleVersion)
        assertEquals("8.5.2", profile.agpVersion)
        assertEquals(35, profile.compileSdk)

        val id = projectRepository.insertProject(project)
        val fromDb = projectRepository.getProjectById(id)
        assertNotNull(fromDb)
        assertEquals(ProjectHealthStatus.HEALTHY, ProjectHealthChecker.checkProjectHealth(context, fromDb!!).status)
    }

    @Test
    fun testNativeAndroidJavaReactViteAndCapacitorImport() = runBlocking {
        val javaZip = createZipArchive(
            "java.zip",
            mapOf(
                "settings.gradle" to "include ':app'",
                "app/build.gradle" to "android { defaultConfig { applicationId 'com.example.javaapp' } }",
                "app/src/main/AndroidManifest.xml" to "<manifest package=\"com.example.javaapp\"/>",
                "app/src/main/java/MainActivity.java" to "public class MainActivity {}"
            )
        )
        assertEquals(
            ProjectCategory.ANDROID_GRADLE_JAVA,
            (ZipImportEngine.importFromUri(context, Uri.fromFile(javaZip)) as ZipImportResult.Success).project.projectCategory
        )

        val reactViteZip = createZipArchive(
            "rv.zip",
            mapOf(
                "package.json" to """{"name":"rv","dependencies":{"react":"^18.0.0"},"devDependencies":{"vite":"^5.0.0"}}""",
                "vite.config.ts" to "export default {}"
            )
        )
        assertEquals(
            ProjectCategory.REACT_VITE,
            (ZipImportEngine.importFromUri(context, Uri.fromFile(reactViteZip)) as ZipImportResult.Success).project.projectCategory
        )

        val capZip = createZipArchive(
            "cap.zip",
            mapOf(
                "package.json" to """{"name":"cap","dependencies":{"@capacitor/core":"^6.0.0"}}""",
                "capacitor.config.json" to """{"appId":"com.example.cap"}""",
                "dist/index.html" to "<html><body>Cap</body></html>"
            )
        )
        val capRes = ZipImportEngine.importFromUri(context, Uri.fromFile(capZip)) as ZipImportResult.Success
        assertEquals(ProjectCategory.CAPACITOR, capRes.project.projectCategory)
        assertEquals("dist/index.html", capRes.project.mainHtmlRelativePath)
    }

    @Test
    fun testBuildCapabilityDetectorDetectsMissingComponentsAndResourceLimits() {
        val envInspector = object : EnvironmentInspector {
            override fun inspect(context: Context) = fakeDevice(isArm64 = false, availableStorageMb = 100L, isThermalThrottled = true)
        }
        val probe = object : LocalToolchainProbe {
            override fun probe(context: Context, device: DeviceEnvironmentInfo) = LocalToolchainProbeResult(
                jdkStatus = ComponentStatus.MISSING,
                gradleStatus = ComponentStatus.MISSING,
                androidSdkStatus = ComponentStatus.MISSING,
                buildToolsStatus = ComponentStatus.INCOMPATIBLE
            )
        }
        val detector = BuildCapabilityDetector(envInspector, probe)
        val report = detector.inspectLocalCompatibility(context)

        assertEquals(BuildSupportLevel.UNSUPPORTED, report.supportLevel)
        assertEquals(ComponentStatus.MISSING, report.jdkStatus)
        assertEquals(ComponentStatus.MISSING, report.gradleStatus)
        assertEquals(ComponentStatus.MISSING, report.androidSdkStatus)
        assertEquals(ComponentStatus.INCOMPATIBLE, report.buildToolsStatus)
        assertTrue(report.blockingReasons.any { it.contains("Insufficient free storage") })
        assertTrue(report.warnings.any { it.contains("thermal", ignoreCase = true) })
    }

    @Test
    fun testBuildOutputValidatorRejectsUnsignedAndRenamedZipsAndAcceptsSignedApk() {
        // 1. Renamed ZIP rejected
        val fakeApk = createZipArchive("renamed.apk", mapOf("index.html" to "<html></html>", "pad.txt" to "x".repeat(2048)))
        assertFalse(BuildOutputValidator.validateApk(fakeApk).base.isValid)

        // 2. Unsigned APK (has manifest + dex, no signature) rejected
        val unsignedApk = File(tempTestDir, "unsigned.apk")
        val dex = ByteArray(2048).also { Random(7).nextBytes(it) }
        ZipOutputStream(FileOutputStream(unsignedApk)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write("manifest".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(dex)
            zos.closeEntry()
        }
        val unsignedRes = BuildOutputValidator.validateApk(unsignedApk)
        assertFalse(unsignedRes.base.isValid)
        assertFalse(unsignedRes.isSigned)

        // 3. Signed APK passes
        val signedApk = createSignedFakeApk("signed.apk")
        val signedRes = BuildOutputValidator.validateApk(signedApk)
        assertTrue("Signed APK should pass: ${signedRes.base.reason}", signedRes.base.isValid)
        assertTrue(signedRes.isSigned)
    }

    @Test
    fun testRemoteBuildProviderUrlValidationAndHttpOutcomes() = runBlocking {
        // HTTPS enforcement
        assertTrue(RemoteBuildProvider.validateServerUrl("https://build.mfb.dev").isSuccess)
        assertTrue(RemoteBuildProvider.validateServerUrl("http://localhost:8080").isSuccess)
        assertTrue(RemoteBuildProvider.validateServerUrl("http://build.public.com").isFailure)
        assertTrue(RemoteBuildProvider.validateServerUrl("").isFailure)

        val webZip = createZipArchive("remote_test.zip", mapOf("index.html" to "<html><body>Hi</body></html>"))
        val project = (ZipImportEngine.importFromUri(context, Uri.fromFile(webZip)) as ZipImportResult.Success).project
        val workspace = ProjectAssembler.assembleWorkspace(context, project).getOrThrow()

        // 401 Auth failure
        val authFailClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("Unauthorized".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()
        val remoteAuthFail = RemoteBuildProvider(
            serverUrlProvider = { "https://build.mfb.dev" },
            httpClient = authFailClient
        )
        val authRes = remoteAuthFail.executeBuild(context, project, workspace, BuildConfiguration(project.id))
        assertTrue(authRes is BuildExecutionOutcome.Failure)
        assertEquals(BuildFailureCategory.INFRASTRUCTURE_FAILURE, (authRes as BuildExecutionOutcome.Failure).category)

        // Successful APK download + validation
        val signedApkBytes = createSignedFakeApk("remote_out.apk").readBytes()
        val successClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(signedApkBytes.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                    .build()
            }
            .build()
        val remoteSuccess = RemoteBuildProvider(
            serverUrlProvider = { "https://build.mfb.dev" },
            httpClient = successClient
        )
        val okRes = remoteSuccess.executeBuild(context, project, workspace, BuildConfiguration(project.id))
        assertTrue("Expected Success, got $okRes", okRes is BuildExecutionOutcome.Success)
    }

    @Test
    fun testHybridRoutingLocalFirstAndLogPreservationAcrossProviders() = runBlocking {
        val webZip = createZipArchive("hybrid.zip", mapOf("index.html" to "<html><body>Hybrid</body></html>"))
        val project = (ZipImportEngine.importFromUri(context, Uri.fromFile(webZip)) as ZipImportResult.Success).project

        val onlineClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val logManager = BuildLogManager()
        val progressManager = BuildProgressManager()

        // Case 1: Local available -> recommends LOCAL
        val availableDetector = BuildCapabilityDetector(
            environmentInspector = object : EnvironmentInspector {
                override fun inspect(context: Context) = fakeDevice()
            },
            toolchainProbe = object : LocalToolchainProbe {
                override fun probe(context: Context, device: DeviceEnvironmentInfo) = LocalToolchainProbeResult(
                    jdkStatus = ComponentStatus.AVAILABLE,
                    gradleStatus = ComponentStatus.AVAILABLE,
                    androidSdkStatus = ComponentStatus.AVAILABLE,
                    buildToolsStatus = ComponentStatus.AVAILABLE,
                    verificationTestPassed = true
                )
            }
        )
        val localReadyProvider = LocalBuildProvider(
            capabilityDetector = availableDetector,
            executionBackend = object : LocalExecutionBackend {
                override suspend fun runGradleBuild(
                    context: Context,
                    project: Project,
                    workspace: AssembledWorkspace,
                    config: BuildConfiguration,
                    onStage: (BuildStage) -> Unit,
                    onLog: (String) -> Unit
                ): Result<File> {
                    onStage(BuildStage.COMPILATION)
                    onLog("Kotlin compilation error in MainActivity.kt")
                    return Result.failure(IllegalStateException("Kotlin compilation error: Unresolved reference"))
                }
                override fun cancel() = Unit
            },
            progressManager = progressManager,
            logManager = logManager
        )
        val remoteOnlineProvider = RemoteBuildProvider(
            serverUrlProvider = { "https://build.mfb.dev" },
            httpClient = onlineClient,
            progressManager = progressManager,
            logManager = logManager
        )

        val manager1 = BuildEngineManager(localReadyProvider, remoteOnlineProvider, availableDetector, progressManager, logManager)
        val decision1 = manager1.evaluateRouting(context, project)
        assertEquals(BuildProviderType.LOCAL, decision1.recommendedProvider)

        // Local compilation error is classified as PROJECT_COMPILATION_ERROR (not runtime limitation)
        val compErr = manager1.executeWithProvider(context, project, BuildConfiguration(project.id), BuildProviderType.LOCAL)
        assertTrue(compErr is BuildExecutionOutcome.Failure)
        assertEquals(BuildFailureCategory.PROJECT_COMPILATION_ERROR, (compErr as BuildExecutionOutcome.Failure).category)
        assertFalse(compErr.offerRemoteFallback)

        // Case 2: Local unavailable + Remote online -> recommends REMOTE & preserves logs
        val missingDetector = BuildCapabilityDetector(
            environmentInspector = object : EnvironmentInspector {
                override fun inspect(context: Context) = fakeDevice()
            },
            toolchainProbe = object : LocalToolchainProbe {
                override fun probe(context: Context, device: DeviceEnvironmentInfo) = LocalToolchainProbeResult(
                    jdkStatus = ComponentStatus.MISSING,
                    gradleStatus = ComponentStatus.MISSING,
                    androidSdkStatus = ComponentStatus.MISSING,
                    buildToolsStatus = ComponentStatus.MISSING
                )
            }
        )
        val localMissingProvider = LocalBuildProvider(missingDetector, progressManager = progressManager, logManager = logManager)
        val manager2 = BuildEngineManager(localMissingProvider, remoteOnlineProvider, missingDetector, progressManager, logManager)
        val decision2 = manager2.evaluateRouting(context, project)
        assertEquals(BuildProviderType.REMOTE, decision2.recommendedProvider)

        // Case 3: Both unavailable -> recommends null
        val remoteOffline = RemoteBuildProvider(serverUrlProvider = { "" }, progressManager = progressManager, logManager = logManager)
        val manager3 = BuildEngineManager(localMissingProvider, remoteOffline, missingDetector, progressManager, logManager)
        val decision3 = manager3.evaluateRouting(context, project)
        assertNull(decision3.recommendedProvider)

        // Switching providers with preserveExistingLogs = true retains prior logs
        val secondAttempt = manager3.executeWithProvider(
            context, project, BuildConfiguration(project.id), BuildProviderType.REMOTE, preserveExistingLogs = true
        )
        assertTrue((secondAttempt as BuildExecutionOutcome.Failure).buildLog.contains("Kotlin compilation error"))
    }

    @Test
    fun testVersionCatalogAgpAndCompileSdkReleaseParsing() = runBlocking {
        val entries = mapOf(
            "gradle/libs.versions.toml" to """
                [versions]
                agp = "8.9.1"
                [plugins]
                android-application = { id = "com.android.application", version.ref = "agp" }
            """.trimIndent(),
            "gradle/wrapper/gradle-wrapper.properties" to "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.11.1-bin.zip",
            "settings.gradle.kts" to "rootProject.name = \"CatalogApp\"\ninclude(\":app\")",
            "build.gradle.kts" to "plugins { alias(libs.plugins.android.application) apply false }",
            "app/build.gradle.kts" to """
                plugins { alias(libs.plugins.android.application) }
                android {
                    namespace = "com.example.catalog"
                    compileSdk { version = release(36) { minorApiLevel = 1 } }
                    defaultConfig { applicationId = "com.example.catalog"; minSdk = 24; targetSdk = 36 }
                    compileOptions { sourceCompatibility = JavaVersion.VERSION_17 }
                }
            """.trimIndent(),
            "app/src/main/AndroidManifest.xml" to "<manifest/>",
            "app/src/main/java/com/example/catalog/MainActivity.kt" to "class MainActivity"
        )
        val zipFile = createZipArchive("catalog_app.zip", entries)
        val project = (ZipImportEngine.importFromUri(context, Uri.fromFile(zipFile)) as ZipImportResult.Success).project
        val profile = ProjectBuildAnalyzer.analyze(project)

        assertEquals("8.9.1", profile.agpVersion)
        assertEquals("8.11.1", profile.gradleVersion)
        assertEquals("17", profile.requiredJdkVersion)
        assertEquals(36, profile.compileSdk)
        assertEquals("app", profile.applicationModule)
    }

    @Test
    fun testLocalRuntimeStatesAndDisabledBuildButtonInUi(): Unit = runBlocking {
        val incompleteProbe = object : LocalToolchainProbe {
            override fun probe(context: Context, device: DeviceEnvironmentInfo) = LocalToolchainProbeResult(
                jdkStatus = ComponentStatus.AVAILABLE,
                gradleStatus = ComponentStatus.MISSING,
                androidSdkStatus = ComponentStatus.MISSING,
                buildToolsStatus = ComponentStatus.MISSING
            )
        }
        val envInspector = object : EnvironmentInspector {
            override fun inspect(context: Context) = fakeDevice()
        }
        assertEquals(
            LocalRuntimeState.LOCAL_RUNTIME_INCOMPLETE,
            BuildCapabilityDetector(envInspector, incompleteProbe).inspectLocalCompatibility(context).runtimeState
        )

        val unverifiedProbe = object : LocalToolchainProbe {
            override fun probe(context: Context, device: DeviceEnvironmentInfo) = LocalToolchainProbeResult(
                jdkStatus = ComponentStatus.AVAILABLE,
                gradleStatus = ComponentStatus.AVAILABLE,
                androidSdkStatus = ComponentStatus.AVAILABLE,
                buildToolsStatus = ComponentStatus.AVAILABLE,
                verificationTestPassed = false
            )
        }
        assertEquals(
            LocalRuntimeState.LOCAL_BUILD_TEST_FAILED,
            BuildCapabilityDetector(envInspector, unverifiedProbe).inspectLocalCompatibility(context).runtimeState
        )

        // UI Test: When local runtime is missing, start_apk_build_button and provider_local_chip MUST be disabled
        val webZip = createZipArchive("ui_test.zip", mapOf("index.html" to "<html><body>UI</body></html>"))
        val project = (ZipImportEngine.importFromUri(context, Uri.fromFile(webZip)) as ZipImportResult.Success).project
        val missingProbe = object : LocalToolchainProbe {
            override fun probe(context: Context, device: DeviceEnvironmentInfo) = LocalToolchainProbeResult(
                jdkStatus = ComponentStatus.MISSING,
                gradleStatus = ComponentStatus.MISSING,
                androidSdkStatus = ComponentStatus.MISSING,
                buildToolsStatus = ComponentStatus.MISSING
            )
        }
        val missingDetector = BuildCapabilityDetector(envInspector, missingProbe)
        val localMissing = LocalBuildProvider(missingDetector)
        val remoteMissing = RemoteBuildProvider(serverUrlProvider = { "" })
        val routing = BuildEngineManager(localMissing, remoteMissing, missingDetector)
            .evaluateRouting(context, project)

        composeRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                com.example.mfb.presentation.build.BuildPipelineContent(
                    project = project,
                    analysis = null,
                    routing = routing,
                    selectedProvider = BuildProviderType.LOCAL,
                    buildState = com.example.mfb.presentation.build.BuildPipelineUiState.Idle,
                    selectedVariant = ApkBuildVariant.DEBUG,
                    currentStage = null,
                    liveLogs = emptyList(),
                    projectBuilds = emptyList(),
                    actionMessage = null,
                    onClearActionMessage = {},
                    onSelectProvider = {},
                    onSelectVariant = {},
                    onStartBuild = { _, _ -> },
                    onCancelBuild = {},
                    onInstallApk = {},
                    onShareApk = {},
                    onExportFile = { _, _, _ -> },
                    onPrepareWorkspaceZip = {},
                    onNavigateBack = {},
                    onNavigateToSettings = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("local_runtime_state_badge").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("LOCAL BUILD UNSUPPORTED").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("local_runtime_setup_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("provider_local_chip").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("start_apk_build_button").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun testLocalBuildBlockedWhenUnsupportedAndSucceedsWhenVerified() = runBlocking {
        val webZip = createZipArchive("local_exec.zip", mapOf("index.html" to "<html><body>Local</body></html>"))
        val project = (ZipImportEngine.importFromUri(context, Uri.fromFile(webZip)) as ZipImportResult.Success).project
        val workspace = ProjectAssembler.assembleWorkspace(context, project).getOrThrow()

        // 1. Missing runtime: LocalBuildProvider MUST block execution before invoking backend
        var backendInvokedWhenUnsupported = false
        val unsupportedDetector = BuildCapabilityDetector(
            environmentInspector = object : EnvironmentInspector {
                override fun inspect(context: Context) = fakeDevice()
            },
            toolchainProbe = object : LocalToolchainProbe {
                override fun probe(context: Context, device: DeviceEnvironmentInfo) = LocalToolchainProbeResult(
                    jdkStatus = ComponentStatus.MISSING,
                    gradleStatus = ComponentStatus.MISSING,
                    androidSdkStatus = ComponentStatus.MISSING,
                    buildToolsStatus = ComponentStatus.MISSING
                )
            }
        )
        val blockedProvider = LocalBuildProvider(
            capabilityDetector = unsupportedDetector,
            executionBackend = object : LocalExecutionBackend {
                override suspend fun runGradleBuild(
                    context: Context,
                    project: Project,
                    workspace: AssembledWorkspace,
                    config: BuildConfiguration,
                    onStage: (BuildStage) -> Unit,
                    onLog: (String) -> Unit
                ): Result<File> {
                    backendInvokedWhenUnsupported = true
                    return Result.failure(IllegalStateException("Should not run"))
                }
                override fun cancel() = Unit
            }
        )
        val blockedResult = blockedProvider.executeBuild(context, project, workspace, BuildConfiguration(project.id))
        assertFalse("Execution backend must NOT be invoked when local runtime is unsupported", backendInvokedWhenUnsupported)
        assertTrue(blockedResult is BuildExecutionOutcome.Failure)
        assertEquals(BuildFailureCategory.UNSUPPORTED_RUNTIME, (blockedResult as BuildExecutionOutcome.Failure).category)

        // 2. Verified runtime: LocalBuildProvider executes backend and validates signed APK
        val verifiedDetector = BuildCapabilityDetector(
            environmentInspector = object : EnvironmentInspector {
                override fun inspect(context: Context) = fakeDevice()
            },
            toolchainProbe = object : LocalToolchainProbe {
                override fun probe(context: Context, device: DeviceEnvironmentInfo) = LocalToolchainProbeResult(
                    jdkStatus = ComponentStatus.AVAILABLE,
                    gradleStatus = ComponentStatus.AVAILABLE,
                    androidSdkStatus = ComponentStatus.AVAILABLE,
                    buildToolsStatus = ComponentStatus.AVAILABLE,
                    verificationTestPassed = true
                )
            }
        )
        assertEquals(LocalRuntimeState.LOCAL_RUNTIME_READY, verifiedDetector.inspectLocalCompatibility(context).runtimeState)

        val producedApk = createSignedFakeApk("local_verified_out.apk")
        val verifiedProvider = LocalBuildProvider(
            capabilityDetector = verifiedDetector,
            executionBackend = object : LocalExecutionBackend {
                override suspend fun runGradleBuild(
                    context: Context,
                    project: Project,
                    workspace: AssembledWorkspace,
                    config: BuildConfiguration,
                    onStage: (BuildStage) -> Unit,
                    onLog: (String) -> Unit
                ): Result<File> {
                    onStage(BuildStage.APK_PACKAGING)
                    onLog("BUILD SUCCESSFUL")
                    return Result.success(producedApk)
                }
                override fun cancel() = Unit
            }
        )
        val successResult = verifiedProvider.executeBuild(context, project, workspace, BuildConfiguration(project.id))
        assertTrue("Verified local build should succeed, got $successResult", successResult is BuildExecutionOutcome.Success)
        val apkFile = (successResult as BuildExecutionOutcome.Success).apkFile
        assertTrue(BuildOutputValidator.validateApk(apkFile).base.isValid)
    }
}
