package com.libretok.downloader.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libretok.downloader.BuildConfig
import com.libretok.downloader.ui.theme.TikTokCyan
import com.libretok.downloader.ui.theme.TikTokPink
import com.libretok.downloader.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val askRename by viewModel.settingsManager.askRenameBeforeDownload.collectAsState()
    val isAutoResolution by viewModel.settingsManager.isAutoResolution.collectAsState()
    val defaultResolution by viewModel.settingsManager.defaultResolution.collectAsState()

    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateCheckMessage by viewModel.updateCheckMessage.collectAsState()

    var showResolutionPicker by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pengaturan & Info",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Free & Open Source Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = TikTokPink.copy(alpha = 0.12f)
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(TikTokPink),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "100% Gratis & Open Source",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TikTokPink
                                )
                                Text(
                                    text = "Download video, musik & foto tanpa watermark & iklan",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Pengaturan Unduhan (Opsi Resolusi & Rename File)
            item {
                Text(
                    text = "Preferensi Unduhan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Switch Rename File
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFileRenameOutline,
                                contentDescription = null,
                                tint = TikTokCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tanya Nama Berkas (Rename)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (askRename) "Konfirmasi nama berkas kustom sebelum mengunduh" else "Langsung unduh dengan judul asli secara otomatis",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = askRename,
                                onCheckedChange = { viewModel.settingsManager.setAskRenameBeforeDownload(it) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Switch Auto-Resolution
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.HighQuality,
                                contentDescription = null,
                                tint = TikTokCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pilihan Resolusi Otomatis",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isAutoResolution) "Otomatis mengunduh dengan resolusi default ($defaultResolution)" else "Resolusi manual (dapat diganti langsung di pratinjau)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAutoResolution,
                                onCheckedChange = { viewModel.settingsManager.setAutoResolution(it) }
                            )
                        }

                        if (isAutoResolution) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            // Pilihan Default Resolution
                            SettingsClickableRow(
                                icon = Icons.Default.SettingsSuggest,
                                title = "Kualitas Video Default",
                                value = "$defaultResolution ${if (defaultResolution == "1080p") "(Full HD)" else if (defaultResolution == "720p") "(HD)" else ""}",
                                actionHint = "Ubah Kualitas",
                                onClick = { showResolutionPicker = true }
                            )
                        }
                    }
                }
            }

            // Pembaruan Aplikasi
            item {
                Text(
                    text = "Pembaruan Aplikasi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isCheckingUpdate) {
                                    viewModel.checkForUpdate(isManual = true)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Periksa Pembaruan",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = when {
                                        isCheckingUpdate -> "Memeriksa rilis terbaru di GitHub..."
                                        updateCheckMessage != null -> updateCheckMessage!!
                                        else -> "Tekan untuk memeriksa versi terbaru LibreTok"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (updateCheckMessage != null) TikTokCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Cek",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Developer & License Info
            item {
                Text(
                    text = "Developer & Lisensi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsClickableRow(
                            icon = Icons.Default.Person,
                            title = "Nama Developer",
                            value = "sunandar3221",
                            actionHint = "Kunjungi Profil",
                            onClick = { openUrl("https://github.com/sunandar3221") }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        SettingsClickableRow(
                            icon = Icons.Default.Code,
                            title = "Repositori GitHub",
                            value = "github.com/sunandar3221/LibreTok",
                            actionHint = "Buka Repo",
                            onClick = { openUrl("https://github.com/sunandar3221/LibreTok") }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        SettingsClickableRow(
                            icon = Icons.Default.Gavel,
                            title = "Lisensi Software",
                            value = "GNU General Public License v3.0 (GPLv3)",
                            actionHint = "Lihat Lisensi",
                            onClick = { openUrl("https://www.gnu.org/licenses/gpl-3.0.html") }
                        )
                    }
                }
            }

            // Supported Platforms Card
            item {
                Text(
                    text = "Platform yang Didukung",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        FeatureHighlightRow(
                            icon = Icons.Default.PlayCircle,
                            title = "TikTok",
                            desc = "Video No Watermark (SD & HD), Audio MP3, dan Foto Slideshow"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        FeatureHighlightRow(
                            icon = Icons.Default.CameraAlt,
                            title = "Instagram",
                            desc = "Reels, Video Post, Audio MP3, dan Carousel"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        FeatureHighlightRow(
                            icon = Icons.Default.Subscriptions,
                            title = "YouTube",
                            desc = "Video Standar & HD, YouTube Shorts, serta Ekstrak MP3"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        FeatureHighlightRow(
                            icon = Icons.Default.ThumbUp,
                            title = "Facebook",
                            desc = "Facebook Watch, Reels, Video Publik, dan Audio MP3"
                        )
                    }
                }
            }

            // Storage Details
            item {
                Text(
                    text = "Penyimpanan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsInfoRow(
                            icon = Icons.Default.Movie,
                            title = "Folder Video",
                            value = "Penyimpanan Utama > Movies > LibreTok"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        SettingsInfoRow(
                            icon = Icons.Default.MusicNote,
                            title = "Folder Audio MP3",
                            value = "Penyimpanan Utama > Music > LibreTok"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        SettingsInfoRow(
                            icon = Icons.Default.PhotoLibrary,
                            title = "Folder Foto",
                            value = "Penyimpanan Utama > Pictures > LibreTok"
                        )
                    }
                }
            }

            // About App
            item {
                Text(
                    text = "Tentang Aplikasi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsInfoRow(
                            icon = Icons.Default.Info,
                            title = "Versi Aplikasi",
                            value = "LibreTok v${BuildConfig.VERSION_NAME} (Signed Release)"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        SettingsInfoRow(
                            icon = Icons.Default.Android,
                            title = "Desain UI Framework",
                            value = "Google Jetpack Compose (Material 3 Native)"
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Dialog Pemilihan Default Resolution
    if (showResolutionPicker) {
        val resolutionOptions = listOf(
            "1080p" to "1080p (Full HD - Rekomendasi)",
            "720p" to "720p (HD Standar)",
            "480p" to "480p (Hemat Kuota)",
            "360p" to "360p (Ukuran Terkecil)"
        )
        AlertDialog(
            onDismissRequest = { showResolutionPicker = false },
            title = {
                Text("Pilih Kualitas Video Default", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    resolutionOptions.forEach { (resKey, resLabel) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (defaultResolution == resKey) TikTokCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (defaultResolution == resKey) TikTokCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.settingsManager.setDefaultResolution(resKey)
                                    showResolutionPicker = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = defaultResolution == resKey,
                                    onClick = {
                                        viewModel.settingsManager.setDefaultResolution(resKey)
                                        showResolutionPicker = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = resLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (defaultResolution == resKey) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResolutionPicker = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    value: String,
    actionHint: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = actionHint,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FeatureHighlightRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TikTokCyan,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
