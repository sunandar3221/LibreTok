package com.libretok.downloader.ui.components

import android.content.Context
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPreviewCard(
    media: TikTokMedia,
    activeDownloads: Map<String, ActiveDownload>,
    askRename: Boolean = false,
    isAutoResolution: Boolean = true,
    onChangeResolution: (String) -> Unit = {},
    onChangeAudioTrack: (String) -> Unit = {},
    onDownloadClick: (DownloadType, String?) -> Unit,
    onDownloadSelectedPhotos: (List<String>, String?) -> Unit = { _, _ -> },
    onCancelDownload: (String) -> Unit,
    onPreviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var isCaptionCopied by remember { mutableStateOf(false) }

    // Dialog rename dan resolusi
    var showRenameDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var pendingDownloadType by remember { mutableStateOf<DownloadType?>(null) }
    var pendingPhotoUrls by remember { mutableStateOf<List<String>?>(null) }
    var customFileNameInput by remember { mutableStateOf("") }

    val gradientBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
            startY = 100f
        )
    }

    // Auto-reset status copy caption setelah 2.5 detik
    LaunchedEffect(isCaptionCopied) {
        if (isCaptionCopied) {
            delay(2500)
            isCaptionCopied = false
        }
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

    fun triggerDownload(type: DownloadType) {
        if (askRename) {
            pendingDownloadType = type
            pendingPhotoUrls = null
            customFileNameInput = media.title.take(40)
            showRenameDialog = true
        } else {
            onDownloadClick(type, null)
        }
    }

    fun triggerDownloadPhotos(urls: List<String>) {
        if (askRename) {
            pendingDownloadType = null
            pendingPhotoUrls = urls
            customFileNameInput = media.title.take(40)
            showRenameDialog = true
        } else {
            onDownloadSelectedPhotos(urls, null)
        }
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

            // Caption / Deskripsi & Tombol Salin Caption
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = {
                        val textToCopy = media.description.ifBlank { media.title }
                        clipboardManager.setText(AnnotatedString(textToCopy))
                        isCaptionCopied = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isCaptionCopied) Color(0xFF00E676).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(
                        imageVector = if (isCaptionCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Salin Caption",
                        tint = if (isCaptionCopied) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCaptionCopied) "Disalin!" else "Salin",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCaptionCopied) Color(0xFF00E676) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Pilihan Resolusi Video (bisa diganti manual atau auto)
            if (media.videoUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = TikTokCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kualitas:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    AssistChip(
                        onClick = { showResolutionDialog = true },
                        label = {
                            Text(
                                text = if (isAutoResolution) "${media.selectedQuality} (Auto)" else media.selectedQuality,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Ubah Resolusi",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }

            // Opsi Pemilihan Audio / Dubbing (Jika ada multi-track audio atau auto-dubbing AI dari YouTube)
            if (media.availableAudioTracks.size > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = TikTokPink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Audio:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val selectedTrack = media.selectedAudioTrack
                    AssistChip(
                        onClick = { showAudioTrackDialog = true },
                        label = {
                            Text(
                                text = selectedTrack?.displayName ?: "Suara Asli",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Pilih Audio / Dubbing",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                        onClick = { triggerDownload(DownloadType.VIDEO) },
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
                        onClick = { triggerDownload(DownloadType.HD_VIDEO) },
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
                            triggerDownloadPhotos(selectedUrls)
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
                        onClick = { triggerDownload(DownloadType.AUDIO) },
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

                // Fallback Button: Jika videoUrl kosong dan list images kosong, sediakan opsi unduh foto cover/preview
                if (media.videoUrl.isBlank() && media.images.isEmpty() && media.coverUrl.isNotBlank()) {
                    Button(
                        onClick = {
                            triggerDownloadPhotos(listOf(media.coverUrl))
                        },
                        enabled = !isAnyDownloading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TikTokPink)
                    ) {
                        if (isDownloadingPhotos) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sedang Mengunduh Foto...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download Foto Postingan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Dialog Ganti Nama Berkas (Rename File Dialog)
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            icon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = TikTokPink) },
            title = { Text("Beri Nama Berkas", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Sesuaikan nama file sebelum diunduh ke galeri:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customFileNameInput,
                        onValueChange = { customFileNameInput = it },
                        label = { Text("Nama Berkas") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRenameDialog = false
                        val finalName = customFileNameInput.ifBlank { media.title }
                        if (pendingDownloadType != null) {
                            onDownloadClick(pendingDownloadType!!, finalName)
                        } else if (pendingPhotoUrls != null) {
                            onDownloadSelectedPhotos(pendingPhotoUrls!!, finalName)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TikTokPink)
                ) {
                    Text("Unduh Sekarang")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Pemilihan Resolusi Video
    if (showResolutionDialog) {
        AlertDialog(
            onDismissRequest = { showResolutionDialog = false },
            icon = { Icon(Icons.Default.HighQuality, contentDescription = null, tint = TikTokCyan) },
            title = { Text("Pilih Kualitas Video", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val resolutions = listOf(
                        "1080p" to "Full HD (Kualitas Tertinggi)",
                        "720p" to "HD (Standar Jernih)",
                        "480p" to "SD (Hemat Kuota)",
                        "360p" to "Low (Ukuran Paling Ringan)"
                    )
                    resolutions.forEach { (res, label) ->
                        val isSelected = media.selectedQuality.startsWith(res)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) TikTokCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) TikTokCyan else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showResolutionDialog = false
                                    onChangeResolution(res)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        showResolutionDialog = false
                                        onChangeResolution(res)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = res, fontWeight = FontWeight.Bold)
                                    Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showResolutionDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Dialog Pemilihan Audio / Dubbing
    if (showAudioTrackDialog) {
        AlertDialog(
            onDismissRequest = { showAudioTrackDialog = false },
            icon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = TikTokPink) },
            title = { Text("Pilih Audio / Dubbing", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    media.availableAudioTracks.forEach { track ->
                        val currentSelectedId = media.selectedAudioTrackId ?: media.selectedAudioTrack?.id
                        val isSelected = track.id == currentSelectedId
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) TikTokPink.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) TikTokPink else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAudioTrackDialog = false
                                    onChangeAudioTrack(track.id)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        showAudioTrackDialog = false
                                        onChangeAudioTrack(track.id)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = track.displayName, fontWeight = FontWeight.Bold)
                                    val desc = when {
                                        track.isOriginal -> "Suara asli bawaan video (Bukan AI)"
                                        track.isAutoDubbed -> "Alih suara otomatis oleh AI YouTube"
                                        else -> "Trek audio alternatif"
                                    }
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAudioTrackDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}
