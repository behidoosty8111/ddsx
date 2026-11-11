package com.example.mfb.domain.model

import com.example.mfb.data.build.ApkBuildVariant
import com.example.mfb.data.build.ApkValidationResult
import java.io.File

enum class BuildProviderType(val displayName: String) {
    LOCAL("Local Gradle"),
    REMOTE("Remote Gradle")
}

enum class ComponentStatus(val label: String) {
    AVAILABLE("AVAILABLE"),
    MISSING("MISSING"),
    INCOMPATIBLE("INCOMPATIBLE"),
    UNVERIFIED("UNVERIFIED")
}

enum class BuildSupportLevel(val label: String) {
    SUPPORTED("SUPPORTED"),
    LIMITED("LIMITED"),
    UNSUPPORTED("UNSUPPORTED")
}

enum class LocalRuntimeState(val label: String) {
    LOCAL_RUNTIME_READY("LOCAL RUNTIME READY"),
    LOCAL_RUNTIME_INCOMPLETE("LOCAL RUNTIME INCOMPLETE"),
    LOCAL_BUILD_UNSUPPORTED("LOCAL BUILD UNSUPPORTED"),
    LOCAL_BUILD_TEST_FAILED("LOCAL BUILD TEST FAILED")
}

enum class BuildStage(val stepNumber: Int, val label: String) {
    PROJECT_ANALYSIS(1, "Project Analysis"),
    ENVIRONMENT_CHECK(2, "Environment Check"),
    DEPENDENCY_RESOLUTION(3, "Dependency Resolution"),
    GRADLE_CONFIGURATION(4, "Gradle Configuration"),
    COMPILATION(5, "Compilation"),
    RESOURCE_PROCESSING(6, "Resource Processing"),
    APK_PACKAGING(7, "APK Packaging"),
    APK_VALIDATION(8, "APK Validation"),
    BUILD_COMPLETE(9, "Build Complete")
}

enum class BuildFailureCategory(val label: String, val isRuntimeLimitation: Boolean) {
    UNSUPPORTED_PROJECT("Unsupported Project", false),
    UNSUPPORTED_RUNTIME("Unsupported Runtime", true),
    MISSING_DEPENDENCIES("Missing Dependencies", false),
    PROJECT_COMPILATION_ERROR("Project Compilation Error", false),
    OUT_OF_MEMORY("Out of Memory", true),
    INSUFFICIENT_STORAGE("Insufficient Storage", true),
    BUILD_FAILURE("Build Failure", false),
    CANCELLED("Build Cancelled", false),
    TIMEOUT("Build Timeout", true),
    INFRASTRUCTURE_FAILURE("Infrastructure Failure", true)
}

data class DeviceEnvironmentInfo(
    val androidVersion: String,
    val sdkInt: Int,
    val cpuAbis: List<String>,
    val primaryAbi: String,
    val isArm64: Boolean,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val availableStorageMb: Long,
    val batteryPercent: Int,
    val isPowerSaveMode: Boolean,
    val thermalStatusLabel: String,
    val isThermalThrottled: Boolean,
    val termuxInstalled: Boolean,
    val termuxRunCommandPermitted: Boolean
)

data class ProjectBuildProfile(
    val category: ProjectCategory,
    val gradleVersion: String?,
    val agpVersion: String?,
    val requiredJdkVersion: String,
    val compileSdk: Int?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val discoveredModules: List<String>,
    val applicationModule: String,
    val availableVariants: List<ApkBuildVariant>,
    val supportsAab: Boolean,
    val hasReleaseSigningConfig: Boolean,
    val dependencyCountEstimate: Int,
    val estimatedComplexity: String,
    val compatibilityIssues: List<String>
)

data class LocalBuildCompatibilityReport(
    val device: DeviceEnvironmentInfo,
    val jdkStatus: ComponentStatus,
    val gradleStatus: ComponentStatus,
    val androidSdkStatus: ComponentStatus,
    val buildToolsStatus: ComponentStatus,
    val supportLevel: BuildSupportLevel,
    val runtimeState: LocalRuntimeState = LocalRuntimeState.LOCAL_BUILD_UNSUPPORTED,
    val blockingReasons: List<String>,
    val warnings: List<String>
)

data class BuildProviderAvailability(
    val providerType: BuildProviderType,
    val isAvailable: Boolean,
    val statusSummary: String,
    val detailReasons: List<String> = emptyList(),
    val serverConnectivityVerified: Boolean = false,
    val apiCompatible: Boolean = false
)

data class BuildConfiguration(
    val projectId: Long,
    val variant: ApkBuildVariant = ApkBuildVariant.DEBUG,
    val buildType: BuildType = BuildType.APK,
    val applicationModule: String = "app",
    val preferredProvider: BuildProviderType? = null,
    val userConfirmedUntrustedExecution: Boolean = false
)

sealed class BuildExecutionOutcome {
    data class Success(
        val provider: BuildProviderType,
        val apkFile: File,
        val validation: ApkValidationResult,
        val buildLog: String,
        val durationMillis: Long,
        val remoteJobId: String = "",
        val runtimeInfo: String = ""
    ) : BuildExecutionOutcome()

    data class Failure(
        val provider: BuildProviderType,
        val category: BuildFailureCategory,
        val message: String,
        val buildLog: String,
        val durationMillis: Long,
        val offerRemoteFallback: Boolean = false,
        val preparedWorkspaceZip: File? = null,
        val remoteJobId: String = ""
    ) : BuildExecutionOutcome()
}
