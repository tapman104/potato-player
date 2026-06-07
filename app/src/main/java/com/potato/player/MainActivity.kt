package com.potato.player.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager // [CHANGE 43]
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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

import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import com.potato.player.PlaybackService
import com.potato.player.files.preferences.AppPreferences
import com.potato.player.ui.theme.VoraPlayerTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {

    private lateinit var appPreferences: AppPreferences

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var viewModelState by mutableStateOf<PlayerViewModel?>(null)

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
        appPreferences = AppPreferences(applicationContext)

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            val controller = mediaControllerFuture?.get() ?: return@addListener
            viewModelState = PlayerViewModel(ExoPlayerEngine(controller))
        }, ContextCompat.getMainExecutor(this))

        setContent {
            val themeSelection by appPreferences.themeSelection.collectAsState()
            val dynamicColor by appPreferences.dynamicColor.collectAsState()

            val isDarkTheme = when (themeSelection) {
                0 -> false
                1 -> true
                else -> isSystemInDarkTheme()
            }

            VoraPlayerTheme(darkTheme = isDarkTheme, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var mediaUri by rememberSaveable { mutableStateOf(resolveMediaUri(intent)?.toString()) }
                    var settingsRoute by rememberSaveable { mutableStateOf<String?>(null) }
                    val viewModel = viewModelState
                    if (viewModel == null) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                        return@Surface
                    }
                    val player = viewModel.playerViewPlayer
                    val actualUri = mediaUri?.let(Uri::parse)
                    if (actualUri != null && player != null) {
                        PlayerScreen(
                            viewModel = viewModel,
                            player = player,
                            uri = actualUri,
                            onBack = { finish() },
                            onPlayerViewReady = { pv -> playerViewRef = pv },
                        )
                    } else {
                        HomeScreen(
                            onFilePicked = { uri -> mediaUri = uri.toString() },
                            onSettingsClick = { settingsRoute = "settings" },
                        )
                    }

                    BackHandler(enabled = settingsRoute != null) {
                        if (settingsRoute == "settings") settingsRoute = null else settingsRoute = "settings"
                    }

                    when (settingsRoute) {
                        "about" -> AboutScreen(onBack = { settingsRoute = "settings" })
                        "appearance" -> AppearanceSettingsScreen(
                            onBack = { settingsRoute = "settings" },
                            appPreferences = appPreferences
                        )
                        "subtitle_appearance" -> com.potato.player.files.ui.settings.SubtitleAppearanceSettingsScreen(
                            onBack = { settingsRoute = "settings" },
                            appPreferences = appPreferences,
                        )
                        "gestures" -> GestureSettingsScreen(onBack = { settingsRoute = "settings" })
                        "settings" -> SettingsScreen(
                            onBack = { settingsRoute = null },
                            onGesturesClick = { settingsRoute = "gestures" },
                            onAppearanceClick = { settingsRoute = "appearance" },
                            onSubtitleAppearanceClick = { settingsRoute = "subtitle_appearance" },
                            onAboutClick = { settingsRoute = "about" },
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
        viewModelState?.onForeground()
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerViewRef?.onPause()  // disables PlayerView rendering cleanly
        // Intentionally skipping viewModelState?.onBackground() to allow background audio
        if (wakeLock.isHeld) wakeLock.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
    }

    private fun resolveMediaUri(intent: Intent?): Uri? {
        val dataUri = intent?.data
        if (dataUri != null) return dataUri

        val rawExtra = intent?.getStringExtra(EXTRA_MEDIA_URI)
        return rawExtra?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }

    // Factory removed as we now instantiate PlayerViewModel directly with the MediaController

    companion object {
        const val EXTRA_MEDIA_URI = "media_uri"
    }
}

