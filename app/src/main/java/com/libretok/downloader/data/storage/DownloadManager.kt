package com.libretok.downloader.data.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.libretok.downloader.data.model.ActiveDownload
import com.libretok.downloader.data.model.DownloadItem
import com.libretok.downloader.data.model.DownloadStatus
import com.libretok.downloader.data.model.DownloadType
import com.libretok.downloader.data.model.TikTokMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadManager(
    private val context: Context,
    private val historyRepository: HistoryRepository,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
) {

    private val _activeDownloads = MutableStateFlow<Map<String, ActiveDownload>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, ActiveDownload>> = _activeDownloads.asStateFlow()

    private val cancelledTasks = ConcurrentHashMap<String, Boolean>()

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9.\\-_ ]"), "_")
            .take(50)
            .trim()
    }

    suspend fun downloadMedia(
        media: TikTokMedia,
        type: DownloadType,
        customFileName: String? = null
    ): Result<DownloadItem> = withContext(Dispatchers.IO) {
        if (type == DownloadType.PHOTO) {
            val list = if (media.images.isNotEmpty()) media.images else listOf(media.coverUrl)
            val result = downloadPhotos(media, list, customBaseName = customFileName)
            return@withContext if (result.isSuccess && result.getOrNull()?.isNotEmpty() == true) {
                Result.success(result.getOrNull()!!.first())
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Gagal mengunduh foto"))
            }
        }

        val downloadId = "${media.id}_${type.name}_${System.currentTimeMillis()}"

        // Jika adaptive stream (seperti YouTube 1080p/720p/480p), unduh video + audio lalu gabungkan dengan MediaMuxer
        if ((type == DownloadType.VIDEO || type == DownloadType.HD_VIDEO) && media.isAdaptiveStream && !media.musicUrl.isNullOrBlank()) {
            val adaptiveResult = downloadAndMuxAdaptiveMedia(media, type, customFileName, downloadId)
            if (adaptiveResult.isSuccess) {
                return@withContext adaptiveResult
            }
        }

        val candidateUrls = when (type) {
            DownloadType.VIDEO -> {
                val list = mutableListOf<String>()
                if (media.selectedQuality == "1080p" && !media.hdVideoUrl.isNullOrBlank()) {
                    list.add(media.hdVideoUrl)
                }
                if (media.videoUrl.isNotBlank()) list.add(media.videoUrl)
                if (!media.hdVideoUrl.isNullOrBlank()) list.add(media.hdVideoUrl)
                if (!media.previewUrl.isNullOrBlank()) list.add(media.previewUrl)
                if (!media.wmVideoUrl.isNullOrBlank()) list.add(media.wmVideoUrl)
                list.distinct()
            }
            DownloadType.HD_VIDEO -> {
                val list = mutableListOf<String>()
                if (!media.hdVideoUrl.isNullOrBlank()) list.add(media.hdVideoUrl)
                if (media.videoUrl.isNotBlank()) list.add(media.videoUrl)
                if (!media.previewUrl.isNullOrBlank()) list.add(media.previewUrl)
                if (!media.wmVideoUrl.isNullOrBlank()) list.add(media.wmVideoUrl)
                list.distinct()
            }
            DownloadType.AUDIO -> {
                val list = mutableListOf<String>()
                if (!media.musicUrl.isNullOrBlank()) list.add(media.musicUrl)
                if (!media.previewUrl.isNullOrBlank()) list.add(media.previewUrl)
                if (media.videoUrl.isNotBlank()) list.add(media.videoUrl)
                list.distinct()
            }
            DownloadType.PHOTO -> listOfNotNull((media.images.firstOrNull() ?: media.coverUrl).ifBlank { null })
        }

        if (candidateUrls.isEmpty()) {
            return@withContext Result.failure(Exception("URL download tidak valid atau tidak ditemukan."))
        }

        val baseTitle = if (!customFileName.isNullOrBlank()) {
            sanitizeFilename(customFileName)
        } else {
            sanitizeFilename(media.title.ifBlank { "LibreTok_${media.platform}_${media.id}" })
        }
        val (extension, mimeType, subDir, collectionUri) = when (type) {
            DownloadType.VIDEO, DownloadType.HD_VIDEO -> {
                val ext = "mp4"
                val mime = "video/mp4"
                val dir = Environment.DIRECTORY_MOVIES + "/LibreTok"
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                Quadruple(ext, mime, dir, uri)
            }
            DownloadType.AUDIO -> {
                val ext = "mp3"
                val mime = "audio/mpeg"
                val dir = Environment.DIRECTORY_MUSIC + "/LibreTok"
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                Quadruple(ext, mime, dir, uri)
            }
            DownloadType.PHOTO -> {
                val ext = "jpg"
                val mime = "image/jpeg"
                val dir = Environment.DIRECTORY_PICTURES + "/LibreTok"
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                Quadruple(ext, mime, dir, uri)
            }
        }

        val fileName = if (!customFileName.isNullOrBlank()) {
            "${baseTitle}.$extension"
        } else {
            "LibreTok_${baseTitle}_${System.currentTimeMillis()}.$extension"
        }

        updateActiveDownload(
            ActiveDownload(
                id = downloadId,
                title = media.title,
                type = type,
                progress = 0f,
                status = DownloadStatus.DOWNLOADING
            )
        )

        var lastException: Exception? = null

        for (targetUrl in candidateUrls) {
            var savedFile: File? = null
            var savedUri: Uri? = null
            var bytesCopied = 0L

            try {
                val requestBuilder = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")

                // Hanya set Referer sesuai domain asal untuk menghindari penolakan CDN (HTTP 403 / 503)
                val host = try { Uri.parse(targetUrl).host?.lowercase() ?: "" } catch (_: Exception) { "" }
                when {
                    host.contains("tikwm.com") -> requestBuilder.header("Referer", "https://www.tikwm.com/")
                    host.contains("tiktok.com") -> requestBuilder.header("Referer", "https://www.tiktok.com/")
                    host.contains("googlevideo.com") -> requestBuilder.header("User-Agent", "com.google.android.youtube/21.26.364 (Linux; U; Android 11)")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    throw Exception("Server gagal merespon (HTTP ${response.code})")
                }

                val body = response.body ?: throw Exception("Konten unduhan kosong")

                // Validasi tipe konten agar tidak menyimpan HTML/Text sebagai video yang menyebabkan file corrupt
                val contentType = body.contentType()
                val mime = contentType?.let { "${it.type}/${it.subtype}".lowercase() } ?: ""
                if (mime.contains("text") || mime.contains("html") || mime.contains("json")) {
                    throw Exception("Tautan menghasilkan respon HTML/Teks, bukan media valid")
                }

                // Deteksi otomatis jika tautan video ternyata berkas foto atau audio mp4/aac/m4a
                val isActuallyImage = (type == DownloadType.VIDEO || type == DownloadType.HD_VIDEO) && mime.startsWith("image/")
                val isActuallyM4a = type == DownloadType.AUDIO && (mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac"))
                val effectiveExt = when {
                    isActuallyImage -> "jpg"
                    isActuallyM4a -> "m4a"
                    else -> extension
                }
                val effectiveMime = when {
                    isActuallyImage -> "image/jpeg"
                    isActuallyM4a -> "audio/mp4"
                    else -> mimeType
                }
                val effectiveSubDir = if (isActuallyImage) (Environment.DIRECTORY_PICTURES + "/LibreTok") else subDir
                val effectiveCollectionUri = if (isActuallyImage) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                } else collectionUri
                val effectiveFileName = when {
                    isActuallyImage -> fileName.replace(".mp4", ".jpg")
                    isActuallyM4a -> fileName.replace(".mp3", ".m4a")
                    else -> fileName
                }
                val effectiveType = if (isActuallyImage) DownloadType.PHOTO else type

                val expectedTotalBytes = body.contentLength()

                bytesCopied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, effectiveFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, effectiveMime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, effectiveSubDir)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val uri = context.contentResolver.insert(effectiveCollectionUri, contentValues)
                        ?: throw Exception("Gagal membuat entri penyimpanan MediaStore")
                    savedUri = uri

                    val copied = context.contentResolver.openOutputStream(uri).use { outStream ->
                        if (outStream == null) throw Exception("Gagal membuka stream output MediaStore")
                        copyStream(downloadId, body.byteStream(), outStream, expectedTotalBytes) { progress, c ->
                            updateActiveDownload(
                                ActiveDownload(
                                    id = downloadId,
                                    title = media.title,
                                    type = effectiveType,
                                    progress = progress,
                                    bytesDownloaded = c,
                                    totalBytes = if (expectedTotalBytes > 0) expectedTotalBytes else c,
                                    status = DownloadStatus.DOWNLOADING
                                )
                            )
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)

                    val fallbackFile = File(
                        Environment.getExternalStoragePublicDirectory(
                            if (effectiveType == DownloadType.AUDIO) Environment.DIRECTORY_MUSIC
                            else if (effectiveType == DownloadType.PHOTO) Environment.DIRECTORY_PICTURES
                            else Environment.DIRECTORY_MOVIES
                        ),
                        "LibreTok/$effectiveFileName"
                    )
                    savedFile = fallbackFile
                    copied

                } else {
                    val publicDir = File(
                        Environment.getExternalStoragePublicDirectory(
                            if (effectiveType == DownloadType.AUDIO) Environment.DIRECTORY_MUSIC
                            else if (effectiveType == DownloadType.PHOTO) Environment.DIRECTORY_PICTURES
                            else Environment.DIRECTORY_MOVIES
                        ),
                        "LibreTok"
                    )
                    if (!publicDir.exists()) publicDir.mkdirs()

                    val file = File(publicDir, effectiveFileName)
                    savedFile = file

                    val copied = FileOutputStream(file).use { outStream ->
                        copyStream(downloadId, body.byteStream(), outStream, expectedTotalBytes) { progress, c ->
                            updateActiveDownload(
                                ActiveDownload(
                                    id = downloadId,
                                    title = media.title,
                                    type = effectiveType,
                                    progress = progress,
                                    bytesDownloaded = c,
                                    totalBytes = if (expectedTotalBytes > 0) expectedTotalBytes else c,
                                    status = DownloadStatus.DOWNLOADING
                                )
                            )
                        }
                    }

                    savedUri = Uri.fromFile(file)
                    copied
                }

                if (bytesCopied <= 0L) {
                    throw Exception("Kualitas video yang dipilih tidak tersedia di server atau berkas kosong (0 bytes diterima).")
                }

                // Check if cancelled
                if (cancelledTasks[downloadId] == true) {
                    cancelledTasks.remove(downloadId)
                    removeActiveDownload(downloadId)
                    savedUri?.let { uri ->
                        try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                    }
                    savedFile?.let { if (it.exists()) it.delete() }
                    return@withContext Result.failure(Exception("Unduhan dibatalkan"))
                }

                val downloadItem = DownloadItem(
                    id = downloadId,
                    title = media.title,
                    author = "${media.authorName} (@${media.authorUsername})",
                    thumbnailUri = media.coverUrl,
                    filePath = savedFile.absolutePath,
                    fileUri = savedUri?.toString() ?: "",
                    type = effectiveType,
                    fileSizeBytes = bytesCopied,
                    durationSeconds = media.duration,
                    timestamp = System.currentTimeMillis(),
                    sourceUrl = media.originalUrl
                )

                historyRepository.addItem(downloadItem)
                removeActiveDownload(downloadId)

                return@withContext Result.success(downloadItem)

            } catch (e: Exception) {
                lastException = e
                // Bersihkan berkas corrupt dari penyimpanan agar tidak merusak galeri
                savedUri?.let { uri ->
                    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }
                savedFile?.let { file ->
                    try { if (file.exists()) file.delete() } catch (_: Exception) {}
                }
                // Jika masih ada candidate URL lain, loop akan mencoba URL berikutnya
            }
        }

        // Jika semua kandidat gagal
        updateActiveDownload(
            ActiveDownload(
                id = downloadId,
                title = media.title,
                type = type,
                progress = 0f,
                status = DownloadStatus.FAILED,
                errorMessage = lastException?.localizedMessage ?: "Terjadi kesalahan saat mengunduh"
            )
        )
        Result.failure(lastException ?: Exception("Gagal mengunduh berkas"))
    }

    private suspend fun downloadAndMuxAdaptiveMedia(
        media: TikTokMedia,
        type: DownloadType,
        customFileName: String?,
        downloadId: String
    ): Result<DownloadItem> = withContext(Dispatchers.IO) {
        val targetVideoUrl = if (type == DownloadType.HD_VIDEO && !media.hdVideoUrl.isNullOrBlank()) {
            media.hdVideoUrl
        } else {
            media.videoUrl
        }
        val targetAudioUrl = media.musicUrl ?: return@withContext Result.failure(Exception("Audio URL tidak ditemukan"))

        val baseTitle = if (!customFileName.isNullOrBlank()) {
            sanitizeFilename(customFileName)
        } else {
            sanitizeFilename(media.title.ifBlank { "LibreTok_${media.platform}_${media.id}" })
        }
        val fileName = if (!customFileName.isNullOrBlank()) {
            "${baseTitle}.mp4"
        } else {
            "LibreTok_${baseTitle}_${System.currentTimeMillis()}.mp4"
        }

        updateActiveDownload(
            ActiveDownload(
                id = downloadId,
                title = media.title,
                type = type,
                progress = 0f,
                status = DownloadStatus.DOWNLOADING
            )
        )

        var tempVideo: File? = null
        var tempAudio: File? = null
        var tempMuxed: File? = null
        var savedFile: File? = null
        var savedUri: Uri? = null

        try {
            tempVideo = File(context.cacheDir, "temp_v_${System.currentTimeMillis()}.mp4")
            tempAudio = File(context.cacheDir, "temp_a_${System.currentTimeMillis()}.m4a")
            tempMuxed = File(context.cacheDir, "temp_m_${System.currentTimeMillis()}.mp4")

            // 1. Unduh track video berkualitas tinggi (0% -> 85%)
            downloadTrackToFile(
                downloadId = downloadId,
                url = targetVideoUrl,
                destinationFile = tempVideo,
                mediaTitle = media.title,
                type = type,
                progressBase = 0f,
                progressScale = 0.85f,
                totalBytesEstimated = if (media.sizeBytes > 0) (media.sizeBytes * 85 / 100) else 15_000_000L
            )

            if (cancelledTasks[downloadId] == true) {
                throw Exception("Unduhan dibatalkan")
            }

            // 2. Unduh track audio AAC jernih (85% -> 95%)
            downloadTrackToFile(
                downloadId = downloadId,
                url = targetAudioUrl,
                destinationFile = tempAudio,
                mediaTitle = media.title,
                type = type,
                progressBase = 0.85f,
                progressScale = 0.10f,
                totalBytesEstimated = if (media.sizeBytes > 0) (media.sizeBytes * 15 / 100) else 1_500_000L
            )

            if (cancelledTasks[downloadId] == true) {
                throw Exception("Unduhan dibatalkan")
            }

            // 3. Muxing video + audio menjadi satu file MP4 (95% -> 99%)
            updateActiveDownload(
                ActiveDownload(
                    id = downloadId,
                    title = media.title,
                    type = type,
                    progress = 0.96f,
                    status = DownloadStatus.DOWNLOADING
                )
            )

            muxVideoAndAudio(tempVideo, tempAudio, tempMuxed)

            if (!tempMuxed.exists() || tempMuxed.length() == 0L) {
                throw Exception("Gagal menggabungkan berkas video dan audio")
            }

            val finalLength = tempMuxed.length()

            // 4. Simpan hasil muxing ke penyimpanan publik (Movies/LibreTok)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LibreTok")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    contentValues
                ) ?: throw Exception("Gagal membuat entri penyimpanan MediaStore")
                savedUri = uri

                context.contentResolver.openOutputStream(uri).use { outStream ->
                    if (outStream == null) throw Exception("Gagal membuka stream output MediaStore")
                    FileInputStream(tempMuxed).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                val fallbackFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "LibreTok/$fileName"
                )
                savedFile = fallbackFile
            } else {
                val publicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "LibreTok"
                )
                if (!publicDir.exists()) publicDir.mkdirs()

                val file = File(publicDir, fileName)
                savedFile = file
                FileInputStream(tempMuxed).use { inStream ->
                    FileOutputStream(file).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                savedUri = Uri.fromFile(file)
            }

            updateActiveDownload(
                ActiveDownload(
                    id = downloadId,
                    title = media.title,
                    type = type,
                    progress = 1f,
                    bytesDownloaded = finalLength,
                    totalBytes = finalLength,
                    status = DownloadStatus.COMPLETED
                )
            )

            val downloadItem = DownloadItem(
                id = downloadId,
                title = media.title,
                author = "${media.authorName} (@${media.authorUsername})",
                thumbnailUri = media.coverUrl,
                filePath = savedFile.absolutePath,
                fileUri = savedUri?.toString() ?: "",
                type = type,
                fileSizeBytes = finalLength,
                durationSeconds = media.duration,
                timestamp = System.currentTimeMillis(),
                sourceUrl = media.originalUrl
            )

            historyRepository.addItem(downloadItem)
            removeActiveDownload(downloadId)
            Result.success(downloadItem)
        } catch (e: Exception) {
            updateActiveDownload(
                ActiveDownload(
                    id = downloadId,
                    title = media.title,
                    type = type,
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Gagal mengunduh media"
                )
            )
            savedUri?.let { uri ->
                try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            }
            savedFile?.let { if (it.exists()) it.delete() }
            Result.failure(e)
        } finally {
            try { tempVideo?.delete() } catch (_: Exception) {}
            try { tempAudio?.delete() } catch (_: Exception) {}
            try { tempMuxed?.delete() } catch (_: Exception) {}
        }
    }

    private fun downloadTrackToFile(
        downloadId: String,
        url: String,
        destinationFile: File,
        mediaTitle: String,
        type: DownloadType,
        progressBase: Float,
        progressScale: Float,
        totalBytesEstimated: Long,
        onBytesCopied: (Long) -> Unit = {}
    ) {
        val isGoogleVideo = url.contains("googlevideo.com")
        val ua = if (isGoogleVideo) {
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X; en_US)"
        } else {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        if (!isGoogleVideo) {
            val req = Request.Builder().url(url).header("User-Agent", ua).header("Accept", "*/*").build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) throw Exception("Server menolak unduhan track (HTTP ${resp.code})")
            val body = resp.body ?: throw Exception("Konten track kosong")
            val total = body.contentLength()
            FileOutputStream(destinationFile).use { outStream ->
                copyStream(downloadId, body.byteStream(), outStream, total) { prog, copied ->
                    onBytesCopied(copied)
                    updateActiveDownload(
                        ActiveDownload(
                            id = downloadId,
                            title = mediaTitle,
                            type = type,
                            progress = progressBase + (prog * progressScale),
                            bytesDownloaded = copied,
                            totalBytes = if (total > 0) total else copied,
                            status = DownloadStatus.DOWNLOADING
                        )
                    )
                }
            }
            return
        }

        // Chunked Range downloader untuk GoogleVideo adaptive streams (mencegah HTTP 403)
        var downloaded = 0L
        val maxChunk = 1024 * 1024L // 1 MB per chunk
        var currChunk = maxChunk
        val totalExpected = if (totalBytesEstimated > 0) totalBytesEstimated else 10_000_000L

        FileOutputStream(destinationFile).use { outStream ->
            while (true) {
                if (cancelledTasks[downloadId] == true) {
                    throw Exception("Unduhan dibatalkan")
                }

                val rangeHeader = "bytes=$downloaded-${downloaded + currChunk - 1}"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", ua)
                    .header("Range", rangeHeader)
                    .header("Accept", "*/*")
                    .build()

                try {
                    val resp = client.newCall(req).execute()
                    if (resp.code == 206) {
                        val body = resp.body ?: break
                        val bytes = body.bytes()
                        if (bytes.isEmpty()) break
                        outStream.write(bytes)
                        downloaded += bytes.size
                        onBytesCopied(downloaded)

                        val prog = (downloaded.toFloat() / totalExpected.toFloat()).coerceIn(0f, 1f)
                        updateActiveDownload(
                            ActiveDownload(
                                id = downloadId,
                                title = mediaTitle,
                                type = type,
                                progress = progressBase + (prog * progressScale),
                                bytesDownloaded = downloaded,
                                totalBytes = maxOf(totalExpected, downloaded),
                                status = DownloadStatus.DOWNLOADING
                            )
                        )

                        if (currChunk < maxChunk) {
                            currChunk = minOf(currChunk * 2, maxChunk)
                        }

                        if (bytes.size < currChunk) {
                            break
                        }
                    } else if (resp.code == 403 || resp.code == 416) {
                        if (currChunk > 8192) {
                            currChunk /= 2
                        } else if (downloaded > 0) {
                            break
                        } else {
                            throw Exception("Server menolak akses track GoogleVideo (HTTP ${resp.code})")
                        }
                    } else {
                        if (downloaded > 0) break
                        throw Exception("Gagal mengunduh track GoogleVideo (HTTP ${resp.code})")
                    }
                } catch (e: Exception) {
                    if (cancelledTasks[downloadId] == true) throw e
                    if (downloaded > 0) break
                    throw e
                }
            }
        }

        if (downloaded <= 0L) {
            throw Exception("Track GoogleVideo kosong (0 bytes diterima)")
        }
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File) {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoTrackIndex = -1
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    videoTrackIndex = muxer.addTrack(format)
                    break
                }
            }

            var audioTrackIndex = -1
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    audioTrackIndex = muxer.addTrack(format)
                    break
                }
            }

            if (videoTrackIndex == -1) {
                throw Exception("Track video tidak ditemukan dalam berkas")
            }

            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                videoExtractor.advance()
            }

            if (audioTrackIndex != -1) {
                while (true) {
                    val sampleSize = audioExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                    audioExtractor.advance()
                }
            }
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
        }
    }

    suspend fun downloadPhotos(
        media: TikTokMedia,
        photoUrls: List<String>,
        customBaseName: String? = null
    ): Result<List<DownloadItem>> = withContext(Dispatchers.IO) {
        val urlsToDownload = photoUrls.filter { it.isNotBlank() }
        if (urlsToDownload.isEmpty()) {
            return@withContext Result.failure(Exception("Tidak ada foto yang valid untuk diunduh."))
        }

        val downloadId = "${media.id}_PHOTO_${System.currentTimeMillis()}"
        val baseTitle = if (!customBaseName.isNullOrBlank()) {
            sanitizeFilename(customBaseName)
        } else {
            sanitizeFilename(media.title.ifBlank { "LibreTok_${media.platform}_${media.id}" })
        }
        val totalCount = urlsToDownload.size

        updateActiveDownload(
            ActiveDownload(
                id = downloadId,
                title = "${media.title} (0/$totalCount Foto)",
                type = DownloadType.PHOTO,
                progress = 0f,
                bytesDownloaded = 0L,
                totalBytes = totalCount.toLong(),
                status = DownloadStatus.DOWNLOADING
            )
        )

        val downloadedItems = mutableListOf<DownloadItem>()

        try {
            val ext = "jpg"
            val mimeType = "image/jpeg"
            val subDir = Environment.DIRECTORY_PICTURES + "/LibreTok"
            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            for ((index, photoUrl) in urlsToDownload.withIndex()) {
                if (cancelledTasks[downloadId] == true) {
                    cancelledTasks.remove(downloadId)
                    removeActiveDownload(downloadId)
                    return@withContext Result.failure(Exception("Unduhan foto dibatalkan"))
                }

                val fileName = "LibreTok_${baseTitle}_${index + 1}_of_${totalCount}_${System.currentTimeMillis()}.$ext"
                val request = Request.Builder()
                    .url(photoUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "image/*,*/*")
                    .build()

                var savedFile: File? = null
                var savedUri: Uri? = null
                var bytesCopied = 0L

                try {
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) continue

                    val body = response.body ?: continue
                    val expectedBytes = body.contentLength()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, subDir)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }

                        val uri = context.contentResolver.insert(collectionUri, contentValues)
                            ?: continue
                        savedUri = uri

                        val copied = context.contentResolver.openOutputStream(uri)?.use { outStream ->
                            copyStream(downloadId, body.byteStream(), outStream, expectedBytes) { _, _ -> }
                        } ?: 0L
                        bytesCopied = copied

                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        context.contentResolver.update(uri, contentValues, null, null)

                        savedFile = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "LibreTok/$fileName"
                        )
                    } else {
                        val publicDir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "LibreTok"
                        )
                        if (!publicDir.exists()) publicDir.mkdirs()
                        val file = File(publicDir, fileName)
                        savedFile = file

                        val copied = FileOutputStream(file).use { outStream ->
                            copyStream(downloadId, body.byteStream(), outStream, expectedBytes) { _, _ -> }
                        }
                        bytesCopied = copied

                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DATA, savedFile.absolutePath)
                            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        }
                        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        savedUri = Uri.fromFile(savedFile)
                    }

                    if (bytesCopied <= 0L) {
                        throw Exception("Foto tidak terunduh")
                    }

                    val downloadItem = DownloadItem(
                        id = "${downloadId}_$index",
                        title = if (totalCount > 1) "${media.title} (${index + 1}/$totalCount)" else media.title,
                        author = "${media.authorName} (@${media.authorUsername})",
                        thumbnailUri = photoUrl,
                        filePath = savedFile.absolutePath,
                        fileUri = savedUri.toString(),
                        type = DownloadType.PHOTO,
                        fileSizeBytes = bytesCopied,
                        durationSeconds = 0,
                        timestamp = System.currentTimeMillis(),
                        sourceUrl = media.originalUrl
                    )

                    historyRepository.addItem(downloadItem)
                    downloadedItems.add(downloadItem)

                } catch (e: Exception) {
                    // Bersihkan jika foto corrupt
                    savedUri?.let { uri ->
                        try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                    }
                    savedFile?.let { file ->
                        try { if (file.exists()) file.delete() } catch (_: Exception) {}
                    }
                }

                val progress = (index + 1).toFloat() / totalCount
                updateActiveDownload(
                    ActiveDownload(
                        id = downloadId,
                        title = "${media.title} (${index + 1}/$totalCount Foto)",
                        type = DownloadType.PHOTO,
                        progress = progress,
                        bytesDownloaded = (index + 1).toLong(),
                        totalBytes = totalCount.toLong(),
                        status = DownloadStatus.DOWNLOADING
                    )
                )
            }

            removeActiveDownload(downloadId)

            if (downloadedItems.isEmpty()) {
                Result.failure(Exception("Gagal mengunduh foto"))
            } else {
                Result.success(downloadedItems)
            }
        } catch (e: Exception) {
            removeActiveDownload(downloadId)
            Result.failure(e)
        }
    }

    private fun copyStream(
        downloadId: String,
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: (progress: Float, copied: Long) -> Unit
    ): Long {
        val buffer = ByteArray(32 * 1024)
        var totalCopied = 0L
        var read: Int
        var lastUpdate = 0L

        input.use { inStream ->
            while (inStream.read(buffer).also { read = it } != -1) {
                if (cancelledTasks[downloadId] == true) {
                    throw Exception("Unduhan dibatalkan")
                }
                output.write(buffer, 0, read)
                totalCopied += read

                val now = System.currentTimeMillis()
                if (now - lastUpdate > 200) {
                    lastUpdate = now
                    val progress = if (totalBytes > 0) (totalCopied.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                    onProgress(progress, totalCopied)
                }
            }
            output.flush()
        }

        val finalProgress = if (totalBytes > 0) 1f else 0f
        onProgress(finalProgress, totalCopied)
        return totalCopied
    }

    fun cancelDownload(downloadId: String) {
        cancelledTasks[downloadId] = true
        removeActiveDownload(downloadId)
    }

    private fun updateActiveDownload(download: ActiveDownload) {
        val current = _activeDownloads.value.toMutableMap()
        current[download.id] = download
        _activeDownloads.value = current
    }

    fun removeActiveDownload(id: String) {
        val current = _activeDownloads.value.toMutableMap()
        current.remove(id)
        _activeDownloads.value = current
    }

    fun shareFile(item: DownloadItem) {
        try {
            val file = File(item.filePath)
            val uri: Uri = if (file.exists()) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            } else if (!item.fileUri.isBlank()) {
                Uri.parse(item.fileUri)
            } else return

            val mimeType = when (item.type) {
                DownloadType.VIDEO, DownloadType.HD_VIDEO -> "video/*"
                DownloadType.AUDIO -> "audio/*"
                DownloadType.PHOTO -> "image/*"
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                this.type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "${item.title} - Downloaded via LibreTok")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan berkas via...").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openFile(item: DownloadItem) {
        try {
            val file = File(item.filePath)
            val uri: Uri = if (file.exists()) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            } else if (!item.fileUri.isBlank()) {
                Uri.parse(item.fileUri)
            } else return

            val mimeType = when (item.type) {
                DownloadType.VIDEO, DownloadType.HD_VIDEO -> "video/*"
                DownloadType.AUDIO -> "audio/*"
                DownloadType.PHOTO -> "image/*"
            }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
