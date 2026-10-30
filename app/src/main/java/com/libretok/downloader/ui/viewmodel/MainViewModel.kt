package com.libretok.downloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.libretok.downloader.data.model.ActiveDownload
import com.libretok.downloader.data.model.DownloadItem
import com.libretok.downloader.data.model.DownloadType
import com.libretok.downloader.data.model.TikTokMedia
import com.libretok.downloader.data.network.TikTokApiService
import com.libretok.downloader.data.storage.DownloadManager
import com.libretok.downloader.data.storage.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

    val downloadsHistory: StateFlow<List<DownloadItem>> = historyRepository.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads: StateFlow<Map<String, ActiveDownload>> = downloadManager.activeDownloads

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

    fun fetchMedia(urlOverride: String? = null) {
        val targetUrl = urlOverride ?: _urlInput.value
        val cleanUrl = apiService.extractUrl(targetUrl) ?: targetUrl.trim()

        if (cleanUrl.isBlank()) {
            _errorMessage.value = "Masukkan link video (TikTok, Instagram, YouTube, Facebook) terlebih dahulu."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = apiService.fetchMedia(cleanUrl)
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

    fun startDownload(type: DownloadType) {
        val media = _mediaPreview.value ?: return
        viewModelScope.launch {
            downloadManager.downloadMedia(media, type)
        }
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
}
