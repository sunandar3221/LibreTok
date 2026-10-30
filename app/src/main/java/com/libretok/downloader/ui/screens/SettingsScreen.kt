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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libretok.downloader.ui.theme.TikTokCyan
import com.libretok.downloader.ui.theme.TikTokPink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
            // Free & Open Source Card
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
                                    text = "Download video & audio tanpa iklan, tanpa batas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            value = "LibreTok v1.0.1 (Signed Release)"
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
