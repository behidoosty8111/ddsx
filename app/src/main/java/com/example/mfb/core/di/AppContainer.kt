package com.example.mfb.core.di

import android.content.Context
import com.example.mfb.data.changelog.ChangelogRepositoryImpl
import com.example.mfb.data.local.datastore.UserPreferencesRepository
import com.example.mfb.data.local.room.MfbDatabase
import com.example.mfb.data.repository.BuildRepositoryImpl
import com.example.mfb.data.repository.ProjectRepositoryImpl
import com.example.mfb.domain.repository.BuildRepository
import com.example.mfb.domain.repository.ChangelogRepository
import com.example.mfb.domain.repository.ProjectRepository

interface AppContainer {
    val appContext: Context
    val userPreferencesRepository: UserPreferencesRepository
    val projectRepository: ProjectRepository
    val buildRepository: BuildRepository
    val changelogRepository: ChangelogRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val appContext: Context = context.applicationContext

    private val database: MfbDatabase by lazy {
        MfbDatabase.getInstance(context)
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }

    override val projectRepository: ProjectRepository by lazy {
        ProjectRepositoryImpl(database.projectDao())
    }

    override val buildRepository: BuildRepository by lazy {
        BuildRepositoryImpl(database.buildDao())
    }

    override val changelogRepository: ChangelogRepository by lazy {
        ChangelogRepositoryImpl(context)
    }
}
