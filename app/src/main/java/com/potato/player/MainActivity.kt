package com.potato.player.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager // [CHANGE 43]
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.ui.PlayerView // [CHANGE 44]
import com.potato.player.player.ui.HomeScreen
import com.potato.player.files.ui.settings.AboutScreen
import com.potato.player.files.ui.settings.AppearanceSettingsScreen
import com.potato.player.files.ui.settings.GestureSettingsScreen
import com.potato.player.files.ui.settings.SettingsScreen
import com.potato.player.player.ui.PlayerScreen
import com.potato.player.player.viewmodel.PlayerViewModel
import mediaengine.ExoPlayerEngine

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels {
        PlayerViewModelFactory(applicationContext)
    }

    // Holds the last known PlayerView to forward Activity lifecycle calls
    // (onResume / onPause) so PlayerView can re-enable rendering correctly.
    // We do NOT use this ref for surface re-attachment — PlayerView manages
    // its own SurfaceHolder internally.
    private var playerViewRef: PlayerView? = null

    // [CHANGE 47] WakeLock acquired while player is in foreground.
    private val wakeLock: PowerManager.WakeLock by lazy { // [CHANGE 48]
        @Suppress("DEPRECATION") // SCREEN_DIM_WAKE_LOCK is the least-invasive lock available // [CHANGE 49]
        (getSystemService(POWER_SERVICE) as PowerManager) // [CHANGE 50]
            .newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "potato:player") // [CHANGE 51]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black,
                ) {
                    var mediaUri by remember { mutableStateOf(resolveMediaUri(intent)) }
                    var showSettings by remember { mutableStateOf(false) }
                    var showGestureSettings by remember { mutableStateOf(false) }
                    var showAppearanceSettings by remember { mutableStateOf(false) }
                    var showAboutScreen by remember { mutableStateOf(false) }
                    val player = viewModel.playerViewPlayer
                    if (mediaUri != null && player != null) {
                        PlayerScreen(
                            viewModel = viewModel,
                            player = player,
                            uri = mediaUri!!,
                            onBack = { finish() },
                            onPlayerViewReady = { pv -> playerViewRef = pv },
                        )
                    } else {
                        HomeScreen(
                            onFilePicked = { uri -> mediaUri = uri },
                            onSettingsClick = { showSettings = true },
                        )
                    }
                    if (showAboutScreen) {
                        AboutScreen(onBack = { showAboutScreen = false })
                    } else if (showAppearanceSettings) {
                        AppearanceSettingsScreen(onBack = { showAppearanceSettings = false })
                    } else if (showGestureSettings) {
                        GestureSettingsScreen(onBack = { showGestureSettings = false })
                    } else if (showSettings) {
                        SettingsScreen(
                            onBack = { showSettings = false },
                            onGesturesClick = { showGestureSettings = true },
                            onAppearanceClick = { showAppearanceSettings = true },
                            onAboutClick = { showAboutScreen = true },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!wakeLock.isHeld) wakeLock.acquire(10 * 60 * 1000L) // 10-min timeout
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerViewRef?.onResume() // re-enables PlayerView rendering
        viewModel.onForeground()
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerViewRef?.onPause()  // disables PlayerView rendering cleanly
        viewModel.onBackground()
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun resolveMediaUri(intent: Intent?): Uri? {
        val dataUri = intent?.data
        if (dataUri != null) return dataUri

        val rawExtra = intent?.getStringExtra(EXTRA_MEDIA_URI)
        return rawExtra?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }

    private class PlayerViewModelFactory(
        private val appContext: android.content.Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
                return PlayerViewModel(ExoPlayerEngine(appContext)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        const val EXTRA_MEDIA_URI = "media_uri"
    }
}

