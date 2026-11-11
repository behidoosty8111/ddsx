package com.example.mfb.data.repository

import com.example.mfb.data.local.room.dao.BuildDao
import com.example.mfb.data.local.room.entity.BuildEntity
import com.example.mfb.domain.model.BuildRecord
import com.example.mfb.domain.repository.BuildRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BuildRepositoryImpl(
    private val buildDao: BuildDao
) : BuildRepository {

    override fun getAllBuilds(): Flow<List<BuildRecord>> {
        return buildDao.getAllBuilds().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRecentBuilds(limit: Int): Flow<List<BuildRecord>> {
        return buildDao.getRecentBuilds(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertBuild(buildRecord: BuildRecord): Long {
        return buildDao.insertBuild(BuildEntity.fromDomain(buildRecord))
    }

    override suspend fun deleteBuild(buildRecord: BuildRecord) {
        buildDao.deleteBuild(BuildEntity.fromDomain(buildRecord))
    }
}
