package com.example.mfb.data.repository

import com.example.mfb.data.local.room.dao.ProjectDao
import com.example.mfb.data.local.room.entity.ProjectEntity
import com.example.mfb.domain.model.Project
import com.example.mfb.domain.model.ProjectHealthStatus
import com.example.mfb.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepositoryImpl(
    private val projectDao: ProjectDao
) : ProjectRepository {

    override fun getAllProjects(): Flow<List<Project>> {
        return projectDao.getAllProjects().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchProjects(query: String): Flow<List<Project>> {
        return projectDao.searchProjects(query.trim()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getProjectById(id: Long): Project? {
        return projectDao.getProjectById(id)?.toDomain()
    }

    override suspend fun getProjectByUuid(uuid: String): Project? {
        return projectDao.getProjectByUuid(uuid)?.toDomain()
    }

    override suspend fun existsByNameExcludingId(name: String, excludeId: Long): Boolean {
        return projectDao.existsByNameExcludingId(name.trim(), excludeId)
    }

    override suspend fun insertProject(project: Project): Long {
        return projectDao.insertProject(ProjectEntity.fromDomain(project))
    }

    override suspend fun updateProject(project: Project) {
        projectDao.updateProject(ProjectEntity.fromDomain(project))
    }

    override suspend fun updateProjectName(id: Long, name: String) {
        projectDao.updateProjectName(id, name, System.currentTimeMillis())
    }

    override suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        projectDao.updateFavorite(id, isFavorite, System.currentTimeMillis())
    }

    override suspend fun recordProjectOpened(id: Long) {
        projectDao.updateLastOpened(id, System.currentTimeMillis())
    }

    override suspend fun updateProjectMetadata(
        id: Long,
        name: String,
        description: String,
        tags: List<String>
    ) {
        projectDao.updateProjectDetails(
            id = id,
            name = name,
            description = description,
            tags = tags.joinToString(","),
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateProjectStats(id: Long, fileCount: Int, totalSizeBytes: Long) {
        projectDao.updateProjectStats(
            id = id,
            fileCount = fileCount,
            totalSizeBytes = totalSizeBytes,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateEntryPoint(id: Long, entryPointRelativePath: String) {
        projectDao.updateEntryPoint(id, entryPointRelativePath, System.currentTimeMillis())
    }

    override suspend fun updateHealthStatus(
        id: Long,
        status: ProjectHealthStatus,
        message: String,
        missingLocalAssets: List<String>
    ) {
        projectDao.updateHealthStatus(
            id = id,
            healthStatus = status.name,
            healthMessage = message,
            missingAssets = missingLocalAssets.joinToString(";;;")
        )
    }

    override suspend fun deleteProject(project: Project) {
        projectDao.deleteProject(ProjectEntity.fromDomain(project))
    }

    override suspend fun deleteProjectById(id: Long) {
        projectDao.deleteProjectById(id)
    }

    override suspend fun getProjectsCount(): Int {
        return projectDao.getProjectsCount()
    }
}
