package com.potato.player.player.ui

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.PointerEvent
import kotlin.math.abs
import kotlin.math.hypot
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.potato.player.player.ui.subtitle.SubtitleSettings
import com.potato.player.player.ui.subtitle.SubtitleSizeDialog
import com.potato.player.player.ui.subtitle.applySubtitleSettings
import com.potato.player.player.ui.bottom.BottomControlBar
import com.potato.player.player.ui.center.CenterControlsRow
import com.potato.player.player.ui.dialog.AudioTrackDialog
import com.potato.player.player.ui.dialog.SubtitleTrackDialog
import com.potato.player.player.ui.gesture.ActiveGesture
import com.potato.player.player.ui.gesture.GestureOverlay
import com.potato.player.player.ui.gesture.PinchZoomHandler
import com.potato.player.player.ui.gesture.PlayerGestureHandler
import com.potato.player.player.ui.topbar.PlayerTopBar
import com.potato.player.viewmodel.PlayerViewModel
import com.potato.player.files.ui.settings.AboutScreen
import com.potato.player.files.ui.settings.AppearanceSettingsScreen
import com.potato.player.files.ui.settings.GestureSettingsScreen
import com.potato.player.files.ui.settings.PlaybackSettingsScreen
import com.potato.player.files.ui.settings.SettingsScreen
import com.potato.player.files.preferences.AppPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.potato.player.engine.MediaEvent

/**
 * Root player screen.
 *
 * Responsibilities:
 *  - Attach [PlayerView] (AndroidView) for video surface rendering.
 *  - Show/hide controls overlay on tap (GestureLayer).
 *  - Auto-hide controls after [CONTROLS_AUTO_HIDE_DELAY_MS] while playing.
 *  - Collect one-time [MediaEvent]s and surface them to the caller.
 *  - Render [AudioTrackDialog] / [SubtitleTrackDialog] from local UI state.
 *
 * Layer stack (bottom â†’ top):
 * 1. **VideoSurface** â€” [AndroidView] wrapping [PlayerView], always visible.
 * 2. **GestureLayer** â€” transparent clickable that toggles [controlsVisible].
 * 3. **PlayerTopBar** â€” [Alignment.TopCenter], [AnimatedVisibility] fade.
 * 4. **CenterControlsRow** â€” [Alignment.Center], [AnimatedVisibility] fade.
 * 5. **BottomControlBar** â€” [Alignment.BottomCenter], [AnimatedVisibility] fade.
 * 6. **AudioTrackDialog** / **SubtitleTrackDialog** â€” conditional on local state.
 *
 * Dialog state ([showAudioDialog], [showSubtitleDialog]) is kept as
 * `remember { mutableStateOf(false) }` local to this composable â€” it is
 * transient UI state with no business significance, so it does not belong in
 * the ViewModel.
 *
 * The screen is stateless from the ViewModel's perspective: it reads
 * [PlayerViewModel.uiState] / [PlayerViewModel.controlsState] and
 * dispatches intents back through lambda references.
 *
 * @param viewModel           The player ViewModel.
 * @param player              ExoPlayer [Player] instance forwarded to [PlayerView].
 * @param uri                 URI to open for playback. Changes trigger [MediaEngine.open].
 * @param onBack              Called when the top-bar back button is tapped. Callers must reset orientation before finishing.
 * @param title               Optional media title shown in [PlayerTopBar].
 * @param onError             Optional callback for fatal playback errors.
 * @param onPlaybackCompleted Optional callback when the source finishes.
 * @param modifier            Applied to the root [Box].
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    player: Player,
    uri: Uri,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    onError: ((Throwable) -> Unit)? = null,
    onPlaybackCompleted: (() -> Unit)? = null,
    onPlayerViewReady: ((PlayerView) -> Unit)? = null, // [CHANGE 65]
) {
    val context = LocalContext.current
    val activityContext = context
    val appPreferences = remember { AppPreferences(context.applicationContext) }

    // ── State collection ──────────────────────────────────────────────────────────
    val uiState by viewModel.uiState.collectAsState()
    val controlsState by viewModel.controlsState.collectAsState()
    val enableHaptics by appPreferences.enableHaptics.collectAsState()

    // Controls visibility — local UI state, auto-hides after idle period.
    var controlsVisible by remember { mutableStateOf(false) }

    // Dialog visibility — purely transient UI state, not promoted to ViewModel.
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var subtitleSettings by remember { mutableStateOf(SubtitleSettings()) }
    var showSubtitleSizeDialog by remember { mutableStateOf(false) }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    val zoomHandler = remember { PinchZoomHandler() }
    val zoomState by zoomHandler.zoomState.collectAsState()

    val videoTrack = uiState.videoTracks.firstOrNull()
    val videoW = videoTrack?.width?.toFloat() ?: 0f
    val videoH = videoTrack?.height?.toFloat() ?: 0f

    LaunchedEffect(Unit) {
        appPreferences.getSubtitleSettings().first().let { 
            subtitleSettings = it 
        }
    }

    LaunchedEffect(subtitleSettings) {
        playerViewRef?.subtitleView?.let {
            applySubtitleSettings(it, subtitleSettings)
        }
    }

    // ── Gesture handler ───────────────────────────────────────────────────────────
    val density = LocalDensity.current
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val handler = remember(viewModel, appPreferences) {
        PlayerGestureHandler(viewModel, audioManager, context, appPreferences)
    }
    val gestureState by handler.gestureState.collectAsState()
    
    val hideControlsForGesture = gestureState.active is ActiveGesture.LongPressSpeed || gestureState.active is ActiveGesture.DoubleTapSeek

    // 24 dp dead zone converted to px once; passed to the handler each drag.
    val deadZoneThresholdPx = with(density) { 24.dp.toPx() }

    DisposableEffect(handler) {
        onDispose { handler.release() }
    }

    // Track whether a long-press is currently active so we can detect finger-up.
    var isLongPressing by remember { mutableStateOf(false) }

    BackHandler {
        if (controlsVisible) {
            controlsVisible = false
        } else {
            onBack()
        }
    }

    // ── Effects ──────────────────────────────────────────────────────────────────

    // Open the URI once (or when it changes).
    val configuration = LocalConfiguration.current
    val screenW = configuration.screenWidthDp
    val screenH = configuration.screenHeightDp
    
    LaunchedEffect(controlsState.resizeMode) {
        zoomHandler.resetZoom()
    }
    var hasResumed by remember { mutableStateOf(false) }

    LaunchedEffect(uri, viewModel) {
        hasResumed = false
        viewModel.cacheScreenSize(screenW, screenH)
        viewModel.open(uri)
        
        // Apply default playback speed
        viewModel.setPlaybackSpeed(appPreferences.defaultPlaybackSpeed.value)

        val savedVolume = appPreferences.getSavedVolume()
        if (savedVolume != -1f) {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetStep = (savedVolume * maxVolume).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0)
        }

        val savedBrightness = appPreferences.getSavedBrightness()
        if (savedBrightness != -1f) {
            val window = (activityContext as? android.app.Activity)?.window
            window?.let {
                val lp = it.attributes
                lp.screenBrightness = savedBrightness
                it.attributes = lp
            }
        }
    }

    LaunchedEffect(uiState.canPlay, uri, viewModel) {
        if (!hasResumed && uiState.canPlay && appPreferences.resumePlayback.value) {
            val savedPos = appPreferences.getPlaybackPosition(uri.toString())
            if (savedPos > 0L) {
                viewModel.seekTo(savedPos)
            }
            hasResumed = true
        }
    }

    // Periodic playback position save removed for battery optimization (I/O reduction)

    DisposableEffect(uri, viewModel) {
        onDispose {
            if (appPreferences.resumePlayback.value) {
                appPreferences.savePlaybackPosition(uri.toString(), viewModel.positionState.value.positionMs)
            }
        }
    }

    // Re-run whenever videoTracks changes (e.g. after the engine resolves
    // the stream). Only apply orientation from the first valid track;
    // once locked by the user, applyAutoOrientation no-ops internally.
    val defaultOrientationName by appPreferences.defaultOrientation.collectAsState()
    val defaultOrientation = remember(defaultOrientationName) {
        try {
            com.potato.player.player.ui.state.OrientationMode.valueOf(defaultOrientationName)
        } catch (e: Exception) {
            com.potato.player.player.ui.state.OrientationMode.AUTO
        }
    }

    LaunchedEffect(uiState.videoTracks, defaultOrientation, viewModel) {
        if (uiState.videoTracks.isNotEmpty()) {
            viewModel.applyAutoOrientation(screenW, screenH, defaultOrientation)
        }
    }

    LaunchedEffect(controlsState.orientationMode) {
        val activity = activityContext as? android.app.Activity
        activity?.requestedOrientation = when (controlsState.orientationMode) {
            com.potato.player.player.ui.state.OrientationMode.AUTO -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
            com.potato.player.player.ui.state.OrientationMode.LOCKED_PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            com.potato.player.player.ui.state.OrientationMode.LOCKED_LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // One-shot: configure edge-to-edge and swipe-to-reveal system bars behaviour.
    // This only needs to happen once for the lifetime of the screen.
    DisposableEffect(Unit) {
        val activity = activityContext as? android.app.Activity
        val window = activity?.window
        
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsCtrl = WindowInsetsControllerCompat(window, window.decorView)
            insetsCtrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            if (activity != null) {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            if (window != null) {
                // Restore brightness
                val lp = window.attributes
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
                
                // Restore window decor
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val insetsCtrl = WindowInsetsControllerCompat(window, window.decorView)
                insetsCtrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Show/hide system bars whenever controls visibility toggles.
    // Status bar stays hidden always — transient swipe is handled by BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE.
    // Only the navigation bar is toggled with the controls.
    LaunchedEffect(controlsVisible) {
        val activity = activityContext as? android.app.Activity ?: return@LaunchedEffect
        val insetsCtrl = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (controlsVisible) {
            insetsCtrl.show(WindowInsetsCompat.Type.navigationBars())
            insetsCtrl.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            insetsCtrl.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Auto-hide controls while playing after the idle delay.
    // Suppressed while any gesture is active to avoid hiding controls mid-swipe.
    LaunchedEffect(controlsVisible, uiState.isPlaying, gestureState.active) {
        if (controlsVisible && uiState.isPlaying && gestureState.active == ActiveGesture.None) {
            delay(CONTROLS_AUTO_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    // Notify the engine about controls visibility so it can adjust position-poll frequency.
    // When controls are hidden the seek bar is not visible, so polling can slow down.
    LaunchedEffect(controlsVisible) {
        viewModel.setControlsVisible(controlsVisible)
    }

    // One-time event handling.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MediaEvent.Error -> onError?.invoke(event.throwable)
                is MediaEvent.PlaybackCompleted -> {
                    if (appPreferences.autoPlayNext.value) {
                        android.widget.Toast.makeText(context, "Auto-play next enabled (Requires folder permission to fully work)", android.widget.Toast.LENGTH_LONG).show()
                    }
                    onPlaybackCompleted?.invoke()
                }
                else -> Unit
            }
        }
    }


    // â”€â”€ UI tree â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // PlayerView with useController=false — our Compose layer owns controls.
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    // Explicit resize mode — RESIZE_MODE_FIT ensures no cropping.
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    subtitleView?.let { applySubtitleSettings(it, subtitleSettings) }
                    onPlayerViewReady?.invoke(this)
                    playerViewRef = this
                }
            },
            update = { pv ->
                if (pv.player !== player) pv.player = player
                pv.resizeMode = controlsState.resizeMode.value
                pv.keepScreenOn = uiState.isPlaying
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomState.scale
                    scaleY = zoomState.scale
                    translationX = zoomState.offsetX
                    translationY = zoomState.offsetY
                },
        )

        // ── Layer 2: Unified gesture state machine ──────────────────────────
        PlayerGestureLayer(
            viewModel = viewModel,
            zoomHandler = zoomHandler,
            gestureHandler = handler,
            controlsVisible = controlsVisible,
            onControlsVisibleChange = { controlsVisible = it },
            isLongPressing = isLongPressing,
            onIsLongPressingChange = { isLongPressing = it },
            deadZoneThresholdPx = deadZoneThresholdPx,
            appPreferences = appPreferences,
            modifier = Modifier,
        )

        // ────────────────────────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible && !hideControlsForGesture,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.80f),
                            0.6f to Color.Black.copy(alpha = 0.20f),
                            1f to Color.Transparent,
                        )
                    ),
            ) {
                val displayTitle = remember(uri) {
                    val projection = arrayOf(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(0)?.substringBeforeLast(".")
                        } else null
                    } ?: uri.lastPathSegment?.substringBeforeLast(".")?.replace("%20", " ")
                    ?: ""
                }

                PlayerTopBar(
                    title = displayTitle,
                    isSubtitleActive = uiState.selectedSubtitleTrackId != null,
                    onBack = onBack,
                    onSubtitleClick = { showSubtitleDialog = true },
                    onAudioTrackClick = { showAudioDialog = true },
                    onSpeedClick = { showSpeedDialog = true }
                )
            }
        }

        // ── Layer 4: Center Controls ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible && !hideControlsForGesture,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            CenterControlsRow(
                isPlaying = uiState.isPlaying,
                isLoading = uiState.isLoading,
                isEnded = uiState.isEnded,
                onPlayPauseClick = viewModel::togglePlayPause,
                onSeekBackward = viewModel::seekBackward10,
                onSeekForward = viewModel::seekForward10,
                enableHaptics = enableHaptics,
            )
        }

        // ── Layer 5: Bottom control bar ─────────────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible && !hideControlsForGesture,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.4f to Color.Black.copy(alpha = 0.30f),
                            1f to Color.Black.copy(alpha = 0.65f),
                        )
                    )
                    .navigationBarsPadding()
                    .padding(bottom = 4.dp, start = 16.dp, end = 16.dp),
            ) {
                BottomControlBar(
                    positionStateFlow = viewModel.positionState,
                    orientationMode = controlsState.orientationMode,
                    resizeMode = controlsState.resizeMode,
                    onSeek = { positionMs ->
                        viewModel.setPositionUpdateRate(100L)
                        viewModel.seekTo(positionMs)
                    },
                    onSeekFinished = { viewModel.onSeekFinished() },
                    onCycleRotation = viewModel::cycleRotationMode,
                    onResizeModeClick = viewModel::cycleResizeMode,
                    enableHaptics = enableHaptics,
                )
            }
        }

        // ── Layer 6: Dialogs ──────────────────────────────────────────────────
        PlayerDialogs(
            uiState = uiState,
            subtitleSettings = subtitleSettings,
            onSubtitleSettingsChange = { subtitleSettings = it },
            playerViewRef = playerViewRef,
            showAudioDialog = showAudioDialog,
            showSubtitleDialog = showSubtitleDialog,
            showSubtitleSizeDialog = showSubtitleSizeDialog,
            showSpeedDialog = showSpeedDialog,
            onAudioDialogDismiss = { showAudioDialog = false },
            onSubtitleDialogDismiss = { showSubtitleDialog = false },
            onSubtitleSizeDismiss = { showSubtitleSizeDialog = false },
            onSpeedDialogDismiss = { showSpeedDialog = false },
            viewModel = viewModel,
            onShowSubtitleSizeDialog = { showSubtitleSizeDialog = true }
        )
    }
}

private const val CONTROLS_AUTO_HIDE_DELAY_MS = 3_000L // [CHANGE 16]