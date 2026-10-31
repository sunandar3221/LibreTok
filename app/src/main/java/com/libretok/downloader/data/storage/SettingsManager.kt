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
}
