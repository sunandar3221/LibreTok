package com.libretok.downloader.data.model

enum class BatchItemStatus {
    WAITING,
    FETCHING,
    DOWNLOADING,
    SUCCESS,
    FAILED
}

data class BatchDownloadItem(
    val url: String,
    val status: BatchItemStatus = BatchItemStatus.WAITING,
    val title: String = "",
    val progress: Float = 0f,
    val errorMessage: String? = null
)
