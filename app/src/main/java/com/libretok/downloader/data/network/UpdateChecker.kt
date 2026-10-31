package com.libretok.downloader.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
        .build()
) {
    suspend fun checkUpdate(currentVersion: String = "1.0.4"): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
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
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "").trim()
            val releaseName = json.optString("name", "").trim()
            val releaseBody = json.optString("body", "").trim()
            val htmlUrl = json.optString("html_url", "https://github.com/sunandar3221/LibreTok/releases/latest")

            var downloadUrl = htmlUrl
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i)
                    val assetName = asset?.optString("name", "") ?: ""
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        val apkDownloadUrl = asset?.optString("browser_download_url", "")
                        if (!apkDownloadUrl.isNullOrBlank()) {
                            downloadUrl = apkDownloadUrl
                            break
                        }
                    }
                }
            }

            val rawTag = tagName.replace("v", "").replace("V", "").trim()
            val isNewer = isVersionNewer(rawTag, currentVersion)

            val info = AppUpdateInfo(
                latestVersion = if (tagName.isNotBlank()) tagName else "v$rawTag",
                currentVersion = "v$currentVersion",
                changelog = if (releaseBody.isNotBlank()) releaseBody else "Pembaruan versi terbaru LibreTok dengan peningkatan fitur dan stabilitas.",
                downloadUrl = downloadUrl,
                releasePageUrl = htmlUrl,
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
