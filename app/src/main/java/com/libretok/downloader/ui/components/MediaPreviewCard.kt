package com.libretok.downloader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.libretok.downloader.data.model.ActiveDownload
import com.libretok.downloader.data.model.DownloadStatus
import com.libretok.downloader.data.model.DownloadType
import com.libretok.downloader.data.model.TikTokMedia
import com.libretok.downloader.ui.theme.TikTokCyan
import com.libretok.downloader.ui.theme.TikTokPink

@Composable
fun MediaPreviewCard(
    media: TikTokMedia,
    activeDownloads: Map<String, ActiveDownload>,
    onDownloadClick: (DownloadType) -> Unit,
    onDownloadSelectedPhotos: (List<String>) -> Unit = {},
    onCancelDownload: (String) -> Unit,
    onPreviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
            startY = 100f
        )
    }

    // State pilihan foto untuk unduhan multi-foto / carousel
    var selectedPhotoIndices by remember(media.id, media.images) {
        mutableStateOf(media.images.indices.toSet())
    }

    // Check if an active download belongs to this media
    val currentActiveDownload = activeDownloads.values.firstOrNull { download ->
        download.id.startsWith(media.id) ||
                (media.title.isNotBlank() && download.title.contains(media.title.take(15)))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Creator info & Platform Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (media.authorAvatar.isNotBlank()) {
                    AsyncImage(
                        model = media.authorAvatar,
                        contentDescription = media.authorName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(TikTokPink.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartDisplay,
                            contentDescription = null,
                            tint = TikTokPink,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = media.authorName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = media.platform,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (media.authorUsername.isNotBlank()) {
                        Text(
                            text = if (media.authorUsername.startsWith("@")) media.authorUsername else "@${media.authorUsername}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Thumbnail / Cover with preview button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPreviewClick() }
            ) {
                if (media.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = media.coverUrl,
                        contentDescription = media.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(gradientBrush)
                )

                // Play Preview button
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Preview",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Duration badge
                if (media.duration > 0) {
                    val durationText = remember(media.duration) {
                        "%02d:%02d".format(media.duration / 60, media.duration % 60)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = durationText,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Video Title / Caption
            Text(
                text = media.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            // STATUS PROGRESS BAR LANGSUNG DI ATAS TOMBOL DOWNLOAD
            AnimatedVisibility(
                visible = currentActiveDownload != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                currentActiveDownload?.let { download ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = TikTokCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val typeLabel = when (download.type) {
                                        DownloadType.VIDEO -> "Video (No Watermark)"
                                        DownloadType.HD_VIDEO -> "Video Full HD"
                                        DownloadType.AUDIO -> "Audio MP3"
                                        DownloadType.PHOTO -> "Foto"
                                    }
                                    Text(
                                        text = "Sedang Mengunduh: $typeLabel",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val percent = (download.progress * 100).toInt()
                                    val sizeProgress = if (download.totalBytes > 0) {
                                        " • %.1f MB / %.1f MB".format(
                                            download.bytesDownloaded / (1024f * 1024f),
                                            download.totalBytes / (1024f * 1024f)
                                        )
                                    } else ""
                                    Text(
                                        text = "$percent%$sizeProgress",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onCancelDownload(download.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Batalkan",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { download.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = TikTokPink,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }

            // Photo Selection Section (Tampil jika konten memiliki foto / album / carousel di platform apapun)
            if (media.images.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = null,
                                tint = TikTokCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pilih Foto (${selectedPhotoIndices.size}/${media.images.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    selectedPhotoIndices = if (selectedPhotoIndices.size == media.images.size) {
                                        emptySet()
                                    } else {
                                        media.images.indices.toSet()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (selectedPhotoIndices.size == media.images.size) "Batal Pilih" else "Pilih Semua",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TikTokPink
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Horizontal thumbnail selector
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(media.images) { index, photoUrl ->
                                val isSelected = selectedPhotoIndices.contains(index)
                                Card(
                                    modifier = Modifier
                                        .size(width = 82.dp, height = 100.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedPhotoIndices = if (isSelected) {
                                                selectedPhotoIndices - index
                                            } else {
                                                selectedPhotoIndices + index
                                            }
                                        },
                                    border = BorderStroke(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) TikTokPink else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = "Foto ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        if (!isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.5f))
                                            )
                                        }

                                        // Badge nomor urut
                                        Surface(
                                            shape = RoundedCornerShape(bottomEnd = 6.dp),
                                            color = Color.Black.copy(alpha = 0.7f),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }

                                        // Badge Centang
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) TikTokPink else Color.Black.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Terpilih",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Download Buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val isAnyDownloading = currentActiveDownload != null
                val isDownloadingVideo = currentActiveDownload?.type == DownloadType.VIDEO
                val isDownloadingHd = currentActiveDownload?.type == DownloadType.HD_VIDEO
                val isDownloadingAudio = currentActiveDownload?.type == DownloadType.AUDIO
                val isDownloadingPhotos = currentActiveDownload?.type == DownloadType.PHOTO

                // Button 1: Download Video (Tanpa Watermark) - jika ada video
                if (media.videoUrl.isNotBlank()) {
                    Button(
                        onClick = { onDownloadClick(DownloadType.VIDEO) },
                        enabled = !isAnyDownloading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TikTokPink)
                    ) {
                        if (isDownloadingVideo) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sedang Mengunduh (${(currentActiveDownload!!.progress * 100).toInt()}%)...",
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Download Video (No Watermark)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Button 2: Download HD Video (if available)
                if (!media.hdVideoUrl.isNullOrBlank()) {
                    FilledTonalButton(
                        onClick = { onDownloadClick(DownloadType.HD_VIDEO) },
                        enabled = !isAnyDownloading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        if (isDownloadingHd) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sedang Mengunduh HD (${(currentActiveDownload!!.progress * 100).toInt()}%)...",
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(imageVector = Icons.Default.HighQuality, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Download Video Full HD",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Button 3: Download Selected Photos (jika ada foto)
                if (media.images.isNotEmpty()) {
                    val count = selectedPhotoIndices.size
                    val buttonLabel = when {
                        count == 0 -> "Pilih Foto Terlebih Dahulu"
                        count == media.images.size -> "Download Semua Foto ($count Foto)"
                        else -> "Download Foto Terpilih ($count Foto)"
                    }

                    Button(
                        onClick = {
                            val selectedUrls = selectedPhotoIndices.sorted().map { media.images[it] }
                            onDownloadSelectedPhotos(selectedUrls)
                        },
                        enabled = !isAnyDownloading && selectedPhotoIndices.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (media.videoUrl.isBlank()) TikTokPink else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isDownloadingPhotos) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sedang Mengunduh Foto...",
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = buttonLabel,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Button 4: Download Audio / MP3
                if (!media.musicUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onDownloadClick(DownloadType.AUDIO) },
                        enabled = !isAnyDownloading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isDownloadingAudio) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sedang Mengunduh MP3 (${(currentActiveDownload!!.progress * 100).toInt()}%)...",
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(imageVector = Icons.Default.MusicNote, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Download Audio (MP3)",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
