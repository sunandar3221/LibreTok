package com.libretok.downloader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.libretok.downloader.data.network.YtDlpHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LibreTokApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Inisialisasi yt-dlp & FFmpeg di background thread agar startup aplikasi tetap instan
        CoroutineScope(Dispatchers.IO).launch {
            YtDlpHelper.init(this@LibreTokApp)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
