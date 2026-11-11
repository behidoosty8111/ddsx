package com.example.mfb.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mfb.data.local.room.entity.BuildEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildDao {
    @Query("SELECT * FROM builds ORDER BY timestamp DESC")
    fun getAllBuilds(): Flow<List<BuildEntity>>

    @Query("SELECT * FROM builds ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentBuilds(limit: Int): Flow<List<BuildEntity>>

    @Query("SELECT * FROM builds WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getBuildsForProject(projectId: Long): Flow<List<BuildEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuild(buildEntity: BuildEntity): Long

    @Delete
    suspend fun deleteBuild(buildEntity: BuildEntity)
}
