package com.libretok.downloader.data.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokApiServiceTest {

    private val apiService = TikTokApiService()

    @Test
    fun testExtractUrl() {
        val rawShorts = "Check this out https://youtube.com/shorts/lhqtmG-trLo?si=jrXzFqt0tFA8VKBj so cool"
        val extracted = apiService.extractUrl(rawShorts)
        assertEquals("https://youtube.com/shorts/lhqtmG-trLo?si=jrXzFqt0tFA8VKBj", extracted)
    }

    @Test
    fun testExtractYouTubeId() {
        val urls = listOf(
            "https://youtube.com/shorts/lhqtmG-trLo?si=jrXzFqt0tFA8VKBj" to "lhqtmG-trLo",
            "https://www.youtube.com/watch?v=lhqtmG-trLo" to "lhqtmG-trLo",
            "https://youtu.be/lhqtmG-trLo?si=xyz" to "lhqtmG-trLo",
            "https://www.youtube.com/embed/lhqtmG-trLo" to "lhqtmG-trLo",
            "https://m.youtube.com/watch?v=lhqtmG-trLo&feature=share" to "lhqtmG-trLo"
        )

        for ((url, expectedId) in urls) {
            val actualId = apiService.extractYouTubeId(url)
            assertEquals("Failed for url: $url", expectedId, actualId)
        }
    }

    @Test
    fun testDetectPlatform() {
        assertEquals("YouTube", apiService.detectPlatform("https://youtube.com/shorts/lhqtmG-trLo"))
        assertEquals("YouTube", apiService.detectPlatform("https://youtu.be/lhqtmG-trLo"))
        assertEquals("TikTok", apiService.detectPlatform("https://www.tiktok.com/@user/video/123"))
        assertEquals("Instagram", apiService.detectPlatform("https://www.instagram.com/p/CmCVWoIr9OH/"))
    }

    @Test
    fun testFetchYouTubeShortsLive() = runBlocking {
        val targetUrl = "https://youtube.com/shorts/lhqtmG-trLo?si=jrXzFqt0tFA8VKBj"
        val result = apiService.fetchMedia(targetUrl)

        assertTrue("Expected fetchMedia to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val media = result.getOrNull()
        assertNotNull(media)
        assertEquals("YouTube", media?.platform)
        assertEquals("lhqtmG-trLo", media?.id)
        assertTrue("videoUrl must not be blank", media?.videoUrl?.isNotBlank() == true)
        assertTrue("videoUrl must point to googlevideo.com", media?.videoUrl?.contains("googlevideo.com") == true)
        assertTrue("coverUrl must not be blank", media?.coverUrl?.isNotBlank() == true)
    }
}
