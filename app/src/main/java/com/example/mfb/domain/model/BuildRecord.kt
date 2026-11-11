package com.example.mfb.domain.model

enum class BuildType {
    APK,
    AAB
}

enum class BuildStatus {
    SUCCESS,
    FAILED,
    IN_PROGRESS,
    QUEUED
}

data class BuildRecord(
    val id: Long = 0,
    val projectId: Long,
    val projectName: String,
    val buildType: BuildType,
    val status: BuildStatus,
    val outputArtifactName: String = "",
    val outputArtifactPath: String = "",
    val fileSizeBytes: Long = 0,
    val durationMillis: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val buildProvider: String = "REMOTE",
    val packageName: String = "",
    val buildLogs: String = "",
    val failureReason: String = "",
    val remoteJobId: String = "",
    val runtimeInfo: String = ""
)
