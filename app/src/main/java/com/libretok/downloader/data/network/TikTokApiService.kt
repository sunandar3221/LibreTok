package com.libretok.downloader.data.network

import com.google.gson.Gson
import com.libretok.downloader.data.model.TikTokMedia
import com.libretok.downloader.data.model.TikWmResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
        private val URL_PATTERN = Pattern.compile("https?://[a-zA-Z0-9./?=_%&+\\-#]+")
    }

    /**
     * Extracts a TikTok URL from any text (e.g., share text from clipboard)
     */
    fun extractUrl(text: String): String? {
        val matcher = URL_PATTERN.matcher(text)
        while (matcher.find()) {
            val candidate = matcher.group()
            if (candidate.contains("tiktok.com") || candidate.contains("douyin.com")) {
                return candidate
            }
        }
        return null
    }

    /**
     * Fetches metadata and watermark-free video download links from TikWM API
     */
    suspend fun fetchMedia(url: String): Result<TikTokMedia> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = extractUrl(url) ?: url.trim()
            val encodedUrl = URLEncoder.encode(cleanUrl, "UTF-8")
            val requestUrl = "$TIKWM_API_URL?url=$encodedUrl&hd=1"

            val request = Request.Builder()
                .url(requestUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36")
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
                originalUrl = cleanUrl
            )

            Result.success(media)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
