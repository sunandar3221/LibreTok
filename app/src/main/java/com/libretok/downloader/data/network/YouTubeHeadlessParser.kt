package com.libretok.downloader.data.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class HeadlessStreamResult(
    val videoUrl: String?,
    val audioUrl: String?,
    val poToken: String?
)

class YouTubeHeadlessParser(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    private val gson = Gson()

    /**
     * Mengekstrak stream video HD atau PO Token untuk video YouTube berdurasi panjang.
     * Mendukung:
     * 1. Server PO Token kustom eksternal (jika disetel).
     * 2. Headless WebView lokal yang menjalankan script YouTube secara otomatis di Android.
     */
    suspend fun extractLongVideoStreams(
        videoId: String,
        customServerUrl: String?
    ): HeadlessStreamResult? {
        // 1. Jika pengguna menyediakan server PO Token kustom di pengaturan, prioritaskan itu
        if (!customServerUrl.isNullOrBlank()) {
            val serverResult = queryCustomPoTokenServer(videoId, customServerUrl)
            if (serverResult != null) {
                return serverResult
            }
        }

        // 2. Gunakan Headless WebView lokal pada thread utama
        return extractViaHeadlessWebView(videoId)
    }

    private suspend fun queryCustomPoTokenServer(
        videoId: String,
        serverUrl: String
    ): HeadlessStreamResult? = withContext(Dispatchers.IO) {
        try {
            val fullUrl = if (serverUrl.contains("?")) {
                "$serverUrl&videoId=$videoId"
            } else {
                "$serverUrl?videoId=$videoId"
            }

            val request = Request.Builder()
                .url(fullUrl)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null

                // Coba parse JSON: format yang mungkin dikembalikan server { "poToken": "...", "videoUrl": "...", "audioUrl": "..." }
                val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { null }
                if (json != null) {
                    val token = json.get("poToken")?.asString
                        ?: json.get("token")?.asString
                    val vUrl = json.get("videoUrl")?.asString
                        ?: json.get("url")?.asString
                    val aUrl = json.get("audioUrl")?.asString
                    return@withContext HeadlessStreamResult(videoUrl = vUrl, audioUrl = aUrl, poToken = token)
                } else if (body.isNotBlank() && body.length < 500) {
                    // Plain text poToken
                    return@withContext HeadlessStreamResult(videoUrl = null, audioUrl = null, poToken = body.trim())
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractViaHeadlessWebView(videoId: String): HeadlessStreamResult? {
        return withContext(Dispatchers.Main) {
            val deferredResult = CompletableDeferred<HeadlessStreamResult?>()
            var webView: WebView? = null

            try {
                webView = WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    }
                }

                var capturedVideoUrl: String? = null
                var capturedAudioUrl: String? = null
                var capturedPoToken: String? = null

                fun checkCompletion() {
                    if (capturedVideoUrl != null && !deferredResult.isCompleted) {
                        deferredResult.complete(
                            HeadlessStreamResult(
                                videoUrl = capturedVideoUrl,
                                audioUrl = capturedAudioUrl,
                                poToken = capturedPoToken
                            )
                        )
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val urlStr = request?.url?.toString() ?: return null

                        if (urlStr.contains("googlevideo.com/videoplayback")) {
                            // Ekstrak parameter jika ada poToken
                            val potParam = request.url.getQueryParameter("pot")
                                ?: request.url.getQueryParameter("potok")
                            if (!potParam.isNullOrBlank()) {
                                capturedPoToken = potParam
                            }

                            val isAudio = urlStr.contains("mime=audio") || urlStr.contains("itag=140")
                            val isVideo = urlStr.contains("mime=video") || urlStr.contains("itag=137") ||
                                    urlStr.contains("itag=299") || urlStr.contains("itag=136") ||
                                    urlStr.contains("itag=298") || urlStr.contains("itag=399")

                            if (isAudio && capturedAudioUrl == null) {
                                capturedAudioUrl = urlStr
                            } else if (isVideo && capturedVideoUrl == null) {
                                capturedVideoUrl = urlStr
                            }

                            if (capturedVideoUrl != null) {
                                Handler(Looper.getMainLooper()).post { checkCompletion() }
                            }
                        }

                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Trigger video play secara muted agar browser meminta stream chunk
                        view?.evaluateJavascript(
                            """
                            (function() {
                                try {
                                    var v = document.querySelector('video');
                                    if (v) {
                                        v.muted = true;
                                        v.play();
                                    }
                                } catch(e) {}
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                }

                // Muat embed player resmi YouTube yang langsung memicu pemutaran video
                webView.loadUrl("https://www.youtube.com/embed/$videoId?autoplay=1&mute=1")

                // Batas waktu tunggu maksimal 15 detik
                val result = withTimeoutOrNull(15_000) {
                    deferredResult.await()
                }

                result
            } catch (_: Exception) {
                null
            } finally {
                try {
                    webView?.stopLoading()
                    webView?.destroy()
                } catch (_: Exception) {}
            }
        }
    }
}
