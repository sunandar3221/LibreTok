package com.libretok.downloader.data.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
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
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class DownloadManager(
    private val context: Context,
    private val historyRepository: HistoryRepository,
    private val client: OkHttpClient = OkHttpClient()
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
        type: DownloadType
    ): Result<DownloadItem> = withContext(Dispatchers.IO) {
        if (type == DownloadType.PHOTO) {
            val list = if (media.images.isNotEmpty()) media.images else listOf(media.coverUrl)
            val result = downloadPhotos(media, list)
            return@withContext if (result.isSuccess && result.getOrNull()?.isNotEmpty() == true) {
                Result.success(result.getOrNull()!!.first())
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Gagal mengunduh foto"))
            }
        }

        val downloadId = "${media.id}_${type.name}_${System.currentTimeMillis()}"
        val targetUrl = when (type) {
            DownloadType.VIDEO -> media.videoUrl
            DownloadType.HD_VIDEO -> media.hdVideoUrl ?: media.videoUrl
            DownloadType.AUDIO -> media.musicUrl ?: return@withContext Result.failure(Exception("Audio URL tidak ditemukan"))
            DownloadType.PHOTO -> media.images.firstOrNull() ?: media.coverUrl
        }

        if (targetUrl.isBlank()) {
            return@withContext Result.failure(Exception("URL download tidak valid."))
        }

        val baseTitle = sanitizeFilename(media.title.ifBlank { "TikTok_${media.id}" })
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

        val fileName = "LibreTok_${baseTitle}_${System.currentTimeMillis()}.$extension"

        updateActiveDownload(
            ActiveDownload(
                id = downloadId,
                title = media.title,
                type = type,
                progress = 0f,
                status = DownloadStatus.DOWNLOADING
            )
        )

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://www.tiktok.com/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Gagal mengunduh berkas (HTTP ${response.code})")
            }

            val body = response.body ?: throw Exception("Konten unduhan kosong")
            val totalBytes = body.contentLength().coerceAtLeast(1L)
            var bytesCopied = 0L

            var savedFile: File?
            val savedUri: Uri?

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, subDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(collectionUri, contentValues)
                    ?: throw Exception("Gagal membuat entri penyimpanan MediaStore")
                savedUri = uri

                context.contentResolver.openOutputStream(uri).use { outStream ->
                    if (outStream == null) throw Exception("Gagal membuka stream output MediaStore")
                    copyStream(downloadId, body.byteStream(), outStream, totalBytes) { progress, copied ->
                        bytesCopied = copied
                        updateActiveDownload(
                            ActiveDownload(
                                id = downloadId,
                                title = media.title,
                                type = type,
                                progress = progress,
                                bytesDownloaded = copied,
                                totalBytes = totalBytes,
                                status = DownloadStatus.DOWNLOADING
                            )
                        )
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                // Try to get actual file path or fallback to Movies/LibreTok/fileName
                val fallbackFile = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (type == DownloadType.AUDIO) Environment.DIRECTORY_MUSIC
                        else if (type == DownloadType.PHOTO) Environment.DIRECTORY_PICTURES
                        else Environment.DIRECTORY_MOVIES
                    ),
                    "LibreTok/$fileName"
                )
                savedFile = fallbackFile

            } else {
                val publicDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (type == DownloadType.AUDIO) Environment.DIRECTORY_MUSIC
                        else if (type == DownloadType.PHOTO) Environment.DIRECTORY_PICTURES
                        else Environment.DIRECTORY_MOVIES
                    ),
                    "LibreTok"
                )
                if (!publicDir.exists()) publicDir.mkdirs()

                val file = File(publicDir, fileName)
                savedFile = file

                FileOutputStream(file).use { outStream ->
                    copyStream(downloadId, body.byteStream(), outStream, totalBytes) { progress, copied ->
                        bytesCopied = copied
                        updateActiveDownload(
                            ActiveDownload(
                                id = downloadId,
                                title = media.title,
                                type = type,
                                progress = progress,
                                bytesDownloaded = copied,
                                totalBytes = totalBytes,
                                status = DownloadStatus.DOWNLOADING
                            )
                        )
                    }
                }

                savedUri = Uri.fromFile(file)
            }

            // Check if cancelled during download
            if (cancelledTasks[downloadId] == true) {
                cancelledTasks.remove(downloadId)
                removeActiveDownload(downloadId)
                savedFile.delete()
                return@withContext Result.failure(Exception("Unduhan dibatalkan"))
            }

            val downloadItem = DownloadItem(
                id = downloadId,
                title = media.title,
                author = "${media.authorName} (@${media.authorUsername})",
                thumbnailUri = media.coverUrl,
                filePath = savedFile.absolutePath,
                fileUri = savedUri?.toString() ?: "",
                type = type,
                fileSizeBytes = if (bytesCopied > 0) bytesCopied else totalBytes,
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
                    progress = 0f,
                    status = DownloadStatus.FAILED,
                    errorMessage = e.localizedMessage ?: "Terjadi kesalahan saat mengunduh"
                )
            )
            Result.failure(e)
        }
    }

    suspend fun downloadPhotos(
        media: TikTokMedia,
        photoUrls: List<String>
    ): Result<List<DownloadItem>> = withContext(Dispatchers.IO) {
        val urlsToDownload = photoUrls.filter { it.isNotBlank() }
        if (urlsToDownload.isEmpty()) {
            return@withContext Result.failure(Exception("Tidak ada foto yang valid untuk diunduh."))
        }

        val downloadId = "${media.id}_PHOTO_${System.currentTimeMillis()}"
        val baseTitle = sanitizeFilename(media.title.ifBlank { "${media.platform}_${media.id}" })
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
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", media.originalUrl)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    continue
                }

                val body = response.body ?: continue
                val totalBytes = body.contentLength().coerceAtLeast(1L)
                var bytesCopied = 0L

                val savedFile: File
                val savedUri: Uri?

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

                    context.contentResolver.openOutputStream(uri).use { outStream ->
                        if (outStream != null) {
                            val buffer = ByteArray(8192)
                            var read: Int
                            val inStream = body.byteStream()
                            while (inStream.read(buffer).also { read = it } != -1) {
                                if (cancelledTasks[downloadId] == true) {
                                    break
                                }
                                outStream.write(buffer, 0, read)
                                bytesCopied += read
                            }
                            outStream.flush()
                        }
                    }

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
                    savedFile = File(publicDir, fileName)
                    savedFile.outputStream().use { outStream ->
                        body.byteStream().copyTo(outStream)
                    }
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DATA, savedFile.absolutePath)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    }
                    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    savedUri = Uri.fromFile(savedFile)
                }

                val downloadItem = DownloadItem(
                    id = "${downloadId}_$index",
                    title = if (totalCount > 1) "${media.title} (${index + 1}/$totalCount)" else media.title,
                    author = "${media.authorName} (@${media.authorUsername})",
                    thumbnailUri = photoUrl,
                    filePath = savedFile.absolutePath,
                    fileUri = savedUri?.toString() ?: "",
                    type = DownloadType.PHOTO,
                    fileSizeBytes = if (bytesCopied > 0) bytesCopied else totalBytes,
                    durationSeconds = 0,
                    timestamp = System.currentTimeMillis(),
                    sourceUrl = media.originalUrl
                )

                historyRepository.addItem(downloadItem)
                downloadedItems.add(downloadItem)

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
    ) {
        val buffer = ByteArray(8192)
        var bytesCopied = 0L
        var read: Int
        var lastUpdate = System.currentTimeMillis()

        input.use { inStream ->
            output.use { outStream ->
                while (inStream.read(buffer).also { read = it } != -1) {
                    if (cancelledTasks[downloadId] == true) {
                        return
                    }
                    outStream.write(buffer, 0, read)
                    bytesCopied += read

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 250 || bytesCopied == totalBytes) {
                        lastUpdate = now
                        val progress = if (totalBytes > 0) (bytesCopied.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                        onProgress(progress, bytesCopied)
                    }
                }
                outStream.flush()
            }
        }
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

    /**
     * Share file with other applications (WhatsApp, Telegram, etc.)
     */
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

    /**
     * Open media file in default system player or viewer
     */
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
