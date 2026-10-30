package com.libretok.downloader.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.libretok.downloader.data.model.DownloadItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class HistoryRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("libretok_downloads", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        try {
            val json = prefs.getString("items_json", null)
            if (!json.isNullOrBlank()) {
                val array = gson.fromJson(json, Array<DownloadItem>::class.java)
                _items.value = array?.toList()?.sortedByDescending { it.timestamp } ?: emptyList()
            }
        } catch (e: Throwable) {
            _items.value = emptyList()
        }
    }

    private fun saveHistory(list: List<DownloadItem>) {
        try {
            val json = gson.toJson(list.toTypedArray())
            prefs.edit().putString("items_json", json).apply()
            _items.value = list.sortedByDescending { it.timestamp }
        } catch (e: Throwable) {
            _items.value = list.sortedByDescending { it.timestamp }
        }
    }

    fun addItem(item: DownloadItem) {
        val current = _items.value.toMutableList()
        current.removeAll { it.id == item.id || it.filePath == item.filePath }
        current.add(0, item)
        saveHistory(current)
    }

    fun removeItem(id: String) {
        val current = _items.value.toMutableList()
        val item = current.find { it.id == id }
        if (item != null) {
            // Delete actual file if accessible
            try {
                val file = File(item.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (ignored: Exception) {}

            current.removeAll { it.id == id }
            saveHistory(current)
        }
    }

    fun clearAll() {
        val current = _items.value
        for (item in current) {
            try {
                val file = File(item.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (ignored: Exception) {}
        }
        saveHistory(emptyList())
    }
}
