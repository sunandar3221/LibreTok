package com.libretok.downloader.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
                                    text = "100% Gratis & Tanpa Iklan",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TikTokPink
                                )
                                Text(
                                    text = "Semua fitur AhaTok Downloader tanpa batasan",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                    }
                }
            }

            // Key Features Section
            item {
                Text(
                    text = "Fitur Unggulan",
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
                            icon = Icons.Default.HighQuality,
                            title = "Tanpa Watermark & Kualitas HD",
                            desc = "Video bersih tanpa logo TikTok dan tanpa kompresi berlebih."
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        FeatureHighlightRow(
                            icon = Icons.Default.Audiotrack,
                            title = "Ekstrak Musik & Suara ke MP3",
                            desc = "Ambil file musik TikTok langsung dalam format audio MP3."
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        FeatureHighlightRow(
                            icon = Icons.Default.Share,
                            title = "Dukungan Share Sheet TikTok",
                            desc = "Klik Bagikan langsung dari TikTok lalu pilih LibreTok."
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        FeatureHighlightRow(
                            icon = Icons.Default.Security,
                            title = "Privasi Terjamin",
                            desc = "Tidak memerlukan login akun, bebas pelacak dan iklan."
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
                            value = "LibreTok v1.0.0 (Native Jetpack Compose)"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        SettingsInfoRow(
                            icon = Icons.Default.Android,
                            title = "Desain UI",
                            value = "Jetpack Compose Material 3 Asli Google"
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
