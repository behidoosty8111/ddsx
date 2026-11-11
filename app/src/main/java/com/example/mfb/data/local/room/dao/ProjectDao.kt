package com.example.mfb.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mfb.data.local.room.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query(
        """
        SELECT * FROM projects
        WHERE (:query = '' OR name LIKE '%' || :query || '%' COLLATE NOCASE
            OR originalFileName LIKE '%' || :query || '%' COLLATE NOCASE
            OR sourceType LIKE '%' || :query || '%' COLLATE NOCASE
            OR description LIKE '%' || :query || '%' COLLATE NOCASE
            OR tags LIKE '%' || :query || '%' COLLATE NOCASE)
        ORDER BY updatedAt DESC
        """
    )
    fun searchProjects(query: String): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE projectUuid = :uuid")
    suspend fun getProjectByUuid(uuid: String): ProjectEntity?

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getProjectsCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM projects WHERE LOWER(name) = LOWER(:name) AND id != :excludeId)")
    suspend fun existsByNameExcludingId(name: String, excludeId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("UPDATE projects SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProjectName(id: Long, name: String, updatedAt: Long)

    @Query("UPDATE projects SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean, updatedAt: Long)

    @Query("UPDATE projects SET lastOpenedAt = :lastOpenedAt WHERE id = :id")
    suspend fun updateLastOpened(id: Long, lastOpenedAt: Long)

    @Query("UPDATE projects SET name = :name, description = :description, tags = :tags, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProjectDetails(id: Long, name: String, description: String, tags: String, updatedAt: Long)

    @Query("UPDATE projects SET fileCount = :fileCount, totalSizeBytes = :totalSizeBytes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProjectStats(id: Long, fileCount: Int, totalSizeBytes: Long, updatedAt: Long)

    @Query("UPDATE projects SET mainHtmlRelativePath = :entryPoint, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateEntryPoint(id: Long, entryPoint: String, updatedAt: Long)

    @Query("UPDATE projects SET healthStatus = :healthStatus, healthMessage = :healthMessage, missingAssets = :missingAssets WHERE id = :id")
    suspend fun updateHealthStatus(id: Long, healthStatus: String, healthMessage: String, missingAssets: String)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)
}
