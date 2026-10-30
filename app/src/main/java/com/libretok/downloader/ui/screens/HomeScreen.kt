package com.libretok.downloader.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val urlInput by viewModel.urlInput.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val mediaPreview by viewModel.mediaPreview.collectAsState()
    val clipboardDetectedUrl by viewModel.clipboardDetectedUrl.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()

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

        // Input URL Field Card
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

                    // Supported Platform Chips - Scrollable to never collide on small screens
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
                    onDownloadClick = { type -> viewModel.startDownload(type) },
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
