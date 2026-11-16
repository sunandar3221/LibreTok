package com.libretok.downloader.data.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("libretok_settings", Context.MODE_PRIVATE)

    private val _isAutoResolution = MutableStateFlow(prefs.getBoolean("auto_resolution", true))
    val isAutoResolution: StateFlow<Boolean> = _isAutoResolution.asStateFlow()

    private val _defaultResolution = MutableStateFlow(prefs.getString("default_resolution", "1080p") ?: "1080p")
    val defaultResolution: StateFlow<String> = _defaultResolution.asStateFlow()

    private val _askRenameBeforeDownload = MutableStateFlow(prefs.getBoolean("ask_rename", false))
    val askRenameBeforeDownload: StateFlow<Boolean> = _askRenameBeforeDownload.asStateFlow()

    private val _appLanguage = MutableStateFlow(prefs.getString("app_language", "system") ?: "system")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    private val _isLongYouTubeHdEnabled = MutableStateFlow(prefs.getBoolean("enable_long_yt_hd", false))
    val isLongYouTubeHdEnabled: StateFlow<Boolean> = _isLongYouTubeHdEnabled.asStateFlow()

    private val _customPoTokenServer = MutableStateFlow(prefs.getString("custom_potoken_server", "") ?: "")
    val customPoTokenServer: StateFlow<String> = _customPoTokenServer.asStateFlow()

    fun setAutoResolution(enabled: Boolean) {
        prefs.edit().putBoolean("auto_resolution", enabled).apply()
        _isAutoResolution.value = enabled
    }

    fun setDefaultResolution(resolution: String) {
        prefs.edit().putString("default_resolution", resolution).apply()
        _defaultResolution.value = resolution
    }

    fun setAskRenameBeforeDownload(enabled: Boolean) {
        prefs.edit().putBoolean("ask_rename", enabled).apply()
        _askRenameBeforeDownload.value = enabled
    }

    fun setAppLanguage(languageCode: String) {
        prefs.edit().putString("app_language", languageCode).apply()
        _appLanguage.value = languageCode
    }

    fun setLongYouTubeHdEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enable_long_yt_hd", enabled).apply()
        _isLongYouTubeHdEnabled.value = enabled
    }

    fun setCustomPoTokenServer(serverUrl: String) {
        prefs.edit().putString("custom_potoken_server", serverUrl.trim()).apply()
        _customPoTokenServer.value = serverUrl.trim()
    }
}
