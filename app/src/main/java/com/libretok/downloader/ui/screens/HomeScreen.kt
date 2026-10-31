package com.libretok.downloader.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libretok.downloader.data.model.ActiveDownload
import com.libretok.downloader.data.model.DownloadStatus
import com.libretok.downloader.ui.components.MediaPreviewCard
import com.libretok.downloader.ui.theme.TikTokCyan
import com.libretok.downloader.ui.theme.TikTokPink
import com.libretok.downloader.ui.viewmodel.MainViewModel
import com.libretok.downloader.ui.viewmodel.PlayerTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToDownloads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isOnline by viewModel.isOnline.collectAsState()
    val downloadsHistory by viewModel.downloadsHistory.collectAsState()

    val urlInput by viewModel.urlInput.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val mediaPreview by viewModel.mediaPreview.collectAsState()
    val clipboardDetectedUrl by viewModel.clipboardDetectedUrl.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()

    val askRename by viewModel.settingsManager.askRenameBeforeDownload.collectAsState()
    val isAutoResolution by viewModel.settingsManager.isAutoResolution.collectAsState()
    val batchQueue by viewModel.batchQueue.collectAsState()
    val isBatchRunning by viewModel.isBatchRunning.collectAsState()

    var isBatchMode by remember { mutableStateOf(false) }
    var batchInputText by remember { mutableStateOf("") }

    val logoBrush = remember { Brush.linearGradient(listOf(TikTokPink, TikTokCyan)) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
    ) {
        item(key = "header") {
            // App Brand Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(logoBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "LibreTok",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TikTokPink.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "100% GRATIS",
                                color = TikTokPink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "TikTok, IG, YouTube & Facebook Downloader",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!isOnline) {
            // Tampilan Khusus Offline saat tidak ada koneksi internet di Tab Beranda
            item(key = "offline_special_view") {
                OfflineStateCard(
                    offlineMediaCount = downloadsHistory.size,
                    onRetry = { viewModel.refreshNetworkStatus() },
                    onGoToDownloads = onNavigateToDownloads
                )
            }
        } else {
            // Auto Clipboard Detection Banner
            item(key = "clipboard_banner") {
            AnimatedVisibility(
                visible = clipboardDetectedUrl != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = TikTokCyan.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Link Media Terdeteksi!",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Ingin langsung memeriksa tautan ini?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { viewModel.applyDetectedUrl() },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Unduh")
                        }
                        IconButton(onClick = { viewModel.dismissClipboardBanner() }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }
            }
        }

        // Input URL Field Card (Mode Tunggal & Unduh Massal)
        item(key = "input_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Mode Selector Tab (Link Tunggal vs Unduh Massal)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (!isBatchMode) TikTokPink else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isBatchMode = false }
                        ) {
                            Text(
                                text = "Link Tunggal",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (!isBatchMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isBatchMode) TikTokCyan else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isBatchMode = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = if (isBatchMode) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Unduh Massal",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isBatchMode) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!isBatchMode) {
                        // MODE TUNGGAL
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { viewModel.onUrlChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Tempel link TikTok, IG, YouTube, atau FB...") },
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null)
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (urlInput.isNotBlank()) {
                                        IconButton(onClick = { viewModel.clearUrl() }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Hapus")
                                        }
                                    } else {
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                            if (!clip.isNullOrBlank()) {
                                                viewModel.onUrlChange(clip)
                                                viewModel.fetchMedia(clip)
                                            }
                                        }) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "Tempel")
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Supported Platform Chips
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(listOf("TikTok", "Instagram", "YouTube", "Facebook")) { plat ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                ) {
                                    Text(
                                        text = plat,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.fetchMedia() },
                            enabled = !isLoading && urlInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TikTokPink)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Memproses Link...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Periksa & Unduh Video", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // MODE UNDUH MASSAL (BATCH DOWNLOAD)
                        OutlinedTextField(
                            value = batchInputText,
                            onValueChange = { batchInputText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            placeholder = { Text("Tempel daftar link di sini (satu link per baris)...") },
                            shape = RoundedCornerShape(14.dp),
                            maxLines = 6
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val extractedUrls = remember(batchInputText) {
                            batchInputText.lines()
                                .map { it.trim() }
                                .filter { it.startsWith("http://") || it.startsWith("https://") }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (!clip.isNullOrBlank()) {
                                        batchInputText = if (batchInputText.isBlank()) clip else "$batchInputText\n$clip"
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tempel Link", fontSize = 12.sp)
                            }

                            if (batchInputText.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { batchInputText = "" },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Bersihkan", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                viewModel.startBatchDownload(extractedUrls)
                            },
                            enabled = !isBatchRunning && extractedUrls.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TikTokCyan)
                        ) {
                            if (isBatchRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Sedang Mengunduh Massal...", color = Color.Black, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (extractedUrls.isEmpty()) "Masukkan Minimal 1 Link" else "Unduh Sekaligus (${extractedUrls.size} Link)",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Antrean Unduhan Massal (Batch Download Queue Card)
        if (batchQueue.isNotEmpty()) {
            item(key = "batch_queue_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, TikTokCyan.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = TikTokCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            val doneCount = batchQueue.count { it.status == com.libretok.downloader.data.model.BatchItemStatus.SUCCESS }
                            Text(
                                text = "Antrean Unduhan Massal ($doneCount/${batchQueue.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (isBatchRunning) {
                                TextButton(onClick = { viewModel.cancelBatchDownload() }) {
                                    Text("Batalkan", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            } else {
                                TextButton(onClick = { viewModel.clearBatchQueue() }) {
                                    Text("Tutup", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            batchQueue.forEachIndexed { idx, item ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "#${idx + 1}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title.ifBlank { item.url },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val statusText = when (item.status) {
                                                com.libretok.downloader.data.model.BatchItemStatus.WAITING -> "Menunggu antrean..."
                                                com.libretok.downloader.data.model.BatchItemStatus.FETCHING -> "Memeriksa tautan..."
                                                com.libretok.downloader.data.model.BatchItemStatus.DOWNLOADING -> "Sedang mengunduh..."
                                                com.libretok.downloader.data.model.BatchItemStatus.SUCCESS -> "Berhasil disimpan ke Galeri"
                                                com.libretok.downloader.data.model.BatchItemStatus.FAILED -> item.errorMessage ?: "Gagal"
                                            }
                                            Text(
                                                text = statusText,
                                                fontSize = 11.sp,
                                                color = when (item.status) {
                                                    com.libretok.downloader.data.model.BatchItemStatus.SUCCESS -> Color(0xFF00E676)
                                                    com.libretok.downloader.data.model.BatchItemStatus.FAILED -> MaterialTheme.colorScheme.error
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        when (item.status) {
                                            com.libretok.downloader.data.model.BatchItemStatus.WAITING -> {
                                                Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                            }
                                            com.libretok.downloader.data.model.BatchItemStatus.FETCHING,
                                            com.libretok.downloader.data.model.BatchItemStatus.DOWNLOADING -> {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = TikTokCyan)
                                            }
                                            com.libretok.downloader.data.model.BatchItemStatus.SUCCESS -> {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(20.dp))
                                            }
                                            com.libretok.downloader.data.model.BatchItemStatus.FAILED -> {
                                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
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

        // Error message banner
        if (errorMessage != null) {
            item(key = "error_banner") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Media Preview Card (Progress bar is located directly inside here, above download buttons!)
        if (mediaPreview != null) {
            item(key = "media_preview_${mediaPreview!!.id}") {
                MediaPreviewCard(
                    media = mediaPreview!!,
                    activeDownloads = activeDownloads,
                    askRename = askRename,
                    isAutoResolution = isAutoResolution,
                    onChangeResolution = { res -> viewModel.changeMediaResolution(res) },
                    onChangeAudioTrack = { trackId -> viewModel.selectAudioTrack(trackId) },
                    onDownloadClick = { type, customName -> viewModel.startDownload(type, customName) },
                    onDownloadSelectedPhotos = { urls, customName -> viewModel.downloadSelectedPhotos(urls, customName) },
                    onCancelDownload = { downloadId -> viewModel.cancelDownload(downloadId) },
                    onPreviewClick = {
                        viewModel.openPlayer(PlayerTarget.StreamPreview(mediaPreview!!))
                    }
                )
            }
        }

        // "Cara Pakai" (How to use) Step-by-step Guide
        item(key = "how_to_guide") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cara Menggunakan LibreTok",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    HowToStepItem(
                        number = "1",
                        title = "Buka TikTok, IG, YT, atau Facebook",
                        desc = "Tekan tombol 'Bagikan' (Share) pada video lalu pilih 'Salin Tautan' (Copy Link)."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    HowToStepItem(
                        number = "2",
                        title = "Tempel di LibreTok",
                        desc = "Buka LibreTok, link akan otomatis terdeteksi atau tekan tombol 'Tempel'."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    HowToStepItem(
                        number = "3",
                        title = "Unduh Tanpa Watermark!",
                        desc = "Pilih unduh video tanpa watermark, full HD, atau audio MP3 gratis."
                    )
                }
            }
        }
    }
}
}

@Composable
fun OfflineStateCard(
    offlineMediaCount: Int,
    onRetry: () -> Unit,
    onGoToDownloads: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(TikTokPink.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = "Offline",
                    tint = TikTokPink,
                    modifier = Modifier.size(42.dp)
                )
            }

            Text(
                text = "Tidak Ada Koneksi Internet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "LibreTok memerlukan koneksi internet untuk mengunduh video dan foto baru. Anda tetap dapat memutar semua video & audio yang sudah pernah diunduh secara offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TikTokCyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Coba Hubungkan Lagi", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Pintasan ke Tab Unduhan untuk memutar berkas offline
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGoToDownloads() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(TikTokPink.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TikTokPink,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Buka Riwayat Unduhan (Offline)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (offlineMediaCount > 0) "$offlineMediaCount berkas tersimpan siap diputar tanpa kuota" else "Buka galeri unduhan Anda",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun HowToStepItem(
    number: String,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(TikTokPink.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontWeight = FontWeight.Bold,
                color = TikTokPink,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
