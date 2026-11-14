package com.libretok.downloader.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.libretok.downloader.data.model.TikTokMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class SlideshowVideoGenerator(
    private val context: Context,
    private val client: OkHttpClient
) {

    suspend fun createSlideshowVideo(
        media: TikTokMedia,
        outputFile: File,
        onProgress: (progress: Float) -> Unit,
        isCancelled: () -> Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val images = if (media.images.isNotEmpty()) {
            media.images
        } else {
            listOfNotNull(media.coverUrl.ifBlank { null })
        }

        if (images.isEmpty()) {
            return@withContext Result.failure(Exception("Tidak ada foto untuk dibuat menjadi video"))
        }

        val tempRawAudioFile = File(context.cacheDir, "temp_slide_rawaudio_${System.currentTimeMillis()}.bin")
        val tempPcmFile = File(context.cacheDir, "temp_slide_pcm_${System.currentTimeMillis()}.raw")
        val tempAacAudioFile = File(context.cacheDir, "temp_slide_aac_${System.currentTimeMillis()}.mp4")
        val tempRawVideoFile = File(context.cacheDir, "temp_slide_rawvideo_${System.currentTimeMillis()}.mp4")

        try {
            onProgress(0.04f)

            // 1. Unduh sumber audio latar belakang (utamakan musicUrl MP3 asli dari TikTok)
            val audioDownloaded = downloadAudioSource(media, tempRawAudioFile, isCancelled)
            if (isCancelled()) throw Exception("Pembuatan video dibatalkan")

            // 2. Dapatkan durasi audio secara akurat
            var audioDurationMs = 0L

            if (audioDownloaded && tempRawAudioFile.exists() && tempRawAudioFile.length() > 0L) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(tempRawAudioFile.absolutePath)
                    val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    audioDurationMs = durStr?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }

            // Fallback durasi jika tidak terdeteksi
            if (audioDurationMs <= 0L) {
                audioDurationMs = (media.duration * 1000L).takeIf { it > 0 } ?: (images.size * 3000L)
            }
            // Batasi minimal 2 detik dan maksimal 5 menit
            audioDurationMs = audioDurationMs.coerceIn(2000L, 300_000L)

            onProgress(0.08f)
            if (isCancelled()) throw Exception("Pembuatan video dibatalkan")

            // 3. Siapkan audio agar kompatibel dengan MediaMuxer MP4 (transcode MP3 ke AAC jika diperlukan)
            val finalAudioFile = if (audioDownloaded && tempRawAudioFile.exists() && tempRawAudioFile.length() > 0L) {
                prepareAudioForMuxing(
                    rawAudioFile = tempRawAudioFile,
                    tempPcmFile = tempPcmFile,
                    tempAacFile = tempAacAudioFile,
                    isCancelled = isCancelled
                )
            } else null

            onProgress(0.12f)
            if (isCancelled()) throw Exception("Pembuatan video dibatalkan")

            // 4. Encode foto-foto menjadi video H.264 dengan animasi slide (swipe transition)
            encodeImagesWithSlideAnimation(
                images = images,
                totalDurationMs = audioDurationMs,
                destinationVideoFile = tempRawVideoFile,
                onProgress = { encProgress ->
                    // Skala progress: 0.12f -> 0.85f
                    onProgress(0.12f + (encProgress * 0.73f))
                },
                isCancelled = isCancelled
            )

            if (isCancelled()) throw Exception("Pembuatan video dibatalkan")

            if (!tempRawVideoFile.exists() || tempRawVideoFile.length() == 0L) {
                throw Exception("Gagal membuat stream video dari foto")
            }

            onProgress(0.88f)

            // 5. Muxing video dengan track audio AAC
            val audioToMux = finalAudioFile ?: (if (audioDownloaded && tempRawAudioFile.exists() && tempRawAudioFile.length() > 0L) tempRawAudioFile else null)
            if (audioToMux != null && audioToMux.exists() && audioToMux.length() > 0L) {
                muxVideoWithAudio(
                    videoFile = tempRawVideoFile,
                    audioFile = audioToMux,
                    outputFile = outputFile,
                    onProgress = { muxProgress ->
                        onProgress(0.88f + (muxProgress * 0.11f))
                    },
                    isCancelled = isCancelled
                )
            } else {
                tempRawVideoFile.copyTo(outputFile, overwrite = true)
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw Exception("Gagal menggabungkan berkas video dan audio")
            }

            onProgress(1.0f)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { tempRawAudioFile.delete() } catch (_: Exception) {}
            try { tempPcmFile.delete() } catch (_: Exception) {}
            try { tempAacAudioFile.delete() } catch (_: Exception) {}
            try { tempRawVideoFile.delete() } catch (_: Exception) {}
        }
    }

    private data class AudioFormatInfo(
        val sampleRate: Int,
        val channelCount: Int
    )

    private fun prepareAudioForMuxing(
        rawAudioFile: File,
        tempPcmFile: File,
        tempAacFile: File,
        isCancelled: () -> Boolean
    ): File? {
        val mime = getAudioMimeType(rawAudioFile) ?: return null
        if (mime == "audio/mp4a-latm") {
            // Sudah dalam format AAC MP4, bisa langsung dipakai untuk muxing
            return rawAudioFile
        }

        // Format audio lain (seperti audio/mpeg MP3 dari TikTok):
        // Tahap 1: Decode MP3 ke PCM mentah secara sekuensial (bebas risiko deadlock)
        val formatInfo = decodeAudioToPcm(rawAudioFile, tempPcmFile, isCancelled) ?: return null
        if (isCancelled()) return null

        // Tahap 2: Encode PCM mentah ke AAC MP4 (bebas risiko deadlock)
        val ok = encodePcmToAacMp4(
            pcmFile = tempPcmFile,
            aacOutputFile = tempAacFile,
            sampleRate = formatInfo.sampleRate,
            channelCount = formatInfo.channelCount,
            isCancelled = isCancelled
        )
        if (ok && tempAacFile.exists() && tempAacFile.length() > 0L) {
            return tempAacFile
        }
        return null
    }

    private fun getAudioMimeType(file: File): String? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    return mime
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
        return null
    }

    private fun decodeAudioToPcm(
        sourceAudioFile: File,
        pcmOutputFile: File,
        isCancelled: () -> Boolean
    ): AudioFormatInfo? {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var fos: FileOutputStream? = null

        try {
            extractor = MediaExtractor().apply { setDataSource(sourceAudioFile.absolutePath) }
            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = format
                    extractor.selectTrack(i)
                    break
                }
            }

            if (audioTrackIndex == -1 || trackFormat == null) return null

            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(trackFormat, null, null, 0)
            decoder.start()

            fos = FileOutputStream(pcmOutputFile)

            val timeoutUs = 10_000L
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false

            var actualSampleRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            var actualChannels = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            var idleCount = 0
            val maxIdle = 500

            while (!outputEos && idleCount < maxIdle) {
                if (isCancelled()) return null
                var progressMade = false

                // 1. Masukkan sampel dari Extractor ke Decoder
                if (!inputEos) {
                    val inIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inBuffer = decoder.getInputBuffer(inIndex)
                        if (inBuffer != null) {
                            inBuffer.clear()
                            val sampleSize = extractor.readSampleData(inBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                            progressMade = true
                        }
                    }
                }

                // 2. Ambil data PCM mentah dari Decoder lalu simpan ke berkas sementara
                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIndex >= 0) {
                    progressMade = true
                    val outBuffer = decoder.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val pcmBytes = ByteArray(bufferInfo.size)
                        outBuffer.get(pcmBytes)
                        fos.write(pcmBytes)
                    }
                    decoder.releaseOutputBuffer(outIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEos = true
                        break
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    progressMade = true
                    val newFormat = decoder.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        actualSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        actualChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }

                if (progressMade) {
                    idleCount = 0
                } else {
                    idleCount++
                }
            }

            fos.flush()
            if (pcmOutputFile.exists() && pcmOutputFile.length() > 0L) {
                return AudioFormatInfo(actualSampleRate, actualChannels)
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try { fos?.close() } catch (_: Exception) {}
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    private fun encodePcmToAacMp4(
        pcmFile: File,
        aacOutputFile: File,
        sampleRate: Int,
        channelCount: Int,
        isCancelled: () -> Boolean
    ): Boolean {
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var fis: FileInputStream? = null
        var muxerStarted = false

        val validSampleRates = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000)
        val rate = if (sampleRate in validSampleRates) sampleRate else 44100
        val channels = channelCount.coerceIn(1, 2)

        try {
            val aacFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", rate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, if (channels == 1) 64_000 else 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
            encoder.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(aacOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var audioTrackIndex = -1

            fis = FileInputStream(pcmFile)
            val frameSize = 1024 * channels * 2
            val pcmChunk = ByteArray(frameSize)

            val timeoutUs = 10_000L
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            var presentationTimeUs = 0L

            var idleCount = 0
            val maxIdle = 500

            while (!outputEos && idleCount < maxIdle) {
                if (isCancelled()) return false
                var progressMade = false

                // 1. Masukkan PCM ke Encoder
                if (!inputEos) {
                    val inIndex = encoder.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inBuffer = encoder.getInputBuffer(inIndex)
                        if (inBuffer != null) {
                            inBuffer.clear()
                            val bytesToRead = minOf(frameSize, inBuffer.remaining())
                            val bytesRead = fis.read(pcmChunk, 0, bytesToRead)
                            if (bytesRead <= 0) {
                                encoder.queueInputBuffer(inIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                inBuffer.put(pcmChunk, 0, bytesRead)
                                encoder.queueInputBuffer(inIndex, 0, bytesRead, presentationTimeUs, 0)
                                presentationTimeUs += (bytesRead.toLong() * 1_000_000L) / (rate * channels * 2L)
                            }
                            progressMade = true
                        }
                    }
                }

                // 2. Ambil paket AAC dari Encoder dan tulis ke MediaMuxer
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIndex >= 0) {
                    progressMade = true
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && muxerStarted) {
                        val outBuffer = encoder.getOutputBuffer(outIndex)
                        if (outBuffer != null) {
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(audioTrackIndex, outBuffer, bufferInfo)
                        }
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEos = true
                        break
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    progressMade = true
                    if (!muxerStarted) {
                        audioTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }

                if (progressMade) {
                    idleCount = 0
                } else {
                    idleCount++
                }
            }

            return aacOutputFile.exists() && aacOutputFile.length() > 0L
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try { fis?.close() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            if (muxerStarted) {
                try { muxer?.stop() } catch (_: Exception) {}
                try { muxer?.release() } catch (_: Exception) {}
            }
        }
    }

    private fun downloadAudioSource(
        media: TikTokMedia,
        destinationFile: File,
        isCancelled: () -> Boolean
    ): Boolean {
        // Prioritas 1: media.musicUrl (Audio MP3 asli TikTok)
        // Prioritas 2: media.videoUrl (Stream MP4 TikTok)
        // Prioritas 3: media.previewUrl
        val candidates = listOfNotNull(
            media.musicUrl.takeIf { !it.isNullOrBlank() },
            media.videoUrl.takeIf { it.isNotBlank() },
            media.previewUrl.takeIf { !it.isNullOrBlank() }
        ).distinct()

        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        for (url in candidates) {
            if (isCancelled()) return false
            try {
                val host = try { Uri.parse(url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", ua)
                    .header("Accept", "*/*")

                if (host.contains("tikwm.com")) {
                    reqBuilder.header("Referer", "https://www.tikwm.com/")
                } else if (host.contains("tiktok.com")) {
                    reqBuilder.header("Referer", "https://www.tiktok.com/")
                }

                val fastClient = client.newBuilder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val resp = fastClient.newCall(reqBuilder.build()).execute()
                if (!resp.isSuccessful) continue
                val body = resp.body ?: continue

                FileOutputStream(destinationFile).use { out ->
                    body.byteStream().use { input ->
                        input.copyTo(out)
                    }
                }

                if (destinationFile.exists() && destinationFile.length() > 0L) {
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(destinationFile.absolutePath)
                        for (i in 0 until extractor.trackCount) {
                            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                            if (mime.startsWith("audio/")) {
                                return true
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        try { extractor.release() } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return destinationFile.exists() && destinationFile.length() > 0L
    }

    private fun encodeImagesWithSlideAnimation(
        images: List<String>,
        totalDurationMs: Long,
        destinationVideoFile: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ) {
        val width = 720
        val height = 1280
        val frameRate = 15 // 15 fps untuk slideshow yang halus, cepat, dan hemat memori
        val frameDurationUs = 1_000_000L / frameRate
        val totalFrames = ((totalDurationMs * frameRate) / 1000L).toInt().coerceAtLeast(frameRate)

        val (codecInfo, colorFormat, isSemiPlanar) = selectAvcEncoder()

        val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createByCodecName(codecInfo.name)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(destinationVideoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var muxerStarted = false

        val numImages = images.size
        val framesPerImage = totalFrames / numImages
        val remainder = totalFrames % numImages

        var currentTimestampUs = 0L
        var globalFrameIndex = 0

        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L

        fun drain(endOfStream: Boolean) {
            var tryAgainCount = 0
            val maxTryAgain = if (endOfStream) 20 else 3

            while (true) {
                if (isCancelled()) break
                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    tryAgainCount++
                    if (tryAgainCount >= maxTryAgain) {
                        break
                    }
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    tryAgainCount = 0
                    if (!muxerStarted) {
                        videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                } else if (outputIndex >= 0) {
                    tryAgainCount = 0
                    val encodedData = encoder.getOutputBuffer(outputIndex)
                    if (encodedData != null) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size != 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outputIndex, false)
                    }
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }

        fun feedFrame(yuvBytes: ByteArray) {
            var inputQueued = false
            while (!inputQueued && !isCancelled()) {
                val inputBufferIndex = encoder.dequeueInputBuffer(timeoutUs)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                        ?: throw Exception("Input buffer MediaCodec bernilai null")
                    inputBuffer.clear()
                    val bytesToPut = minOf(yuvBytes.size, inputBuffer.remaining())
                    inputBuffer.put(yuvBytes, 0, bytesToPut)
                    encoder.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        bytesToPut,
                        currentTimestampUs,
                        0
                    )
                    inputQueued = true
                    currentTimestampUs += frameDurationUs
                    globalFrameIndex++
                }
                drain(false)
            }
            if (totalFrames > 0) {
                onProgress((globalFrameIndex.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f))
            }
        }

        try {
            var currentBitmap: Bitmap? = downloadAndRenderBitmap(images[0], width, height)
                ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }

            val transitionBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val transitionCanvas = Canvas(transitionBitmap)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            for (imgIdx in 0 until numImages) {
                if (isCancelled()) throw Exception("Pembuatan video dibatalkan")

                val isLastImage = (imgIdx == numImages - 1)
                val framesForThisImage = framesPerImage + if (isLastImage) remainder else 0

                // Durasi transisi geser: ~0.4 detik (6 frame pada 15 fps)
                val transitionFrames = if (!isLastImage) minOf(6, maxOf(1, framesForThisImage / 4)) else 0
                val holdFrames = framesForThisImage - transitionFrames

                // 1. Tampilkan gambar saat ini (hold frame diam)
                val holdYuv = convertBitmapToYuv420(currentBitmap!!, width, height, isSemiPlanar)
                for (f in 0 until holdFrames) {
                    if (isCancelled()) throw Exception("Pembuatan video dibatalkan")
                    feedFrame(holdYuv)
                }

                // 2. Jika ada gambar berikutnya, tampilkan animasi slide horizontal (swipe effect)
                if (!isLastImage) {
                    val nextBitmap: Bitmap = downloadAndRenderBitmap(images[imgIdx + 1], width, height)
                        ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }

                    for (t in 0 until transitionFrames) {
                        if (isCancelled()) throw Exception("Pembuatan video dibatalkan")
                        val p = (t + 1).toFloat() / transitionFrames
                        // Kurva ease-in-out yang mulus
                        val eased = if (p < 0.5f) 2f * p * p else 1f - ((-2f * p + 2f) * (-2f * p + 2f)) / 2f

                        transitionCanvas.drawColor(Color.BLACK)
                        // Gambar lama bergeser ke kiri (slide out)
                        transitionCanvas.drawBitmap(currentBitmap, -eased * width, 0f, paint)
                        // Gambar baru bergeser masuk dari kanan (slide in)
                        transitionCanvas.drawBitmap(nextBitmap, (1f - eased) * width, 0f, paint)

                        val transYuv = convertBitmapToYuv420(transitionBitmap, width, height, isSemiPlanar)
                        feedFrame(transYuv)
                    }

                    currentBitmap.recycle()
                    currentBitmap = nextBitmap
                }
            }

            currentBitmap?.recycle()
            transitionBitmap.recycle()

            // Kirim tanda End of Stream (EOS) ke encoder
            var eosQueued = false
            var attempts = 0
            while (!eosQueued && attempts < 50 && !isCancelled()) {
                attempts++
                val inputBufferIndex = encoder.dequeueInputBuffer(timeoutUs)
                if (inputBufferIndex >= 0) {
                    encoder.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        0,
                        currentTimestampUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    eosQueued = true
                } else {
                    drain(false)
                }
            }

            drain(true)
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            if (muxerStarted) {
                try { muxer.stop() } catch (_: Exception) {}
                try { muxer.release() } catch (_: Exception) {}
            }
        }
    }

    private fun downloadAndRenderBitmap(imgUrl: String, width: Int, height: Int): Bitmap? {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val imgHost = try { Uri.parse(imgUrl).host?.lowercase() ?: "" } catch (_: Exception) { "" }
        val reqBuilder = Request.Builder()
            .url(imgUrl)
            .header("User-Agent", ua)
            .header("Accept", "*/*")

        if (imgHost.contains("tikwm.com")) {
            reqBuilder.header("Referer", "https://www.tikwm.com/")
        } else if (imgHost.contains("tiktok.com")) {
            reqBuilder.header("Referer", "https://www.tiktok.com/")
        }

        val fastClient = client.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        return try {
            val resp = fastClient.newCall(reqBuilder.build()).execute()
            val imgBytes = resp.body?.bytes() ?: return null
            val rawBitmap = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size) ?: return null
            val frameBitmap = renderTo720x1280(rawBitmap, width, height)
            rawBitmap.recycle()
            frameBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun muxVideoWithAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ) {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoTrackIndex = -1
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    videoTrackIndex = muxer.addTrack(format)
                    break
                }
            }

            if (videoTrackIndex == -1) {
                throw Exception("Track video tidak ditemukan dalam berkas")
            }

            var extractorAudioTrackIndex = -1
            var muxerAudioTrackIndex = -1

            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    extractorAudioTrackIndex = i
                    try {
                        muxerAudioTrackIndex = muxer.addTrack(format)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    break
                }
            }

            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            var videoDone = false
            var audioDone = (extractorAudioTrackIndex == -1 || muxerAudioTrackIndex == -1)

            // Lompat melewati frame non-audio (misal dummy video) sampai ke sample audio berikutnya
            fun advanceAudioToNextAudioSample() {
                while (!audioDone) {
                    val trackIdx = audioExtractor.sampleTrackIndex
                    if (trackIdx == -1) {
                        audioDone = true
                        break
                    } else if (trackIdx == extractorAudioTrackIndex) {
                        break
                    } else {
                        audioExtractor.advance()
                    }
                }
            }

            advanceAudioToNextAudioSample()

            // Interleaved sample writing agar timestamps selalu teratur
            while (!videoDone || !audioDone) {
                if (isCancelled()) throw Exception("Pembuatan video dibatalkan")

                advanceAudioToNextAudioSample()

                val videoSampleTime = if (!videoDone) videoExtractor.sampleTime else -1L
                val audioSampleTime = if (!audioDone) audioExtractor.sampleTime else -1L

                if (videoSampleTime < 0L) videoDone = true
                if (audioSampleTime < 0L) audioDone = true

                if (videoDone && audioDone) break

                if (!videoDone && (audioDone || videoSampleTime <= audioSampleTime)) {
                    val sampleSize = videoExtractor.readSampleData(buffer, 0)
                    if (sampleSize >= 0) {
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = videoSampleTime
                        bufferInfo.flags = videoExtractor.sampleFlags
                        muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                        videoExtractor.advance()
                    } else {
                        videoDone = true
                    }
                } else if (!audioDone) {
                    val sampleSize = audioExtractor.readSampleData(buffer, 0)
                    if (sampleSize >= 0) {
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = audioSampleTime
                        bufferInfo.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
                        audioExtractor.advance()
                        advanceAudioToNextAudioSample()
                    } else {
                        audioDone = true
                    }
                }
            }
            onProgress(1.0f)
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
        }
    }

    private fun renderTo720x1280(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)

        val srcWidth = source.width.toFloat()
        val srcHeight = source.height.toFloat()
        if (srcWidth <= 0f || srcHeight <= 0f) return result

        val scale = minOf(targetWidth.toFloat() / srcWidth, targetHeight.toFloat() / srcHeight)
        val destWidth = srcWidth * scale
        val destHeight = srcHeight * scale
        val left = (targetWidth - destWidth) / 2f
        val top = (targetHeight - destHeight) / 2f

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val destRect = RectF(left, top, left + destWidth, top + destHeight)
        canvas.drawBitmap(source, null, destRect, paint)
        return result
    }

    private fun convertBitmapToYuv420(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        isSemiPlanar: Boolean
    ): ByteArray {
        val totalPixels = width * height
        val yuv = ByteArray(totalPixels * 3 / 2)
        val argb = IntArray(totalPixels)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        var yIndex = 0
        var uIndex = totalPixels
        var vIndex = totalPixels + (totalPixels / 4)

        for (j in 0 until height) {
            val rowOffset = j * width
            for (i in 0 until width) {
                val c = argb[rowOffset + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                // ITU-R BT.601 conversion
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                // Subsample U and V 2x2
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                    if (isSemiPlanar) {
                        // NV12: U then V interleaved
                        yuv[uIndex++] = u.coerceIn(0, 255).toByte()
                        yuv[uIndex++] = v.coerceIn(0, 255).toByte()
                    } else {
                        // I420: Planar U block then V block
                        yuv[uIndex++] = u.coerceIn(0, 255).toByte()
                        yuv[vIndex++] = v.coerceIn(0, 255).toByte()
                    }
                }
            }
        }
        return yuv
    }

    @Suppress("DEPRECATION")
    private fun selectAvcEncoder(): Triple<MediaCodecInfo, Int, Boolean> {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.equals("video/avc", ignoreCase = true)) {
                    val caps = info.getCapabilitiesForType("video/avc")
                    val colorFormats = caps.colorFormats

                    if (colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)) {
                        return Triple(info, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar, true)
                    }
                    if (colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)) {
                        return Triple(info, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar, false)
                    }
                }
            }
        }

        // Fallback default
        val defaultCodec = MediaCodec.createEncoderByType("video/avc")
        val info = defaultCodec.codecInfo
        defaultCodec.release()
        return Triple(info, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar, true)
    }
}
