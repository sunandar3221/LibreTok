package com.libretok.downloader.data.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class GithubRelease(
    @SerializedName("tag_name") val tagName: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("html_url") val htmlUrl: String?,
    @SerializedName("assets") val assets: List<GithubAsset>?
)

data class GithubAsset(
    @SerializedName("name") val name: String?,
    @SerializedName("browser_download_url") val downloadUrl: String?,
    @SerializedName("size") val size: Long?
)

data class AppUpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val changelog: String,
    val downloadUrl: String,
    val releasePageUrl: String,
    val isUpdateAvailable: Boolean
)

class UpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {
    suspend fun checkUpdate(currentVersion: String = "1.0.2"): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/sunandar3221/LibreTok/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "LibreTokApp-v$currentVersion")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gagal menghubungi server GitHub (HTTP ${response.code})"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Respon update kosong"))
            val release = gson.fromJson(body, GithubRelease::class.java)

            val rawTag = release.tagName?.replace("v", "")?.trim() ?: ""
            val apkAsset = release.assets?.firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true }
            val downloadUrl = apkAsset?.downloadUrl ?: release.htmlUrl ?: "https://github.com/sunandar3221/LibreTok/releases/latest"
            val releasePageUrl = release.htmlUrl ?: "https://github.com/sunandar3221/LibreTok/releases/latest"

            val isNewer = isVersionNewer(rawTag, currentVersion)

            val info = AppUpdateInfo(
                latestVersion = release.tagName ?: "v$rawTag",
                currentVersion = "v$currentVersion",
                changelog = release.body ?: "Pembaruan versi terbaru LibreTok dengan peningkatan fitur dan stabilitas.",
                downloadUrl = downloadUrl,
                releasePageUrl = releasePageUrl,
                isUpdateAvailable = isNewer
            )
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        if (latest.isBlank()) return false
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
