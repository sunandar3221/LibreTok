package com.libretok.downloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.libretok.downloader.data.model.*
import com.libretok.downloader.data.network.AppUpdateInfo
import com.libretok.downloader.data.network.NetworkMonitor
import com.libretok.downloader.data.network.TikTokApiService
import com.libretok.downloader.data.network.UpdateChecker
import com.libretok.downloader.data.storage.DownloadManager
import com.libretok.downloader.data.storage.HistoryRepository
import com.libretok.downloader.data.storage.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class HistoryFilter {
    ALL,
    VIDEOS,
    AUDIOS,
    PHOTOS
}

sealed class PlayerTarget {
    data class StreamPreview(val media: TikTokMedia, val isAudioOnly: Boolean = false) : PlayerTarget()
    data class LocalFile(val item: DownloadItem) : PlayerTarget()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = TikTokApiService()
    private val historyRepository = HistoryRepository(application)
    val downloadManager = DownloadManager(application, historyRepository)
    val settingsManager = SettingsManager(application)
    private val updateChecker = UpdateChecker()
    val networkMonitor = NetworkMonitor(application)

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), networkMonitor.isCurrentlyOnline())

    fun refreshNetworkStatus() {
        val online = networkMonitor.isCurrentlyOnline()
        if (online) {
            if (_errorMessage.value?.contains("koneksi", ignoreCase = true) == true) {
                _errorMessage.value = null
            }
        }
    }

    // UI States
    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _mediaPreview = MutableStateFlow<TikTokMedia?>(null)
    val mediaPreview: StateFlow<TikTokMedia?> = _mediaPreview.asStateFlow()

    private val _clipboardDetectedUrl = MutableStateFlow<String?>(null)
    val clipboardDetectedUrl: StateFlow<String?> = _clipboardDetectedUrl.asStateFlow()

    private val _selectedFilter = MutableStateFlow(HistoryFilter.ALL)
    val selectedFilter: StateFlow<HistoryFilter> = _selectedFilter.asStateFlow()

    private val _activePlayerTarget = MutableStateFlow<PlayerTarget?>(null)
    val activePlayerTarget: StateFlow<PlayerTarget?> = _activePlayerTarget.asStateFlow()

    private val _downloadFinishedItem = MutableStateFlow<DownloadItem?>(null)
    val downloadFinishedItem: StateFlow<DownloadItem?> = _downloadFinishedItem.asStateFlow()

    val downloadsHistory: StateFlow<List<DownloadItem>> = historyRepository.items

    val activeDownloads: StateFlow<Map<String, ActiveDownload>> = downloadManager.activeDownloads

    // In-App Update States
    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateCheckMessage = MutableStateFlow<String?>(null)
    val updateCheckMessage: StateFlow<String?> = _updateCheckMessage.asStateFlow()

    // Batch Download States
    private val _batchQueue = MutableStateFlow<List<BatchDownloadItem>>(emptyList())
    val batchQueue: StateFlow<List<BatchDownloadItem>> = _batchQueue.asStateFlow()

    private val _isBatchRunning = MutableStateFlow(false)
    val isBatchRunning: StateFlow<Boolean> = _isBatchRunning.asStateFlow()

    private var batchJob: Job? = null

    init {
        // Cek update otomatis saat aplikasi dibuka
        checkForUpdate(isManual = false)
    }

    fun onUrlChange(text: String) {
        _urlInput.value = text
        _errorMessage.value = null
    }

    fun clearUrl() {
        _urlInput.value = ""
        _mediaPreview.value = null
        _errorMessage.value = null
    }

    fun checkClipboard(clipText: String?) {
        if (clipText.isNullOrBlank()) return
        val extracted = apiService.extractUrl(clipText)
        if (extracted != null && extracted != _urlInput.value && extracted != _mediaPreview.value?.originalUrl) {
            _clipboardDetectedUrl.value = extracted
        }
    }

    fun dismissClipboardBanner() {
        _clipboardDetectedUrl.value = null
    }

    fun applyDetectedUrl() {
        val url = _clipboardDetectedUrl.value ?: return
        _urlInput.value = url
        _clipboardDetectedUrl.value = null
        fetchMedia(url)
    }

    fun fetchMedia(urlOverride: String? = null, qualityOverride: String? = null) {
        val targetUrl = urlOverride ?: _urlInput.value
        val cleanUrl = apiService.extractUrl(targetUrl) ?: targetUrl.trim()

        if (cleanUrl.isBlank()) {
            _errorMessage.value = "Masukkan link video/foto (TikTok, Instagram, YouTube, Facebook) terlebih dahulu."
            return
        }

        val quality = qualityOverride ?: settingsManager.defaultResolution.value

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = apiService.fetchMedia(cleanUrl, quality)
            _isLoading.value = false

            result.onSuccess { media ->
                _mediaPreview.value = media
                _errorMessage.value = null
                _urlInput.value = media.originalUrl
            }.onFailure { error ->
                _errorMessage.value = error.localizedMessage ?: "Gagal mengambil data video. Pastikan link benar."
            }
        }
    }

    fun changeMediaResolution(quality: String) {
        val current = _mediaPreview.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = apiService.fetchMedia(current.originalUrl, quality)
            _isLoading.value = false
            result.onSuccess { updated ->
                _mediaPreview.value = updated
            }
        }
    }

    fun startDownload(type: DownloadType, customFileName: String? = null) {
        val media = _mediaPreview.value ?: return
        viewModelScope.launch {
            val result = downloadManager.downloadMedia(media, type, customFileName)
            result.onSuccess { item ->
                _downloadFinishedItem.value = item
            }.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Gagal mengunduh media"
            }
        }
    }

    fun downloadSelectedPhotos(photoUrls: List<String>, customFileName: String? = null) {
        val media = _mediaPreview.value ?: return
        if (photoUrls.isEmpty()) return
        viewModelScope.launch {
            val result = downloadManager.downloadPhotos(media, photoUrls, customBaseName = customFileName)
            result.onSuccess { items ->
                if (items.isNotEmpty()) {
                    _downloadFinishedItem.value = items.first()
                }
            }.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Gagal mengunduh foto"
            }
        }
    }

    fun dismissDownloadFinishedEvent() {
        _downloadFinishedItem.value = null
    }

    fun cancelDownload(downloadId: String) {
        downloadManager.cancelDownload(downloadId)
    }

    fun deleteDownloadedItem(id: String) {
        historyRepository.removeItem(id)
    }

    fun clearAllHistory() {
        historyRepository.clearAll()
    }

    fun setFilter(filter: HistoryFilter) {
        _selectedFilter.value = filter
    }

    fun openPlayer(target: PlayerTarget) {
        _activePlayerTarget.value = target
    }

    fun closePlayer() {
        _activePlayerTarget.value = null
    }

    fun shareItem(item: DownloadItem) {
        downloadManager.shareFile(item)
    }

    fun openInSystem(item: DownloadItem) {
        downloadManager.openFile(item)
    }

    // In-App Update Methods
    fun checkForUpdate(isManual: Boolean = true) {
        viewModelScope.launch {
            if (isManual) {
                _isCheckingUpdate.value = true
                _updateCheckMessage.value = null
            }
            val result = updateChecker.checkUpdate("1.0.3")
            if (isManual) {
                _isCheckingUpdate.value = false
            }

            result.onSuccess { info ->
                if (info.isUpdateAvailable) {
                    _updateInfo.value = info
                    if (isManual) {
                        _updateCheckMessage.value = "Versi ${info.latestVersion} tersedia!"
                    }
                } else if (isManual) {
                    _updateCheckMessage.value = "Anda sudah menggunakan versi terbaru (v1.0.3)!"
                }
            }.onFailure { err ->
                if (isManual) {
                    _updateCheckMessage.value = "Gagal memeriksa pembaruan: ${err.localizedMessage ?: "Coba lagi nanti"}"
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    fun dismissUpdateMessage() {
        _updateCheckMessage.value = null
    }

    // Batch Download Methods
    fun startBatchDownload(urls: List<String>, quality: String = "1080p") {
        if (urls.isEmpty()) return

        val items = urls.map { url ->
            BatchDownloadItem(url = url.trim(), status = BatchItemStatus.WAITING)
        }
        _batchQueue.value = items
        _isBatchRunning.value = true

        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            for (i in items.indices) {
                if (!isActive) break
                val currentItem = _batchQueue.value[i]

                // Step 1: Fetching
                updateBatchItem(i) { it.copy(status = BatchItemStatus.FETCHING) }

                var fetchResult = apiService.fetchMedia(currentItem.url, quality)
                if (fetchResult.isFailure && isActive) {
                    // Retry fetch sekali jika terjadi kegagalan jaringan sementara
                    delay(500)
                    fetchResult = apiService.fetchMedia(currentItem.url, quality)
                }

                if (fetchResult.isFailure) {
                    updateBatchItem(i) {
                        it.copy(
                            status = BatchItemStatus.FAILED,
                            errorMessage = fetchResult.exceptionOrNull()?.localizedMessage ?: "Gagal mengambil media"
                        )
                    }
                    continue
                }

                val media = fetchResult.getOrNull()!!
                updateBatchItem(i) {
                    it.copy(
                        status = BatchItemStatus.DOWNLOADING,
                        title = media.title
                    )
                }

                // Step 2: Downloading
                var downloadResult = if (media.isImagesPost && media.images.isNotEmpty()) {
                    downloadManager.downloadPhotos(media, media.images).map { it.first() }
                } else {
                    downloadManager.downloadMedia(media, DownloadType.VIDEO)
                }

                // Retry unduhan sekali jika terjadi kegagalan koneksi CDN
                if (downloadResult.isFailure && isActive) {
                    delay(800)
                    downloadResult = if (media.isImagesPost && media.images.isNotEmpty()) {
                        downloadManager.downloadPhotos(media, media.images).map { it.first() }
                    } else {
                        downloadManager.downloadMedia(media, DownloadType.VIDEO)
                    }
                }

                if (downloadResult.isSuccess) {
                    updateBatchItem(i) {
                        it.copy(
                            status = BatchItemStatus.SUCCESS,
                            progress = 1f
                        )
                    }
                    _downloadFinishedItem.value = downloadResult.getOrNull()
                } else {
                    updateBatchItem(i) {
                        it.copy(
                            status = BatchItemStatus.FAILED,
                            errorMessage = downloadResult.exceptionOrNull()?.localizedMessage ?: "Gagal mengunduh"
                        )
                    }
                }

                // Jeda antar unduhan agar CDN tidak memblokir/rate-limit request massal
                delay(350)
            }
            _isBatchRunning.value = false
        }
    }

    fun cancelBatchDownload() {
        batchJob?.cancel()
        _isBatchRunning.value = false
    }

    fun clearBatchQueue() {
        cancelBatchDownload()
        _batchQueue.value = emptyList()
    }

    private fun updateBatchItem(index: Int, transform: (BatchDownloadItem) -> BatchDownloadItem) {
        val current = _batchQueue.value.toMutableList()
        if (index in current.indices) {
            current[index] = transform(current[index])
            _batchQueue.value = current
        }
    }
}

