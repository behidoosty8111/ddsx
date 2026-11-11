package com.example.mfb.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.mfb.domain.model.BuildRecord
import com.example.mfb.domain.model.BuildStatus
import com.example.mfb.domain.model.BuildType

@Entity(tableName = "builds")
data class BuildEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val projectName: String,
    val buildType: String,
    val status: String,
    val outputArtifactName: String,
    val outputArtifactPath: String,
    val fileSizeBytes: Long,
    val durationMillis: Long,
    val timestamp: Long,
    val buildProvider: String = "REMOTE",
    val packageName: String = "",
    val buildLogs: String = "",
    val failureReason: String = "",
    val remoteJobId: String = "",
    val runtimeInfo: String = ""
) {
    fun toDomain(): BuildRecord = BuildRecord(
        id = id,
        projectId = projectId,
        projectName = projectName,
        buildType = runCatching { BuildType.valueOf(buildType) }.getOrDefault(BuildType.APK),
        status = runCatching { BuildStatus.valueOf(status) }.getOrDefault(BuildStatus.SUCCESS),
        outputArtifactName = outputArtifactName,
        outputArtifactPath = outputArtifactPath,
        fileSizeBytes = fileSizeBytes,
        durationMillis = durationMillis,
        timestamp = timestamp,
        buildProvider = buildProvider,
        packageName = packageName,
        buildLogs = buildLogs,
        failureReason = failureReason,
        remoteJobId = remoteJobId,
        runtimeInfo = runtimeInfo
    )

    companion object {
        fun fromDomain(record: BuildRecord): BuildEntity = BuildEntity(
            id = record.id,
            projectId = record.projectId,
            projectName = record.projectName,
            buildType = record.buildType.name,
            status = record.status.name,
            outputArtifactName = record.outputArtifactName,
            outputArtifactPath = record.outputArtifactPath,
            fileSizeBytes = record.fileSizeBytes,
            durationMillis = record.durationMillis,
            timestamp = record.timestamp,
            buildProvider = record.buildProvider,
            packageName = record.packageName,
            buildLogs = record.buildLogs,
            failureReason = record.failureReason,
            remoteJobId = record.remoteJobId,
            runtimeInfo = record.runtimeInfo
        )
    }
}
