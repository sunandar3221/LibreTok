package com.libretok.downloader.data.model

enum class DownloadType {
    VIDEO,
    HD_VIDEO,
    AUDIO,
    PHOTO
}

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadItem(
    val id: String,
    val title: String,
    val author: String,
    val thumbnailUri: String?,
    val filePath: String,
    val fileUri: String,
    val type: DownloadType,
    val fileSizeBytes: Long,
    val durationSeconds: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceUrl: String
)

data class ActiveDownload(
    val id: String,
    val title: String,
    val type: DownloadType,
    val progress: Float = 0f, // 0.0 to 1.0
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val errorMessage: String? = null
)
