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
import com.libretok.downloader.ui.localization.AppLanguage
import com.libretok.downloader.ui.localization.LocalAppStrings
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
    val strings = LocalAppStrings.current

    val currentLanguage by viewModel.appLanguage.collectAsState()
    val askRename by viewModel.settingsManager.askRenameBeforeDownload.collectAsState()
    val isAutoResolution by viewModel.settingsManager.isAutoResolution.collectAsState()
    val defaultResolution by viewModel.settingsManager.defaultResolution.collectAsState()

    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateCheckMessage by viewModel.updateCheckMessage.collectAsState()

    var showLanguagePicker by remember { mutableStateOf(false) }
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
                        text = strings.settingsTitle,
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
                                    text = strings.freeOpenSource,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TikTokPink
                                )
                                Text(
                                    text = strings.freeOpenSourceDesc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Bahasa Aplikasi (Language)
            item {
                Text(
                    text = strings.sectionLanguage,
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
                            icon = Icons.Default.Language,
                            title = strings.languageTitle,
                            value = AppLanguage.fromCode(currentLanguage).nativeName + if (currentLanguage == "system") " (${strings.systemDefault})" else "",
                            actionHint = strings.languageTitle,
                            onClick = { showLanguagePicker = true }
                        )
                    }
                }
            }

            // Pengaturan Unduhan (Opsi Resolusi & Rename File)
            item {
                Text(
                    text = strings.sectionDownloadPref,
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
                                    text = strings.askRenameTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = strings.askRenameDesc,
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
                                    text = strings.autoResTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = strings.autoResDesc,
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
                                title = strings.defaultResTitle,
                                value = "$defaultResolution ${if (defaultResolution == "1080p") "(Full HD)" else if (defaultResolution == "720p") "(HD)" else ""}",
                                actionHint = strings.changeQualityHint,
                                onClick = { showResolutionPicker = true }
                            )
                        }
                    }
                }
            }

            // Pembaruan Aplikasi
            item {
                Text(
                    text = strings.sectionUpdates,
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
                                    text = strings.checkUpdateTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = when {
                                        isCheckingUpdate -> strings.checkingUpdate
                                        updateCheckMessage != null -> updateCheckMessage!!
                                        else -> strings.checkUpdateHint
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
                                    contentDescription = strings.checkUpdateTitle,
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
                    text = strings.sectionDev,
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
                            title = strings.devName,
                            value = "sunandar3221",
                            actionHint = strings.visitProfile,
                            onClick = { openUrl("https://github.com/sunandar3221") }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        SettingsClickableRow(
                            icon = Icons.Default.Code,
                            title = strings.githubRepo,
                            value = "github.com/sunandar3221/LibreTok",
                            actionHint = strings.openRepo,
                            onClick = { openUrl("https://github.com/sunandar3221/LibreTok") }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        SettingsClickableRow(
                            icon = Icons.Default.Gavel,
                            title = strings.appLicense,
                            value = "GNU General Public License v3.0 (GPLv3)",
                            actionHint = strings.viewLicense,
                            onClick = { openUrl("https://www.gnu.org/licenses/gpl-3.0.html") }
                        )
                    }
                }
            }

            // Supported Platforms Card
            item {
                Text(
                    text = strings.supportedPlatforms,
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
                            desc = strings.platformTikTokDesc
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        FeatureHighlightRow(
                            icon = Icons.Default.CameraAlt,
                            title = "Instagram",
                            desc = strings.platformInstagramDesc
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        FeatureHighlightRow(
                            icon = Icons.Default.Subscriptions,
                            title = "YouTube",
                            desc = strings.platformYouTubeDesc
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        FeatureHighlightRow(
                            icon = Icons.Default.ThumbUp,
                            title = "Facebook",
                            desc = strings.platformFacebookDesc
                        )
                    }
                }
            }

            // Storage Details
            item {
                Text(
                    text = strings.sectionStorage,
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
                            title = strings.folderVideo,
                            value = "Movies > LibreTok"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        SettingsInfoRow(
                            icon = Icons.Default.MusicNote,
                            title = strings.folderAudio,
                            value = "Music > LibreTok"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        SettingsInfoRow(
                            icon = Icons.Default.PhotoLibrary,
                            title = strings.folderPhoto,
                            value = "Pictures > LibreTok"
                        )
                    }
                }
            }

            // About App
            item {
                Text(
                    text = strings.sectionAbout,
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
                            title = strings.appVersion,
                            value = "LibreTok v${BuildConfig.VERSION_NAME} (Signed Release)"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        SettingsInfoRow(
                            icon = Icons.Default.Android,
                            title = strings.uiFramework,
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

    // Dialog Pemilihan Bahasa Aplikasi
    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = {
                Text(strings.languageDialogTitle, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.values().forEach { lang ->
                        val isSelected = currentLanguage == lang.code
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) TikTokCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) TikTokCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAppLanguage(lang.code)
                                    showLanguagePicker = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setAppLanguage(lang.code)
                                        showLanguagePicker = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (lang == AppLanguage.SYSTEM) "${lang.nativeName} (${strings.systemDefault})" else lang.nativeName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguagePicker = false }) {
                    Text(strings.btnClose)
                }
            }
        )
    }

    // Dialog Pemilihan Default Resolution
    if (showResolutionPicker) {
        val resolutionOptions = listOf(
            "1080p" to "1080p (Full HD)",
            "720p" to "720p (HD)",
            "480p" to "480p (SD)",
            "360p" to "360p (Low)"
        )
        AlertDialog(
            onDismissRequest = { showResolutionPicker = false },
            title = {
                Text(strings.dialogSelectQuality, fontWeight = FontWeight.Bold)
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
                    Text(strings.btnClose)
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
