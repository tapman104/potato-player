package com.potato.player.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle

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
import com.potato.player.home.HomeScreen
import com.potato.player.home.HomeViewModel
import com.potato.player.files.ui.settings.AboutScreen
import com.potato.player.files.ui.settings.AppearanceSettingsScreen
import com.potato.player.files.ui.settings.GestureSettingsScreen
import com.potato.player.files.ui.settings.PlaybackSettingsScreen
import com.potato.player.files.ui.settings.SettingsScreen
import com.potato.player.player.ui.PlayerScreen
import com.potato.player.viewmodel.PlayerViewModel
import com.potato.player.engine.ExoPlayerEngine

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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var appPreferences: AppPreferences

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var viewModelState by mutableStateOf<PlayerViewModel?>(null)
    private var mediaUriState by mutableStateOf<String?>(null)
    private var isExternalLaunch by mutableStateOf(false)
    private val homeViewModel: HomeViewModel by viewModels { HomeViewModel.provideFactory(this) }
    private var isUsingService: Boolean? = null

    // Holds the last known PlayerView to forward Activity lifecycle calls
    // (onResume / onPause) so PlayerView can re-enable rendering correctly.
    // We do NOT use this ref for surface re-attachment — PlayerView manages
    // its own SurfaceHolder internally.
    private var playerViewRef: PlayerView? = null

    // WakeLock removed for better battery efficiency. FLAG_KEEP_SCREEN_ON handles this properly.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appPreferences = AppPreferences(applicationContext)
        
        val initialUri = resolveMediaUri(intent)
        mediaUriState = initialUri?.toString()
        isExternalLaunch = initialUri != null && intent?.action == Intent.ACTION_VIEW

        lifecycleScope.launch {
            appPreferences.enablePlaybackService.collectLatest { useService ->
                if (isUsingService == useService) return@collectLatest
                isUsingService = useService

                val currentPos = viewModelState?.uiState?.value?.positionMs
                val currentUri = mediaUriState

                viewModelState?.release()
                viewModelState = null
                mediaControllerFuture?.let {
                    MediaController.releaseFuture(it)
                    mediaControllerFuture = null
                }

                if (useService) {
                    val sessionToken = SessionToken(this@MainActivity, ComponentName(this@MainActivity, PlaybackService::class.java))
                    val future = MediaController.Builder(this@MainActivity, sessionToken).buildAsync()
                    mediaControllerFuture = future
                    future.addListener({
                        val controller = future.get() ?: return@addListener
                        if (mediaControllerFuture == future) {
                            viewModelState = PlayerViewModel(ExoPlayerEngine(controller))
                            currentUri?.let { uriStr ->
                                viewModelState?.open(Uri.parse(uriStr))
                                if (currentPos != null && currentPos > 0L) {
                                    viewModelState?.seekTo(currentPos)
                                }
                            }
                        } else {
                            MediaController.releaseFuture(future)
                        }
                    }, ContextCompat.getMainExecutor(this@MainActivity))
                } else {
                    val exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(this@MainActivity).build()
                    viewModelState = PlayerViewModel(ExoPlayerEngine(exoPlayer))
                    currentUri?.let { uriStr ->
                        viewModelState?.open(Uri.parse(uriStr))
                        if (currentPos != null && currentPos > 0L) {
                            viewModelState?.seekTo(currentPos)
                        }
                    }
                }
            }
        }

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
                    var settingsRoute by rememberSaveable { mutableStateOf<String?>(null) }
                    val viewModel = viewModelState
                    if (viewModel == null) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                        return@Surface
                    }
                    val player = viewModel.playerViewPlayer
                    val actualUri = mediaUriState?.let(Uri::parse)
                    if (actualUri != null && player != null) {
                        BackHandler(enabled = settingsRoute == null) {
                            viewModelState?.pause()
                            if (isExternalLaunch) finish() else mediaUriState = null
                        }
                        PlayerScreen(
                            viewModel = viewModel,
                            player = player,
                            uri = actualUri,
                            onBack = {
                                viewModelState?.pause()
                                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                if (isExternalLaunch) finish() else mediaUriState = null
                            },
                            onPlayerViewReady = { pv -> playerViewRef = pv },
                        )
                    } else {
                        BackHandler(enabled = settingsRoute == null) {
                            moveTaskToBack(true)
                        }
                        HomeScreen(
                            viewModel = homeViewModel,
                            onFilePicked = { uri -> mediaUriState = uri.toString() },
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
                        "gestures" -> GestureSettingsScreen(
                            onBack = { settingsRoute = "settings" },
                            appPreferences = appPreferences
                        )
                        "home_screen_settings" -> com.potato.player.files.ui.settings.HomeScreenSettingsScreen(
                            onBack = { settingsRoute = "settings" },
                            appPreferences = appPreferences
                        )
                        "playback_settings" -> PlaybackSettingsScreen(
                            onBack = { settingsRoute = "settings" },
                            appPreferences = appPreferences
                        )
                        "misc_settings" -> com.potato.player.files.ui.settings.MiscSettingsScreen(
                            onBack = { settingsRoute = "settings" },
                            appPreferences = appPreferences
                        )
                        "settings" -> SettingsScreen(
                            onBack = { settingsRoute = null },
                            onGesturesClick = { settingsRoute = "gestures" },
                            onPlaybackClick = { settingsRoute = "playback_settings" },
                            onAppearanceClick = { settingsRoute = "appearance" },
                            onSubtitleAppearanceClick = { settingsRoute = "subtitle_appearance" },
                            onHomeScreenClick = { settingsRoute = "home_screen_settings" },
                            onMiscClick = { settingsRoute = "misc_settings" },
                            onAboutClick = { settingsRoute = "about" },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uriFromIntent = resolveMediaUri(intent)
        if (uriFromIntent != null) {
            mediaUriState = uriFromIntent.toString()
            isExternalLaunch = intent.action == Intent.ACTION_VIEW
        }
    }

    override fun onStart() {
        super.onStart()
        playerViewRef?.onResume() // re-enables PlayerView rendering
        viewModelState?.onForeground()
    }

    override fun onStop() {
        super.onStop()
        playerViewRef?.onPause()  // disables PlayerView rendering cleanly
        
        if (appPreferences.resumePlayback.value) {
            val uriStr = mediaUriState ?: intent?.data?.toString() ?: intent?.getStringExtra(EXTRA_MEDIA_URI)
            if (uriStr != null) {
                viewModelState?.uiState?.value?.positionMs?.let { pos ->
                    appPreferences.savePlaybackPosition(uriStr, pos)
                }
            }
        }

        viewModelState?.onBackground()
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

