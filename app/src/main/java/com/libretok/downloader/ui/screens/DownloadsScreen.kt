package com.libretok.downloader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.libretok.downloader.data.model.DownloadItem
import com.libretok.downloader.data.model.DownloadType
import com.libretok.downloader.ui.components.DownloadItemCard
import com.libretok.downloader.ui.viewmodel.HistoryFilter
import com.libretok.downloader.ui.viewmodel.MainViewModel
import com.libretok.downloader.ui.viewmodel.PlayerTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.downloadsHistory.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filteredItems = remember(items, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> items
            HistoryFilter.VIDEOS -> items.filter { it.type == DownloadType.VIDEO || it.type == DownloadType.HD_VIDEO }
            HistoryFilter.AUDIOS -> items.filter { it.type == DownloadType.AUDIO }
            HistoryFilter.PHOTOS -> items.filter { it.type == DownloadType.PHOTO }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Riwayat Unduhan",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Hapus Semua")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(HistoryFilter.values()) { filter ->
                    val label = when (filter) {
                        HistoryFilter.ALL -> "Semua (${items.size})"
                        HistoryFilter.VIDEOS -> "Video (${items.count { it.type == DownloadType.VIDEO || it.type == DownloadType.HD_VIDEO }})"
                        HistoryFilter.AUDIOS -> "Audio MP3 (${items.count { it.type == DownloadType.AUDIO }})"
                        HistoryFilter.PHOTOS -> "Foto (${items.count { it.type == DownloadType.PHOTO }})"
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(label) }
                    )
                }
            }

            if (filteredItems.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Belum Ada Berkas Unduhan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Salin link video atau foto (TikTok, Instagram, YouTube, Facebook) dan unduh secara gratis.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        DownloadItemCard(
                            item = item,
                            onPlayClick = {
                                if (item.type == DownloadType.PHOTO) {
                                    viewModel.openInSystem(item)
                                } else {
                                    viewModel.openPlayer(PlayerTarget.LocalFile(item))
                                }
                            },
                            onShareClick = {
                                viewModel.shareItem(item)
                            },
                            onOpenSystemClick = {
                                viewModel.openInSystem(item)
                            },
                            onDeleteClick = {
                                viewModel.deleteDownloadedItem(item.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Hapus Semua Riwayat Unduhan?") },
            text = { Text("Semua catatan dan berkas unduhan akan dihapus dari aplikasi ini.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("Hapus Semua", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
