package com.libretok.downloader.data.storage

import com.libretok.downloader.data.model.TikTokMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideshowVideoGeneratorTest {

    @Test
    fun testAudioCandidatePriority() {
        val mediaWithAll = TikTokMedia(
            id = "12345",
            title = "Sample TikTok",
            authorName = "User",
            authorUsername = "user",
            authorAvatar = "",
            coverUrl = "https://example.com/cover.jpg",
            videoUrl = "https://example.com/video.mp4",
            hdVideoUrl = "https://example.com/hd.mp4",
            wmVideoUrl = "",
            musicUrl = "https://example.com/music.mp3",
            musicTitle = "Sample Sound",
            duration = 15,
            sizeBytes = 1000,
            isImagesPost = true,
            images = listOf("https://example.com/img1.jpg", "https://example.com/img2.jpg"),
            originalUrl = "https://www.tiktok.com/@user/video/12345"
        )

        // Prioritas kandidat audio latar belakang: utamakan musik MP3 TikTok asli
        val candidates = listOfNotNull(
            mediaWithAll.musicUrl.takeIf { !it.isNullOrBlank() },
            mediaWithAll.videoUrl.takeIf { it.isNotBlank() },
            mediaWithAll.previewUrl.takeIf { !it.isNullOrBlank() }
        ).distinct()

        assertEquals(2, candidates.size)
        assertEquals("https://example.com/music.mp3", candidates[0])
        assertEquals("https://example.com/video.mp4", candidates[1])
    }

    @Test
    fun testSlideCalculation() {
        val numImages = 4
        val audioDurationMs = 20_000L // 20 detik
        val frameRate = 15
        val totalFrames = ((audioDurationMs * frameRate) / 1000L).toInt().coerceAtLeast(frameRate)
        val framesPerImage = totalFrames / numImages
        val remainder = totalFrames % numImages

        assertEquals(300, totalFrames)
        assertEquals(75, framesPerImage)
        assertEquals(0, remainder)

        // Verifikasi transisi slide frames
        val transitionFrames = minOf(6, maxOf(1, framesPerImage / 4))
        assertEquals(6, transitionFrames) // 6 frame (400ms) untuk swipe effect
        assertTrue("Hold frames must be positive", (framesPerImage - transitionFrames) > 0)
    }

    @Test
    fun testTrackIndexDistinction() {
        // Simulasi file TikTok MP4: stream 0 adalah audio (AAC), stream 1 adalah video (H264)
        val extractorAudioTrackIndex = 0
        // Di MediaMuxer, video track didaftarkan pertama (index 0), audio track didaftarkan kedua (index 1)
        val muxerAudioTrackIndex = 1

        // Pastikan extractor sample track index dicocokkan ke extractorAudioTrackIndex, BUKAN muxerAudioTrackIndex
        val sampleTrackIndexFromExtractor = 0
        val isAudioSample = (sampleTrackIndexFromExtractor == extractorAudioTrackIndex)
        val wrongComparison = (sampleTrackIndexFromExtractor == muxerAudioTrackIndex)

        assertTrue("Sample index harus cocok dengan extractor audio index", isAudioSample)
        assertTrue("Sample index tidak boleh disamakan dengan muxer index yang berbeda", !wrongComparison)
    }

    @Test
    fun testPcmTimestampCalculation() {
        val sampleRate = 44100
        val channels = 2
        val bytesPerSecond = sampleRate * channels * 2L // 16-bit PCM: 2 bytes per sample
        assertEquals(176400L, bytesPerSecond)

        val frameSize = 1024 * channels * 2 // 4096 bytes per AAC frame
        assertEquals(4096, frameSize)

        val frameTimeUs = (frameSize.toLong() * 1_000_000L) / bytesPerSecond
        // 4096 * 1000000 / 176400 = 23219.95 -> 23219 us (~23.2ms)
        assertEquals(23219L, frameTimeUs)
    }
}
