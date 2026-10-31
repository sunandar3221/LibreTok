package com.libretok.downloader.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.libretok.downloader.data.model.DownloadType
import com.libretok.downloader.ui.components.VideoPlayerDialog
import com.libretok.downloader.ui.theme.TikTokCyan
import com.libretok.downloader.ui.theme.TikTokPink
import com.libretok.downloader.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

sealed class Screen(val route: String, val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    object Home : Screen("home", "Beranda", Icons.Filled.Home, Icons.Outlined.Home)
    object Downloads : Screen("downloads", "Unduhan", Icons.Filled.Download, Icons.Outlined.Download)
    object Settings : Screen("settings", "Pengaturan", Icons.Filled.Settings, Icons.Outlined.Settings)
}

private val navScreens = listOf(
    Screen.Home,
    Screen.Downloads,
    Screen.Settings
)

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val activePlayerTarget by viewModel.activePlayerTarget.collectAsState()
    val downloadFinishedItem by viewModel.downloadFinishedItem.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()

    // Auto-dismiss bottom popup after 7 seconds
    LaunchedEffect(downloadFinishedItem) {
        if (downloadFinishedItem != null) {
            delay(7000)
            viewModel.dismissDownloadFinishedEvent()
        }
    }

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Bottom Pop-up Banner saat unduhan selesai
                AnimatedVisibility(
                    visible = downloadFinishedItem != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    downloadFinishedItem?.let { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 8.dp,
                            tonalElevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF00E676).copy(alpha = 0.15f),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF00E676),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Download Selesai!",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = item.title.ifBlank { "Berkas telah disimpan" },
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.dismissDownloadFinishedEvent() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Tutup",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.openInSystem(item)
                                            viewModel.dismissDownloadFinishedEvent()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        val icon = if (item.type == DownloadType.AUDIO) {
                                            Icons.Default.Audiotrack
                                        } else {
                                            Icons.Default.PhotoLibrary
                                        }
                                        val label = if (item.type == DownloadType.AUDIO) "Buka Musik" else "Lihat di Galeri"
                                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Button(
                                        onClick = {
                                            if (currentRoute != Screen.Downloads.route) {
                                                navController.navigate(Screen.Downloads.route) {
                                                    popUpTo(navController.graph.startDestinationId) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                            viewModel.dismissDownloadFinishedEvent()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = TikTokPink
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "Lihat Unduhan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    navScreens.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) }
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(viewModel = viewModel)
            }
            composable(Screen.Downloads.route) {
                DownloadsScreen(viewModel = viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }

    // Built-in media player dialog
    activePlayerTarget?.let { target ->
        VideoPlayerDialog(
            target = target,
            onDismissRequest = { viewModel.closePlayer() }
        )
    }

    // In-App Update Dialog
    updateInfo?.let { update ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = TikTokCyan,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Pembaruan Tersedia!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Versi baru ${update.latestVersion} telah dirilis di GitHub.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (update.changelog.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                        ) {
                            Text(
                                text = update.changelog,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Text(
                        text = "Apakah Anda ingin mengunduh pembaruan sekarang?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val downloadUrl = update.downloadUrl.ifBlank { update.releasePageUrl }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                        context.startActivity(intent)
                        viewModel.dismissUpdateDialog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TikTokCyan)
                ) {
                    Text("Update Sekarang", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text("Nanti Saja")
                }
            }
        )
    }
}
