package com.example.mfb.domain.repository

import com.example.mfb.domain.model.BuildRecord
import kotlinx.coroutines.flow.Flow

interface BuildRepository {
    fun getAllBuilds(): Flow<List<BuildRecord>>
    fun getRecentBuilds(limit: Int = 5): Flow<List<BuildRecord>>
    suspend fun insertBuild(buildRecord: BuildRecord): Long
    suspend fun deleteBuild(buildRecord: BuildRecord)
}
