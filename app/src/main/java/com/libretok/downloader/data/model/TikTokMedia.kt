package com.libretok.downloader.data.model

import com.google.gson.annotations.SerializedName

data class TikTokMedia(
    val id: String,
    val title: String,
    val authorName: String,
    val authorUsername: String,
    val authorAvatar: String,
    val coverUrl: String,
    val videoUrl: String,
    val hdVideoUrl: String?,
    val musicUrl: String?,
    val musicTitle: String?,
    val duration: Int,
    val sizeBytes: Long,
    val isImagesPost: Boolean = false,
    val images: List<String> = emptyList(),
    val originalUrl: String,
    val platform: String = "TikTok"
)

// DTO for Cobalt API
data class CobaltResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("filename") val filename: String?,
    @SerializedName("picker") val picker: List<CobaltPickerItem>?,
    @SerializedName("text") val text: String?,
    @SerializedName("error") val error: CobaltError?
)

data class CobaltPickerItem(
    @SerializedName("type") val type: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("thumb") val thumb: String?
)

data class CobaltError(
    @SerializedName("code") val code: String?
)

// DTO for TikWM API response
data class TikWmResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String?,
    @SerializedName("data") val data: TikWmData?
)

data class TikWmData(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("origin_cover") val originCover: String?,
    @SerializedName("duration") val duration: Int?,
    @SerializedName("play") val play: String?,
    @SerializedName("wmplay") val wmplay: String?,
    @SerializedName("hdplay") val hdplay: String?,
    @SerializedName("size") val size: Long?,
    @SerializedName("music") val music: String?,
    @SerializedName("music_info") val musicInfo: TikWmMusicInfo?,
    @SerializedName("author") val author: TikWmAuthor?,
    @SerializedName("images") val images: List<String>?
)

data class TikWmAuthor(
    @SerializedName("id") val id: String?,
    @SerializedName("unique_id") val uniqueId: String?,
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("avatar") val avatar: String?
)

data class TikWmMusicInfo(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("play") val play: String?,
    @SerializedName("author") val author: String?
)
