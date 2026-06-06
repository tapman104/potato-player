package com.potato.player.player.ui

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import com.potato.player.player.ui.gesture.PlayerGestureHandler
import com.potato.player.player.ui.topbar.PlayerTopBar
import com.potato.player.player.viewmodel.PlayerViewModel
import com.potato.player.files.ui.settings.AboutScreen
import com.potato.player.files.ui.settings.AppearanceSettingsScreen
import com.potato.player.files.ui.settings.GestureSettingsScreen
import com.potato.player.files.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import mediaengine.MediaEvent

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
 * @param onBack              Called when the top-bar back button is tapped.
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

    // ── State collection ──────────────────────────────────────────────────────────
    val uiState by viewModel.uiState.collectAsState()
    val controlsState by viewModel.controlsState.collectAsState()

    // Controls visibility — local UI state, auto-hides after idle period.
    var controlsVisible by remember { mutableStateOf(true) }

    // Dialog visibility — purely transient UI state, not promoted to ViewModel.
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var subtitleSettings by remember { mutableStateOf(SubtitleSettings()) }
    var showSubtitleSizeDialog by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var settingsRoute by rememberSaveable { mutableStateOf<String?>(null) }

    // ── Gesture handler ───────────────────────────────────────────────────────────
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val handler = remember(viewModel) {
        PlayerGestureHandler(viewModel, audioManager, screenHeightPx, context)
    }
    val gestureState by handler.gestureState.collectAsState()

    DisposableEffect(handler) {
        onDispose { handler.release() }
    }

    // Track whether a long-press is currently active so we can detect finger-up.
    var isLongPressing by remember { mutableStateOf(false) }

    // ── Effects ──────────────────────────────────────────────────────────────────

    // Open the URI once (or when it changes).
    val configuration = LocalConfiguration.current
    val screenW = configuration.screenWidthDp
    val screenH = configuration.screenHeightDp
    LaunchedEffect(uri) {
        viewModel.cacheScreenSize(screenW, screenH)
        viewModel.open(uri)
    }

    // Re-run whenever videoTracks changes (e.g. after the engine resolves
    // the stream). Only apply orientation from the first valid track;
    // once locked by the user, applyAutoOrientation no-ops internally.
    LaunchedEffect(uiState.videoTracks) {
        if (uiState.videoTracks.isNotEmpty()) {
            viewModel.applyAutoOrientation(screenW, screenH)
        }
    }

    LaunchedEffect(controlsState.orientationMode) {
        val activity = activityContext as? android.app.Activity
        activity?.requestedOrientation = when (controlsState.orientationMode) {
            com.potato.player.player.ui.state.OrientationMode.AUTO -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
            com.potato.player.player.ui.state.OrientationMode.LOCKED_PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            com.potato.player.player.ui.state.OrientationMode.LOCKED_LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    // One-shot: configure edge-to-edge and swipe-to-reveal system bars behaviour.
    // This only needs to happen once for the lifetime of the screen.
    LaunchedEffect(Unit) {
        val activity = activityContext as? android.app.Activity ?: return@LaunchedEffect
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsCtrl = WindowInsetsControllerCompat(window, window.decorView)
        insetsCtrl.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Show/hide system bars whenever controls visibility toggles.
    // LaunchedEffect re-runs only when controlsVisible changes, not on every recompose.
    LaunchedEffect(controlsVisible) {
        val activity = activityContext as? android.app.Activity ?: return@LaunchedEffect
        val insetsCtrl = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (controlsVisible) {
            insetsCtrl.show(WindowInsetsCompat.Type.systemBars())
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

    // One-time event handling.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MediaEvent.Error -> onError?.invoke(event.throwable)
                is MediaEvent.PlaybackCompleted -> onPlaybackCompleted?.invoke()
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
        // â”€â”€ Layer 1: Video surface â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // PlayerView with useController=false â€” our Compose layer owns controls.
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
                // Change 3: RESIZE_MODE_FIT (set in factory) tells ExoPlayer to maintain
                // the correct aspect ratio automatically. PlayerView does not expose
                // setAspectRatio() publicly, so we rely on the mode rather than forcing
                // a manual ratio on each composition pass.
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Layer 2: Gesture layer (tap + long-press + vertical drag) ────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Tap + long-press: separate pointerInput key so they compose independently.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val third = size.width / 3f
                            when {
                                offset.x < third -> handler.onDoubleTap(isForward = false)
                                offset.x > third * 2 -> handler.onDoubleTap(isForward = true)
                                else -> {
                                    handler.onTap()
                                    controlsVisible = !controlsVisible
                                }
                            }
                        },
                        onTap = {
                            handler.onTap()
                            controlsVisible = !controlsVisible
                        },
                        onLongPress = { _ ->
                            handler.onLongPressStart()
                            isLongPressing = true
                        },
                    )
                }
                // Vertical drag: separate pointerInput block for brightness / volume.
                .pointerInput(Unit) {
                    var dragStartX = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStartX = offset.x
                            handler.onVerticalDragStart(dragStartX, size.width.toFloat())
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            handler.onVerticalDrag(dragAmount.y)
                        },
                        onDragEnd = { handler.onVerticalDragEnd() },
                        onDragCancel = { handler.onVerticalDragEnd() },
                    )
                }
                // Long-press release detection: wait for all pointers up while long-pressing.
                .pointerInput(isLongPressing) {
                    if (isLongPressing) {
                        awaitEachGesture {
                            // Wait until no pointers remain down.
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val allUp = event.changes.all { !it.pressed }
                                if (allUp) {
                                    isLongPressing = false
                                    handler.onLongPressEnd()
                                    break
                                }
                            } while (true)
                        }
                    }
                }
        )

        // ── Layer 2b: Gesture overlay ─────────────────────────────────────────────
        GestureOverlay(
            gestureState = gestureState,
            modifier = Modifier.fillMaxSize(),
        )

        // ────────────────────────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.70f),
                                Color.Transparent,
                            ),
                        )
                    ),
            ) {
                PlayerTopBar(
                    title = title,
                    isSubtitleActive = uiState.selectedSubtitleTrackId != null,
                    onBack = onBack,
                    onSubtitleClick = { showSubtitleDialog = true },
                    onAudioTrackClick = { showAudioDialog = true },
                )
            }
        }

        // â”€â”€ Layer 4: Center controls â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        AnimatedVisibility(
            visible = controlsVisible,
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
            )
        }

        // â”€â”€ Layer 5: Bottom control bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f),
                            ),
                        )
                    )
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                BottomControlBar(
                    uiState = uiState,
                    isRotationLocked = controlsState.rotationLocked,
                    onSeek = { positionMs ->
                        viewModel.setPositionUpdateRate(100L)
                        viewModel.seekTo(positionMs)
                    },
                    onSeekFinished = { viewModel.setPositionUpdateRate(250L) },
                    onToggleRotationLock = viewModel::toggleRotationLock,
                )
            }
        }

        // â”€â”€ Layer 6: Dialogs â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Rendered as conditional composables (not inside a Box child slot) so
        // they draw as AlertDialogs above the entire player surface naturally.

        if (showAudioDialog) {
            AudioTrackDialog(
                tracks = uiState.audioTracks,
                selectedId = uiState.selectedAudioTrackId,
                onSelect = { id ->
                    viewModel.selectAudioTrack(id)
                    showAudioDialog = false
                },
                onDismiss = { showAudioDialog = false },
            )
        }

        if (showSubtitleDialog) {
            SubtitleTrackDialog(
                tracks = uiState.subtitleTracks,
                selectedId = uiState.selectedSubtitleTrackId,
                onSelect = { id ->
                    viewModel.selectSubtitleTrack(id)
                    showSubtitleDialog = false
                },
                onDisable = {
                    viewModel.disableSubtitles()
                    showSubtitleDialog = false
                },
                onDismiss = { showSubtitleDialog = false },
                onAppearanceClick = { showSubtitleSizeDialog = true },
            )
        }

        if (showSubtitleSizeDialog) {
            SubtitleSizeDialog(
                settings = subtitleSettings,
                onConfirm = { newSettings ->
                    subtitleSettings = newSettings
                    playerViewRef?.subtitleView?.let {
                        applySubtitleSettings(it, newSettings)
                    }
                    showSubtitleSizeDialog = false
                },
                onDismiss = { showSubtitleSizeDialog = false },
            )
        }

        // ── Settings navigation stack ─────────────────────────────────────────────
        BackHandler(enabled = settingsRoute != null) {
            if (settingsRoute == "settings") settingsRoute = null else settingsRoute = "settings"
        }

        when (settingsRoute) {
            "about" -> AboutScreen(onBack = { settingsRoute = "settings" })
            "appearance" -> AppearanceSettingsScreen(onBack = { settingsRoute = "settings" })
            "gestures" -> GestureSettingsScreen(onBack = { settingsRoute = "settings" })
            "settings" -> SettingsScreen(
                onBack = { settingsRoute = null },
                onGesturesClick = { settingsRoute = "gestures" },
                onAppearanceClick = { settingsRoute = "appearance" },
                onAboutClick = { settingsRoute = "about" },
            )
        }
    }
}

private const val CONTROLS_AUTO_HIDE_DELAY_MS = 3_000L // [CHANGE 16]


