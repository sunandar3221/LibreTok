package com.libretok.downloader.data.network

import com.google.gson.Gson
import com.libretok.downloader.data.model.CobaltResponse
import com.libretok.downloader.data.model.TikTokMedia
import com.libretok.downloader.data.model.TikWmResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class TikTokApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
    private val gson: Gson = Gson()
) {

    companion object {
        private const val TIKWM_API_URL = "https://www.tikwm.com/api/"
        private val COBALT_INSTANCES = listOf(
            "https://co.otomir23.me/",
            "https://cobaltapi.cjs.nz/"
        )
        private val URL_PATTERN = Pattern.compile("https?://[a-zA-Z0-9./?=_%&+\\-#]+")
        private val YOUTUBE_ID_PATTERN = Pattern.compile("(?:v=|youtu\\.be/|shorts/)([a-zA-Z0-9_-]{11})")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Extracts social media video URLs from shared text or clipboard
     */
    fun extractUrl(text: String): String? {
        val matcher = URL_PATTERN.matcher(text)
        var firstValidUrl: String? = null

        while (matcher.find()) {
            val candidate = matcher.group()
            val lower = candidate.lowercase()
            if (lower.contains("tiktok.com") || lower.contains("douyin.com") ||
                lower.contains("instagram.com") || lower.contains("instagr.am") || lower.contains("threads.net") ||
                lower.contains("youtube.com") || lower.contains("youtu.be") ||
                lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") ||
                lower.contains("twitter.com") || lower.contains("x.com")
            ) {
                return candidate
            }
            if (firstValidUrl == null) {
                firstValidUrl = candidate
            }
        }
        return firstValidUrl
    }

    /**
     * Detects the social media platform
     */
    fun detectPlatform(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("tiktok.com") || lower.contains("douyin.com") -> "TikTok"
            lower.contains("instagram.com") || lower.contains("instagr.am") || lower.contains("threads.net") -> "Instagram"
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube"
            lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") -> "Facebook"
            lower.contains("twitter.com") || lower.contains("x.com") -> "Twitter/X"
            else -> "Video"
        }
    }

    /**
     * Fetches metadata & download links for TikTok, Instagram, YouTube, Facebook, etc.
     */
    suspend fun fetchMedia(url: String): Result<TikTokMedia> = withContext(Dispatchers.IO) {
        val cleanUrl = extractUrl(url) ?: url.trim()
        val platform = detectPlatform(cleanUrl)

        if (platform == "TikTok") {
            val tikWmResult = fetchTikWm(cleanUrl)
            if (tikWmResult.isSuccess) {
                return@withContext tikWmResult
            }
            // Fallback to Cobalt if TikWM fails
            val cobaltResult = fetchCobalt(cleanUrl, platform)
            if (cobaltResult.isSuccess) {
                return@withContext cobaltResult
            }
            return@withContext tikWmResult
        } else {
            // Instagram, YouTube, Facebook, etc.
            val cobaltResult = fetchCobalt(cleanUrl, platform)
            if (cobaltResult.isSuccess) {
                return@withContext cobaltResult
            }
            return@withContext Result.failure(
                cobaltResult.exceptionOrNull()
                    ?: Exception("Gagal mengunduh video dari $platform. Pastikan link bersifat publik.")
            )
        }
    }

    private suspend fun fetchTikWm(cleanUrl: String): Result<TikTokMedia> = withContext(Dispatchers.IO) {
        try {
            val encodedUrl = URLEncoder.encode(cleanUrl, "UTF-8")
            val requestUrl = "$TIKWM_API_URL?url=$encodedUrl&hd=1"

            val request = Request.Builder()
                .url(requestUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
            val tikWmResponse = gson.fromJson(body, TikWmResponse::class.java)

            if (tikWmResponse.code != 0 || tikWmResponse.data == null) {
                val errorMsg = tikWmResponse.msg ?: "Gagal mendapatkan video TikTok dari link ini."
                return@withContext Result.failure(Exception(errorMsg))
            }

            val data = tikWmResponse.data
            val baseUrl = "https://www.tikwm.com"

            fun fixUrl(u: String?): String? {
                if (u.isNullOrBlank()) return null
                return if (u.startsWith("http")) u else "$baseUrl$u"
            }

            val isImages = !data.images.isNullOrEmpty()
            val playUrl = fixUrl(data.play) ?: ""
            val hdPlayUrl = fixUrl(data.hdplay)
            val musicUrl = fixUrl(data.music) ?: fixUrl(data.musicInfo?.play)
            val coverUrl = fixUrl(data.originCover) ?: fixUrl(data.cover) ?: ""
            val avatarUrl = fixUrl(data.author?.avatar) ?: ""
            val imagesList = data.images?.mapNotNull { fixUrl(it) } ?: emptyList()

            val media = TikTokMedia(
                id = data.id ?: System.currentTimeMillis().toString(),
                title = data.title?.ifBlank { "TikTok Video" } ?: "TikTok Video",
                authorName = data.author?.nickname ?: "TikTok User",
                authorUsername = data.author?.uniqueId ?: "",
                authorAvatar = avatarUrl,
                coverUrl = coverUrl,
                videoUrl = playUrl,
                hdVideoUrl = hdPlayUrl,
                musicUrl = musicUrl,
                musicTitle = data.musicInfo?.title ?: "Original Sound",
                duration = data.duration ?: 0,
                sizeBytes = data.size ?: 0L,
                isImagesPost = isImages,
                images = imagesList,
                originalUrl = cleanUrl,
                platform = "TikTok"
            )

            Result.success(media)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchCobalt(cleanUrl: String, platform: String): Result<TikTokMedia> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        for (endpoint in COBALT_INSTANCES) {
            try {
                // Query video and audio concurrently
                val videoDeferred = async { requestCobalt(endpoint, cleanUrl, isAudio = false) }
                val audioDeferred = async { requestCobalt(endpoint, cleanUrl, isAudio = true) }

                val videoResp = videoDeferred.await()
                val audioResp = audioDeferred.await()

                if (videoResp != null && (videoResp.status == "tunnel" || videoResp.status == "redirect" || videoResp.status == "picker")) {
                    val media = mapCobaltToMedia(cleanUrl, platform, videoResp, audioResp)
                    return@withContext Result.success(media)
                }

                if (videoResp?.error != null) {
                    lastError = Exception("Error ${videoResp.error.code ?: "Cobalt"}")
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        Result.failure(lastError ?: Exception("Gagal memproses link $platform. Pastikan link valid dan publik."))
    }

    private fun requestCobalt(endpoint: String, targetUrl: String, isAudio: Boolean): CobaltResponse? {
        return try {
            val payload = if (isAudio) {
                mapOf("url" to targetUrl, "downloadMode" to "audio")
            } else {
                mapOf("url" to targetUrl, "videoQuality" to "1080")
            }
            val jsonString = gson.toJson(payload)
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "LibreTok/1.0.1")
                .post(jsonString.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            gson.fromJson(body, CobaltResponse::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun mapCobaltToMedia(
        cleanUrl: String,
        platform: String,
        videoResp: CobaltResponse,
        audioResp: CobaltResponse?
    ): TikTokMedia {
        val ytMatcher = YOUTUBE_ID_PATTERN.matcher(cleanUrl)
        val youtubeId = if (ytMatcher.find()) ytMatcher.group(1) else null

        // Generate clean title from filename
        val rawFilename = videoResp.filename ?: audioResp?.filename ?: "$platform Media"
        val cleanTitle = rawFilename
            .replace(".mp4", "")
            .replace(".mp3", "")
            .replace(".webm", "")
            .replace(Regex("\\([0-9]+p.*\\)"), "")
            .trim()
            .ifBlank { "$platform Video" }

        // Determine cover image
        val coverUrl = when {
            youtubeId != null -> "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"
            videoResp.status == "picker" -> {
                videoResp.picker?.firstOrNull()?.thumb
                    ?: videoResp.picker?.firstOrNull()?.url
                    ?: ""
            }
            else -> ""
        }

        // Handle Picker (Slideshow / Carousels / Multi-images)
        val isPicker = videoResp.status == "picker"
        val pickerItems = videoResp.picker ?: emptyList()
        val imagesList = if (isPicker) {
            pickerItems.filter { it.type == "photo" }.mapNotNull { it.url }
        } else {
            emptyList()
        }
        val isImages = imagesList.isNotEmpty()

        val mainVideoUrl = when {
            isPicker -> pickerItems.firstOrNull { it.type == "video" }?.url ?: (videoResp.url ?: "")
            else -> videoResp.url ?: ""
        }

        val musicUrl = audioResp?.url

        return TikTokMedia(
            id = youtubeId ?: System.currentTimeMillis().toString(),
            title = cleanTitle,
            authorName = platform,
            authorUsername = platform.lowercase(),
            authorAvatar = "",
            coverUrl = coverUrl,
            videoUrl = mainVideoUrl,
            hdVideoUrl = mainVideoUrl,
            musicUrl = musicUrl,
            musicTitle = if (musicUrl != null) "$cleanTitle (Audio)" else null,
            duration = 0,
            sizeBytes = 0L,
            isImagesPost = isImages,
            images = imagesList,
            originalUrl = cleanUrl,
            platform = platform
        )
    }
}
