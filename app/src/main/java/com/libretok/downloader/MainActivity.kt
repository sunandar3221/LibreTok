package com.libretok.downloader

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.libretok.downloader.ui.screens.MainScreen
import com.libretok.downloader.ui.theme.LibreTokTheme
import com.libretok.downloader.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)

        setContent {
            LibreTokTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            delay(350)
            checkClipboard()
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.onUrlChange(sharedText)
                viewModel.fetchMedia(sharedText)
            }
        }
    }

    private fun checkClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
            if (!clip.isNullOrBlank()) {
                viewModel.checkClipboard(clip)
            }
        } catch (ignored: Exception) {
            // Some devices restrict background clipboard access
        }
    }
}
