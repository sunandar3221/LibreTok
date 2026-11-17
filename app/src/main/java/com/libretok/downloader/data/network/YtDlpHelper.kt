package com.libretok.downloader.data.network

import android.content.Context
import android.util.Log
import com.libretok.downloader.data.model.TikTokMedia
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YtDlpHelper {
    private const val TAG = "YtDlpHelper"

    @Volatile
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        try {
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            isInitialized = true
            logD(TAG, "YoutubeDL and FFmpeg initialized successfully. Version: ${getVersion(context)}")
        } catch (e: Exception) {
            logE(TAG, "Failed to initialize YoutubeDL / FFmpeg", e)
        }
    }

    fun isReady(): Boolean = isInitialized

    fun getVersion(context: Context): String {
        return try {
            YoutubeDL.getInstance().version(context.applicationContext) ?: "Unknown"
        } catch (_: Exception) {
            "Not Available"
        }
    }

    suspend fun update(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)
            val currentVersion = getVersion(context)
            Result.success("Pembaruan yt-dlp: $status (Versi: $currentVersion)")
        } catch (e: Exception) {
            logE(TAG, "Failed to update YoutubeDL", e)
            Result.failure(Exception("Gagal memperbarui yt-dlp: ${e.message}"))
        }
    }

    suspend fun fetchMedia(url: String): Result<TikTokMedia> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--socket-timeout", "20")
            }

            val info = YoutubeDL.getInstance().getInfo(request)
            val id = info.id ?: System.currentTimeMillis().toString()
            val title = info.title ?: "video_$id"
            val author = info.uploader ?: ""
            val cover = info.thumbnail ?: ""
            val duration = info.duration

            // Format resolusi dari info.formats
            val heights = info.formats?.mapNotNull { f ->
                if (f.height > 0) f.height else null
            }?.distinct()?.sortedDescending() ?: emptyList()

            val availableResolutions = if (heights.isNotEmpty()) {
                heights.map { "${it}p" }
            } else {
                listOf("1080p", "720p", "480p", "360p")
            }

            val defaultQuality = availableResolutions.firstOrNull { it == "1080p" }
                ?: availableResolutions.firstOrNull()
                ?: "1080p"

            val media = TikTokMedia(
                id = id,
                title = title,
                authorName = author,
                authorUsername = author,
                authorAvatar = cover,
                coverUrl = cover,
                videoUrl = url,
                hdVideoUrl = url,
                musicUrl = url,
                musicTitle = title,
                duration = duration,
                sizeBytes = if (info.fileSizeApproximate > 0) info.fileSizeApproximate else info.fileSize,
                isAdaptiveStream = true,
                originalUrl = url,
                platform = info.extractor ?: "Video",
                availableResolutions = availableResolutions,
                selectedQuality = defaultQuality
            )
            Result.success(media)
        } catch (e: Exception) {
            logE(TAG, "fetchMedia error via yt-dlp", e)
            Result.failure(Exception("Gagal mengekstrak media: ${e.message ?: "Kesalahan tidak diketahui"}"))
        }
    }

    private fun logE(tag: String, msg: String, tr: Throwable? = null) {
        try {
            Log.e(tag, msg, tr)
        } catch (_: Throwable) {
            System.err.println("[$tag] ERROR: $msg ${tr?.message ?: ""}")
        }
    }

    private fun logD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] DEBUG: $msg")
        }
    }
}
