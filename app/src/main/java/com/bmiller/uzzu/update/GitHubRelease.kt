package com.bmiller.uzzu.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The parts of GitHub's `releases/latest` response the updater needs. */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<Asset> = emptyList(),
) {
    @Serializable
    data class Asset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
        val size: Long,
        @SerialName("content_type") val contentType: String? = null,
    )

    /** The release APK: the .apk asset (the release workflow attaches exactly one). */
    val apk: Asset? get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): GitHubRelease = json.decodeFromString(serializer(), body)
    }
}
