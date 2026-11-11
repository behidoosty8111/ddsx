package com.example.mfb.domain.repository

import com.example.mfb.domain.model.ReleaseInfo

interface ChangelogRepository {
    fun getAllReleases(): List<ReleaseInfo>
    fun getLatestRelease(): ReleaseInfo?
    fun getReleaseByVersionCode(versionCode: Int): ReleaseInfo?
}
