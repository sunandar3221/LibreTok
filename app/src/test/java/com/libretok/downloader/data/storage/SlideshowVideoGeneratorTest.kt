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

        // Prioritas kandidat audio yang aman untuk MediaMuxer AAC
        val candidates = listOfNotNull(
            mediaWithAll.videoUrl.takeIf { it.isNotBlank() },
            mediaWithAll.musicUrl.takeIf { !it.isNullOrBlank() },
            mediaWithAll.previewUrl.takeIf { !it.isNullOrBlank() }
        ).distinct()

        assertEquals(2, candidates.size)
        assertEquals("https://example.com/video.mp4", candidates[0])
        assertEquals("https://example.com/music.mp3", candidates[1])
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
}
