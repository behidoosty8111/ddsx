package com.example.mfb.data.changelog

import android.content.Context
import com.example.mfb.domain.model.ChangeItem
import com.example.mfb.domain.model.ReleaseInfo
import com.example.mfb.domain.model.ReleaseType
import com.example.mfb.domain.repository.ChangelogRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader

class ChangelogRepositoryImpl(
    private val context: Context
) : ChangelogRepository {

    private val cachedReleases: List<ReleaseInfo> by lazy {
        loadReleasesFromAssets()
    }

    override fun getAllReleases(): List<ReleaseInfo> {
        return cachedReleases
    }

    override fun getLatestRelease(): ReleaseInfo? {
        return cachedReleases.maxByOrNull { it.versionCode }
    }

    override fun getReleaseByVersionCode(versionCode: Int): ReleaseInfo? {
        return cachedReleases.firstOrNull { it.versionCode == versionCode }
    }

    private fun loadReleasesFromAssets(): List<ReleaseInfo> {
        return try {
            val jsonString = context.assets.open("changelog.json").use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).readText()
            }
            parseReleasesJson(jsonString)
        } catch (e: Exception) {
            getFallbackReleases()
        }
    }

    private fun parseReleasesJson(json: String): List<ReleaseInfo> {
        val array = JSONArray(json)
        val releases = mutableListOf<ReleaseInfo>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val releaseType = try {
                ReleaseType.valueOf(obj.optString("releaseType", "FEATURE_UPDATE"))
            } catch (e: Exception) {
                ReleaseType.FEATURE_UPDATE
            }

            releases.add(
                ReleaseInfo(
                    id = obj.getString("id"),
                    versionName = obj.getString("versionName"),
                    versionCode = obj.getInt("versionCode"),
                    phaseNumber = obj.getInt("phaseNumber"),
                    releaseTitle = obj.getString("releaseTitle"),
                    releaseDate = obj.getString("releaseDate"),
                    releaseDescription = obj.getString("releaseDescription"),
                    releaseType = releaseType,
                    addedFeatures = parseChangeItems(obj.optJSONArray("addedFeatures")),
                    improvedFeatures = parseChangeItems(obj.optJSONArray("improvedFeatures")),
                    bugFixes = parseChangeItems(obj.optJSONArray("bugFixes")),
                    securityImprovements = parseChangeItems(obj.optJSONArray("securityImprovements")),
                    removedFeatures = parseChangeItems(obj.optJSONArray("removedFeatures")),
                    knownIssues = parseChangeItems(obj.optJSONArray("knownIssues"))
                )
            )
        }
        return releases.sortedByDescending { it.versionCode }
    }

    private fun parseChangeItems(array: JSONArray?): List<ChangeItem> {
        if (array == null) return emptyList()
        val items = mutableListOf<ChangeItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            items.add(
                ChangeItem(
                    category = obj.getString("category"),
                    title = obj.getString("title"),
                    description = obj.getString("description"),
                    relatedFeature = obj.optString("relatedFeature").takeIf { it.isNotBlank() },
                    technicalNote = obj.optString("technicalNote").takeIf { it.isNotBlank() }
                )
            )
        }
        return items
    }

    private fun getFallbackReleases(): List<ReleaseInfo> {
        return listOf(
            ReleaseInfo(
                id = "rel-phase-03",
                versionName = "0.3.0",
                versionCode = 3,
                phaseNumber = 3,
                releaseTitle = "Project Workspace, File Manager & Update Log",
                releaseDate = "November 10, 2026",
                releaseDescription = "Introduces the full-fledged Project Workspace, internal Project File Explorer, safe file and folder operations, project duplication, source ZIP export, favorites, and the permanent application Update Log system.",
                releaseType = ReleaseType.FEATURE_UPDATE
            ),
            ReleaseInfo(
                id = "rel-phase-02",
                versionName = "0.2.0",
                versionCode = 2,
                phaseNumber = 2,
                releaseTitle = "Professional Import Engine & Storage Sandbox",
                releaseDate = "November 10, 2026",
                releaseDescription = "Delivered native ZIP and HTML import engines, Zip Slip protection, decompression bomb defense quotas, and Room database persistence.",
                releaseType = ReleaseType.FEATURE_UPDATE
            ),
            ReleaseInfo(
                id = "rel-phase-01",
                versionName = "0.1.0",
                versionCode = 1,
                phaseNumber = 1,
                releaseTitle = "Foundation, Architecture & Professional Dashboard",
                releaseDate = "November 09, 2026",
                releaseDescription = "Initial architectural foundation establishing Clean Architecture, MVVM, Room SQLite database, and primary dashboard.",
                releaseType = ReleaseType.DEVELOPMENT_MILESTONE
            )
        )
    }
}
