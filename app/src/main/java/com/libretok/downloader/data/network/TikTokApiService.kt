package com.libretok.downloader.data.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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

data class YouTubeOEmbed(
    @SerializedName("title") val title: String?,
    @SerializedName("author_name") val authorName: String?,
    @SerializedName("author_url") val authorUrl: String?,
    @SerializedName("thumbnail_url") val thumbnailUrl: String?
)

data class InnertubePlayerResponse(
    @SerializedName("playabilityStatus") val playabilityStatus: InnertubePlayabilityStatus?,
    @SerializedName("streamingData") val streamingData: InnertubeStreamingData?,
    @SerializedName("videoDetails") val videoDetails: InnertubeVideoDetails?
)

data class InnertubePlayabilityStatus(
    @SerializedName("status") val status: String?,
    @SerializedName("reason") val reason: String?
)

data class InnertubeStreamingData(
    @SerializedName("formats") val formats: List<InnertubeFormat>?,
    @SerializedName("adaptiveFormats") val adaptiveFormats: List<InnertubeFormat>?
)

data class InnertubeFormat(
    @SerializedName("itag") val itag: Int?,
    @SerializedName("url") val url: String?,
    @SerializedName("mimeType") val mimeType: String?,
    @SerializedName("bitrate") val bitrate: Long?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?,
    @SerializedName("contentLength") val contentLength: String?,
    @SerializedName("qualityLabel") val qualityLabel: String?,
    @SerializedName("approxDurationMs") val approxDurationMs: String?
)

data class InnertubeVideoDetails(
    @SerializedName("videoId") val videoId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("lengthSeconds") val lengthSeconds: String?,
    @SerializedName("author") val author: String?,
    @SerializedName("channelId") val channelId: String?,
    @SerializedName("shortDescription") val shortDescription: String?
)

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
        private val URL_PATTERN = Pattern.compile("https?://[^\\s<>\"'\\[\\]{}|^`]+")
        private val YOUTUBE_ID_PATTERN = Pattern.compile("(?:v=|youtu\\.be/|shorts/|embed/|/v/)([a-zA-Z0-9_-]{11})")
        private val INSTAGRAM_USER_PATTERN = Pattern.compile("instagram\\.com/([a-zA-Z0-9._]+)/(?:p|reel|tv|reels)")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Extracts and sanitizes social media video URLs from shared text or clipboard
     */
    fun extractUrl(text: String): String? {
        val matcher = URL_PATTERN.matcher(text)
        var firstValidUrl: String? = null

        while (matcher.find()) {
            val candidate = sanitizeUrl(matcher.group())
            val lower = candidate.lowercase()
            if (lower.contains("tiktok.com") || lower.contains("douyin.com") ||
                lower.contains("instagram.com") || lower.contains("instagr.am") || lower.contains("threads.net") || lower.contains("ig.me") ||
                lower.contains("youtube.com") || lower.contains("youtu.be") ||
                lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") ||
                lower.contains("twitter.com") || lower.contains("x.com") || lower.contains("t.co")
            ) {
                return candidate
            }
            if (firstValidUrl == null) {
                firstValidUrl = candidate
            }
        }
        return firstValidUrl?.let { sanitizeUrl(it) }
    }

    private fun sanitizeUrl(raw: String): String {
        var u = raw.trim()
        u = u.trimEnd('.', ',', '!', '?', ';', ':', ')', ']', '}', '>', '"', '\'', ' ')
        return u
    }

    /**
     * Resolves redirects for short links (vt.tiktok.com, vm.tiktok.com, youtu.be, fb.watch, t.co, /share/ links)
     */
    suspend fun resolveRedirectUrl(url: String): String = withContext(Dispatchers.IO) {
        val lower = url.lowercase()
        val isShortLink = lower.contains("vt.tiktok.com") ||
                lower.contains("vm.tiktok.com") ||
                lower.contains("v.douyin.com") ||
                lower.contains("youtu.be") ||
                lower.contains("fb.watch") ||
                lower.contains("t.co") ||
                lower.contains("instagram.com/share/") ||
                lower.contains("ig.me")

        if (!isShortLink) {
            return@withContext url
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            val resolved = response.request.url.toString()
            response.close()

            if (resolved.isNotBlank() && !resolved.startsWith("about:")) {
                return@withContext sanitizeUrl(resolved)
            }
        } catch (_: Exception) {
            // Jika gagal resolve, tetap gunakan url asli
        }
        url
    }

    private fun cleanMediaUrl(url: String): String {
        var clean = url.trim()
        val igMatcher = Pattern.compile("instagram\\.com/(?:p|reel|reels|tv)/([a-zA-Z0-9_-]+)").matcher(clean)
        if (igMatcher.find()) {
            val code = igMatcher.group(1)
            val type = if (clean.contains("/reel")) "reel" else "p"
            return "https://www.instagram.com/$type/$code/"
        }
        val ytMatcher = YOUTUBE_ID_PATTERN.matcher(clean)
        if (ytMatcher.find()) {
            val id = ytMatcher.group(1)
            return "https://www.youtube.com/watch?v=$id"
        }
        val ttMatcher = Pattern.compile("tiktok\\.com/(@[a-zA-Z0-9._]+/(?:video|photo)/[0-9]+)").matcher(clean)
        if (ttMatcher.find()) {
            return "https://www.tiktok.com/${ttMatcher.group(1)}"
        }
        return clean
    }

    /**
     * Detects the social media platform
     */
    fun detectPlatform(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("tiktok.com") || lower.contains("douyin.com") -> "TikTok"
            lower.contains("instagram.com") || lower.contains("instagr.am") || lower.contains("threads.net") || lower.contains("ig.me") -> "Instagram"
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube"
            lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") -> "Facebook"
            lower.contains("twitter.com") || lower.contains("x.com") || lower.contains("t.co") -> "Twitter/X"
            else -> "Video"
        }
    }

    /**
     * Fetches metadata & download links for TikTok, Instagram, YouTube, Facebook, etc.
     */
    suspend fun fetchMedia(url: String, quality: String = "1080p"): Result<TikTokMedia> = withContext(Dispatchers.IO) {
        val extracted = extractUrl(url) ?: sanitizeUrl(url)
        val resolved = resolveRedirectUrl(extracted)
        val cleanUrl = cleanMediaUrl(resolved)
        val platform = detectPlatform(cleanUrl)
        val cleanQuality = quality.replace("p", "").ifBlank { "1080" }

        if (platform == "TikTok") {
            var tikWmResult = fetchTikWm(cleanUrl, cleanQuality)
            if (tikWmResult.isFailure && cleanUrl != resolved) {
                tikWmResult = fetchTikWm(resolved, cleanQuality)
            }
            if (tikWmResult.isFailure && resolved != extracted) {
                tikWmResult = fetchTikWm(extracted, cleanQuality)
            }

            if (tikWmResult.isSuccess) {
                return@withContext tikWmResult
            }
            // Fallback to Cobalt if TikWM fails
            val cobaltResult = fetchCobalt(cleanUrl, platform, cleanQuality)
            if (cobaltResult.isSuccess) {
                return@withContext cobaltResult
            }
            return@withContext Result.failure(
                Exception("Video TikTok tidak dapat ditemukan. Pastikan tautan benar dan video bersifat publik.")
            )
        } else if (platform == "YouTube") {
            // 1. Coba YouTube Innertube player API langsung (cepat, stabil, tanpa blokir bot datacenter)
            val innertubeResult = fetchYouTubeInnertube(cleanUrl, resolved, extracted, cleanQuality)
            if (innertubeResult.isSuccess) {
                return@withContext innertubeResult
            }

            // 2. Fallback ke Cobalt jika Innertube gagal
            var cobaltResult = fetchCobalt(cleanUrl, platform, cleanQuality)
            if (cobaltResult.isFailure && cleanUrl != resolved) {
                cobaltResult = fetchCobalt(resolved, platform, cleanQuality)
            }
            if (cobaltResult.isSuccess) {
                return@withContext cobaltResult
            }

            val errorMsg = innertubeResult.exceptionOrNull()?.message
                ?: "Video YouTube tidak ditemukan atau dibatasi. Pastikan video masih tersedia dan bersifat publik."
            return@withContext Result.failure(Exception(errorMsg))
        } else {
            // Instagram, Facebook, Twitter, etc.
            var cobaltResult = fetchCobalt(cleanUrl, platform, cleanQuality)
            if (cobaltResult.isFailure && cleanUrl != resolved) {
                cobaltResult = fetchCobalt(resolved, platform, cleanQuality)
            }
            if (cobaltResult.isSuccess) {
                return@withContext cobaltResult
            }

            val friendlyMsg = when (platform) {
                "Instagram" -> "Konten Instagram tidak dapat diakses atau akun bersifat privat. Pastikan postingan/Reel bersifat publik dan coba salin link langsung dari menu Bagikan di Instagram."
                "Facebook" -> "Video Facebook tidak dapat diakses. Pastikan video berasal dari postingan publik (bukan grup privat)."
                else -> "Gagal memproses tautan video dari $platform. Pastikan tautan masih aktif dan bersifat publik."
            }

            return@withContext Result.failure(Exception(friendlyMsg))
        }
    }

    private suspend fun fetchTikWm(cleanUrl: String, quality: String = "1080"): Result<TikTokMedia> = withContext(Dispatchers.IO) {
        try {
            val encodedUrl = URLEncoder.encode(cleanUrl, "UTF-8")
            val isHd = if (quality == "1080" || quality == "720") 1 else 0
            val requestUrl = "$TIKWM_API_URL?url=$encodedUrl&hd=$isHd"

            val request = Request.Builder()
                .url(requestUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Respon server kosong"))
            val tikWmResponse = gson.fromJson(body, TikWmResponse::class.java)

            if (tikWmResponse.code != 0 || tikWmResponse.data == null) {
                return@withContext Result.failure(Exception(tikWmResponse.msg ?: "Video TikTok tidak ditemukan"))
            }

            val data = tikWmResponse.data
            val baseUrl = "https://www.tikwm.com"

            fun fixUrl(u: String?): String? {
                if (u.isNullOrBlank()) return null
                return if (u.startsWith("http")) u else "$baseUrl$u"
            }

            val rawImages = data.images?.mapNotNull { fixUrl(it) } ?: emptyList()
            val playUrl = fixUrl(data.play) ?: ""
            val hdPlayUrl = fixUrl(data.hdplay)
            val wmPlayUrl = fixUrl(data.wmplay)
            val musicUrl = fixUrl(data.music) ?: fixUrl(data.musicInfo?.play)
            val coverUrl = fixUrl(data.originCover) ?: fixUrl(data.cover) ?: ""
            val avatarUrl = fixUrl(data.author?.avatar) ?: ""
            val imagesList = when {
                rawImages.isNotEmpty() -> rawImages
                playUrl.isBlank() && coverUrl.isNotBlank() -> listOf(coverUrl)
                else -> emptyList()
            }
            val isImages = imagesList.isNotEmpty()
            val postTitle = data.title?.ifBlank { "TikTok Media" } ?: "TikTok Media"

            val media = TikTokMedia(
                id = data.id ?: System.currentTimeMillis().toString(),
                title = postTitle,
                authorName = data.author?.nickname?.ifBlank { "TikTok Creator" } ?: "TikTok Creator",
                authorUsername = data.author?.uniqueId ?: "",
                authorAvatar = avatarUrl,
                coverUrl = coverUrl,
                videoUrl = playUrl,
                hdVideoUrl = hdPlayUrl,
                wmVideoUrl = wmPlayUrl,
                musicUrl = musicUrl,
                musicTitle = data.musicInfo?.title ?: "Original Sound",
                duration = data.duration ?: 0,
                sizeBytes = data.size ?: 0L,
                isImagesPost = isImages,
                images = imagesList,
                originalUrl = cleanUrl,
                platform = "TikTok",
                description = postTitle,
                selectedQuality = "${quality}p"
            )

            Result.success(media)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun extractYouTubeId(url: String): String? {
        val matcher = YOUTUBE_ID_PATTERN.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private suspend fun fetchYouTubeInnertube(
        cleanUrl: String,
        resolvedUrl: String,
        extractedUrl: String,
        quality: String = "1080"
    ): Result<TikTokMedia> = withContext(Dispatchers.IO) {
        try {
            val videoId = extractYouTubeId(cleanUrl)
                ?: extractYouTubeId(resolvedUrl)
                ?: extractYouTubeId(extractedUrl)
                ?: return@withContext Result.failure(Exception("ID video YouTube tidak ditemukan"))

            val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"

            // Ambil oEmbed secara paralel untuk judul, channel, dan thumbnail resmi
            val oEmbedDeferred = async { fetchYouTubeOEmbed(canonicalUrl) }

            // Ambil progressive stream (video + audio muxed) melalui client ANDROID
            val videoPayload = mapOf(
                "videoId" to videoId,
                "racyCheckOk" to true,
                "contentCheckOk" to true,
                "context" to mapOf(
                    "client" to mapOf(
                        "clientName" to "ANDROID",
                        "clientVersion" to "21.26.364",
                        "androidSdkVersion" to 30,
                        "osName" to "Android",
                        "osVersion" to "11",
                        "hl" to "en",
                        "gl" to "US"
                    )
                )
            )

            val videoRequest = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .header("Content-Type", "application/json")
                .header("User-Agent", "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip")
                .header("Accept", "application/json")
                .post(gson.toJson(videoPayload).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val videoResponse = client.newCall(videoRequest).execute()
            if (!videoResponse.isSuccessful) {
                return@withContext Result.failure(Exception("YouTube API gagal merespon (HTTP ${videoResponse.code})"))
            }

            val videoBody = videoResponse.body?.string()
                ?: return@withContext Result.failure(Exception("Respon YouTube kosong"))
            val playerResp = gson.fromJson(videoBody, InnertubePlayerResponse::class.java)

            val status = playerResp?.playabilityStatus?.status
            if (status != "OK") {
                val reason = playerResp?.playabilityStatus?.reason
                    ?: "Video YouTube tidak dapat diputar (Status: $status)"
                return@withContext Result.failure(Exception(reason))
            }

            val formats = playerResp.streamingData?.formats ?: emptyList()
            val validFormats = formats.filter { !it.url.isNullOrBlank() }
            val chosenFormat = validFormats.maxByOrNull { it.bitrate ?: 0L }

            val videoUrl = chosenFormat?.url
            if (videoUrl.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Format video YouTube tidak tersedia untuk diunduh"))
            }

            // Ambil stream audio m4a/aac melalui client IOS
            val audioFormatDeferred = async {
                try {
                    val audioPayload = mapOf(
                        "videoId" to videoId,
                        "racyCheckOk" to true,
                        "contentCheckOk" to true,
                        "context" to mapOf(
                            "client" to mapOf(
                                "clientName" to "IOS",
                                "clientVersion" to "20.10.4",
                                "deviceMake" to "Apple",
                                "deviceModel" to "iPhone16,2",
                                "osName" to "iPhone",
                                "osVersion" to "18.3.2.22D82",
                                "hl" to "en",
                                "gl" to "US"
                            )
                        )
                    )

                    val audioRequest = Request.Builder()
                        .url("https://www.youtube.com/youtubei/v1/player")
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X; en_US)")
                        .header("Accept", "application/json")
                        .post(gson.toJson(audioPayload).toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                    val audioResp = client.newCall(audioRequest).execute()
                    if (audioResp.isSuccessful) {
                        val aBody = audioResp.body?.string()
                        val aJson = gson.fromJson(aBody, InnertubePlayerResponse::class.java)
                        val adaptives = aJson?.streamingData?.adaptiveFormats ?: emptyList()
                        adaptives.filter { it.mimeType?.startsWith("audio/mp4") == true && !it.url.isNullOrBlank() }
                            .maxByOrNull { it.bitrate ?: 0L }
                            ?.url
                    } else null
                } catch (_: Exception) {
                    null
                }
            }

            val ytOEmbed = oEmbedDeferred.await()
            val musicUrl = audioFormatDeferred.await()

            val details = playerResp.videoDetails
            val title = ytOEmbed?.title?.ifBlank { null }
                ?: details?.title?.ifBlank { null }
                ?: "YouTube Video"

            val authorName = ytOEmbed?.authorName?.ifBlank { null }
                ?: details?.author?.ifBlank { null }
                ?: "YouTube Creator"

            val authorUsername = ytOEmbed?.authorUrl?.substringAfterLast("/")?.replace("@", "")
                ?: details?.channelId
                ?: "youtube"

            val coverUrl = ytOEmbed?.thumbnailUrl
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            val duration = details?.lengthSeconds?.toIntOrNull()
                ?: ((chosenFormat.approxDurationMs?.toLongOrNull() ?: 0L) / 1000L).toInt()

            val sizeBytes = chosenFormat.contentLength?.toLongOrNull() ?: 0L

            val media = TikTokMedia(
                id = videoId,
                title = title,
                authorName = authorName,
                authorUsername = authorUsername,
                authorAvatar = "",
                coverUrl = coverUrl,
                videoUrl = videoUrl,
                hdVideoUrl = videoUrl,
                wmVideoUrl = null,
                musicUrl = musicUrl,
                musicTitle = if (musicUrl != null) "$title (Audio)" else null,
                duration = duration,
                sizeBytes = sizeBytes,
                isImagesPost = false,
                images = emptyList(),
                originalUrl = canonicalUrl,
                platform = "YouTube",
                description = title,
                selectedQuality = chosenFormat.qualityLabel ?: "360p"
            )

            Result.success(media)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchCobalt(cleanUrl: String, platform: String, quality: String = "1080"): Result<TikTokMedia> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        // If YouTube, fetch official oEmbed in parallel to get exact channel name, handle, and thumbnail
        val youtubeOEmbedDeferred = if (platform == "YouTube") {
            async { fetchYouTubeOEmbed(cleanUrl) }
        } else null

        for (endpoint in COBALT_INSTANCES) {
            try {
                val videoDeferred = async { requestCobalt(endpoint, cleanUrl, isAudio = false, quality = quality) }
                val audioDeferred = async { requestCobalt(endpoint, cleanUrl, isAudio = true) }

                val videoResp = videoDeferred.await()
                val audioResp = audioDeferred.await()

                if (videoResp != null && (videoResp.status == "tunnel" || videoResp.status == "redirect" || videoResp.status == "picker")) {
                    val ytOEmbed = youtubeOEmbedDeferred?.await()
                    val media = mapCobaltToMedia(cleanUrl, platform, videoResp, audioResp, ytOEmbed, quality)
                    return@withContext Result.success(media)
                }

                if (videoResp?.error != null) {
                    lastError = Exception("Error ${videoResp.error.code ?: "Cobalt"}")
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        Result.failure(lastError ?: Exception("Gagal memproses tautan"))
    }

    private fun fetchYouTubeOEmbed(url: String): YouTubeOEmbed? {
        return try {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val oEmbedUrl = "https://www.youtube.com/oembed?url=$encoded&format=json"
            val request = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            gson.fromJson(body, YouTubeOEmbed::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun requestCobalt(endpoint: String, targetUrl: String, isAudio: Boolean, quality: String = "1080"): CobaltResponse? {
        val cobaltQuality = if (quality == "1080" || quality.isBlank() || quality == "max") "max" else quality
        val resp = executeCobaltRequest(endpoint, targetUrl, isAudio, cobaltQuality)
        if (resp != null && (resp.status == "tunnel" || resp.status == "redirect" || resp.status == "picker")) {
            return resp
        }
        // Fallback otomatis ke "max" jika resolusi spesifik tidak tersedia di platform
        if (!isAudio && cobaltQuality != "max") {
            val fallbackResp = executeCobaltRequest(endpoint, targetUrl, isAudio, "max")
            if (fallbackResp != null && (fallbackResp.status == "tunnel" || fallbackResp.status == "redirect" || fallbackResp.status == "picker")) {
                return fallbackResp
            }
        }
        return resp
    }

    private fun executeCobaltRequest(endpoint: String, targetUrl: String, isAudio: Boolean, quality: String): CobaltResponse? {
        return try {
            val payload = if (isAudio) {
                mapOf("url" to targetUrl, "downloadMode" to "audio")
            } else {
                mapOf("url" to targetUrl, "videoQuality" to quality)
            }
            val jsonString = gson.toJson(payload)
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "LibreTok/1.0.4")
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
        audioResp: CobaltResponse?,
        ytOEmbed: YouTubeOEmbed?,
        quality: String = "1080"
    ): TikTokMedia {
        val ytMatcher = YOUTUBE_ID_PATTERN.matcher(cleanUrl)
        val youtubeId = if (ytMatcher.find()) ytMatcher.group(1) else null

        // Generate clean title
        val rawFilename = videoResp.filename ?: audioResp?.filename ?: "$platform Media"
        val cleanTitle = ytOEmbed?.title?.ifBlank { null } ?: rawFilename
            .replace(".mp4", "")
            .replace(".mp3", "")
            .replace(".webm", "")
            .replace(Regex("\\([0-9]+p.*\\)"), "")
            .trim()
            .ifBlank { "$platform Video" }

        // Determine Channel / Author Name
        var authorName = when (platform) {
            "YouTube" -> ytOEmbed?.authorName?.ifBlank { null } ?: "YouTube Creator"
            "Instagram" -> {
                val igMatcher = INSTAGRAM_USER_PATTERN.matcher(cleanUrl)
                if (igMatcher.find()) {
                    val user = igMatcher.group(1)
                    if (!user.isNullOrBlank() && user !in listOf("p", "reel", "reels", "tv", "stories")) user else "Instagram Creator"
                } else {
                    "Instagram Creator"
                }
            }
            "Facebook" -> "Facebook Creator"
            else -> "$platform Creator"
        }

        var authorUsername = when (platform) {
            "YouTube" -> {
                ytOEmbed?.authorUrl?.substringAfterLast("/")?.replace("@", "") ?: "youtube"
            }
            "Instagram" -> {
                val igMatcher = INSTAGRAM_USER_PATTERN.matcher(cleanUrl)
                if (igMatcher.find()) {
                    val user = igMatcher.group(1)
                    if (!user.isNullOrBlank() && user !in listOf("p", "reel", "reels", "tv", "stories")) user else "instagram"
                } else {
                    "instagram"
                }
            }
            "Facebook" -> "facebook"
            else -> platform.lowercase()
        }

        // Handle Picker (Slideshow / Carousels / Multi-images) & Single Photos across platforms
        val isPicker = videoResp.status == "picker"
        val pickerItems = videoResp.picker ?: emptyList()
        val pickerPhotos = if (isPicker) {
            pickerItems.filter { it.type == "photo" || it.type == "image" || it.type == null || it.type != "video" }
                .mapNotNull { it.url ?: it.thumb }
        } else {
            emptyList()
        }

        val isSinglePhoto = !isPicker && !videoResp.url.isNullOrBlank() && (
            videoResp.url.contains(Regex("\\.(jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE)) ||
            videoResp.filename?.contains(Regex("\\.(jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE)) == true ||
            (platform == "Instagram" && cleanUrl.contains("/p/") && !videoResp.url.contains(".mp4")) ||
            videoResp.filename?.contains("photo", ignoreCase = true) == true
        )

        var imagesList = when {
            pickerPhotos.isNotEmpty() -> pickerPhotos
            isSinglePhoto -> listOf(videoResp.url!!)
            else -> emptyList()
        }

        // Determine cover image
        val coverUrl = when {
            ytOEmbed?.thumbnailUrl != null -> ytOEmbed.thumbnailUrl
            youtubeId != null -> "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"
            imagesList.isNotEmpty() -> imagesList.first()
            videoResp.status == "picker" -> {
                videoResp.picker?.firstOrNull()?.thumb
                    ?: videoResp.picker?.firstOrNull()?.url
                    ?: ""
            }
            else -> ""
        }

        val mainVideoUrl = when {
            isPicker -> pickerItems.firstOrNull { it.type == "video" }?.url ?: (videoResp.url ?: "")
            imagesList.isNotEmpty() && videoResp.url?.contains(".mp4") == false -> ""
            else -> videoResp.url ?: ""
        }

        // Fallback: Jika tidak ada video dan imagesList kosong, gunakan cover atau url sebagai gambar
        if (mainVideoUrl.isBlank() && imagesList.isEmpty()) {
            if (!videoResp.url.isNullOrBlank()) {
                imagesList = listOf(videoResp.url)
            } else if (coverUrl.isNotBlank()) {
                imagesList = listOf(coverUrl)
            }
        }
        val isImages = imagesList.isNotEmpty()

        val musicUrl = audioResp?.url

        return TikTokMedia(
            id = youtubeId ?: System.currentTimeMillis().toString(),
            title = cleanTitle,
            authorName = authorName,
            authorUsername = authorUsername,
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
            platform = platform,
            description = cleanTitle,
            selectedQuality = "${quality}p"
        )
    }
}
