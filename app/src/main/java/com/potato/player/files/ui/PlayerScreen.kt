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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEvent
import kotlin.math.abs
import kotlin.math.hypot
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
import com.potato.player.player.ui.gesture.PlayerGestureHandler
import com.potato.player.player.ui.topbar.PlayerTopBar
import com.potato.player.player.viewmodel.PlayerViewModel
import com.potato.player.files.ui.settings.AboutScreen
import com.potato.player.files.ui.settings.AppearanceSettingsScreen
import com.potato.player.files.ui.settings.GestureSettingsScreen
import com.potato.player.files.ui.settings.SettingsScreen
import com.potato.player.files.preferences.AppPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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
    val appPreferences = remember { AppPreferences(context.applicationContext) }

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
    // 24 dp dead zone converted to px once; passed to the handler each drag.
    val deadZoneThresholdPx = with(density) { 24.dp.toPx() }

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
        // ──────────────────────────────────────────────────────────────────────────
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
                if (pv.resizeMode != controlsState.resizeMode.value) {
                    pv.resizeMode = controlsState.resizeMode.value
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Layer 2: Unified gesture state machine ──────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Exclude the full view from the system back swipe gesture so
                // vertical brightness/volume drags starting at the left/right
                // edge don't trigger Android's predictive-back and close the
                // Activity.
                .systemGestureExclusion()
                .pointerInput(viewModel) { // key=viewModel so it resets if VM changes
                    // Android timing constants from the system ViewConfiguration
                    val longPressMs = viewConfiguration.longPressTimeoutMillis
                    val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis
                    val dragSlop    = viewConfiguration.touchSlop

                    awaitEachGesture {
                        // ── Phase 1: wait for finger down ────────────────────────
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val downX    = firstDown.position.x
                        val downY    = firstDown.position.y
                        val downTime = System.currentTimeMillis()

                        var gestureCommitted = false
                        var isDragging       = false
                        var isLongPressHeld  = false
                        var lastY            = downY

                        // ── Phase 2: classify gesture ─────────────────────────────
                        eventLoop@ while (true) {
                            val elapsed   = System.currentTimeMillis() - downTime
                            val remaining = longPressMs - elapsed

                            val event: PointerEvent? = if (!gestureCommitted && remaining > 0) {
                                withTimeoutOrNull(remaining) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }

                            // Timeout — long-press fires
                            if (event == null) {
                                if (!gestureCommitted) {
                                    gestureCommitted = true
                                    isLongPressHeld  = true
                                    isLongPressing   = true
                                    handler.onLongPressStart()
                                }
                                continue@eventLoop
                            }

                            val primary = event.changes.firstOrNull { it.id == firstDown.id }
                                ?: break@eventLoop
                            val dx       = primary.position.x - downX
                            val dy       = primary.position.y - downY
                            val distance = hypot(dx, dy)

                            when {
                                // ── Axis classification (pre-commit) ──────────────────
                                // Once total movement exceeds dragSlop, determine whether
                                // this is a vertical or horizontal drag:
                                //   - |dy| > 24dp  AND  |dy| > |dx|*1.5 → vertical: commit
                                //   - |dx| wins first                    → horizontal: abandon
                                !gestureCommitted && distance > dragSlop -> {
                                    val absDx = kotlin.math.abs(dx)
                                    val absDy = kotlin.math.abs(dy)
                                    when {
                                        // Clearly vertical: cross dead zone AND steeper than 1.5:1 ratio
                                        absDy >= deadZoneThresholdPx && absDy > absDx * 1.5f -> {
                                            gestureCommitted = true
                                            isDragging = true
                                            firstDown.consume()
                                            handler.onVerticalDragStart(downX, size.width.toFloat())
                                            val firstDy = primary.position.y - lastY
                                            lastY = primary.position.y
                                            primary.consume()
                                            handler.onVerticalDrag(firstDy)
                                        }
                                        // Horizontal wins first — abandon this gesture entirely
                                        // so the horizontal seek handler can take over.
                                        absDx > absDy * 1.5f -> {
                                            gestureCommitted = true // prevents re-evaluation
                                            // isDragging stays false, so onVerticalDragEnd is NOT called
                                        }
                                        // Ambiguous (diagonal) — keep waiting
                                        else -> Unit
                                    }
                                }
                                // ── Continuing vertical drag ────────────────────────
                                isDragging -> {
                                    val delta = primary.position.y - lastY
                                    lastY = primary.position.y
                                    primary.consume()
                                    handler.onVerticalDrag(delta)
                                }
                            }

                            // ── Pointer lifted ──────────────────────────────────────
                            val allUp = event.changes.all { !it.pressed }
                            if (allUp) {
                                when {
                                    isDragging -> handler.onVerticalDragEnd()
                                    isLongPressHeld -> {
                                        isLongPressing = false
                                        handler.onLongPressEnd()
                                    }
                                    else -> {
                                        // UP before slop/long-press → could be tap or first
                                        // half of a double-tap. Wait for a second DOWN.
                                        var secondDown = false

                                        doubleTapLoop@ while (true) {
                                            val dtEvent = withTimeoutOrNull(doubleTapMs) {
                                                awaitPointerEvent()
                                            }
                                            if (dtEvent == null) break@doubleTapLoop

                                            val dtPrimary = dtEvent.changes.firstOrNull { it.pressed }
                                            if (dtPrimary != null) {
                                                secondDown = true
                                                val tapX  = dtPrimary.position.x
                                                val third = size.width / 3f
                                                when {
                                                    tapX < third      -> handler.onDoubleTap(isForward = false)
                                                    tapX > third * 2f -> handler.onDoubleTap(isForward = true)
                                                    else              -> controlsVisible = !controlsVisible
                                                }
                                                withTimeoutOrNull(500L) { awaitPointerEvent() } // drain UP
                                                break@doubleTapLoop
                                            }
                                        }

                                        if (!secondDown) {
                                            // No second tap — plain single tap: toggle controls
                                            controlsVisible = !controlsVisible
                                        }
                                    }
                                }
                                break@eventLoop
                            }
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
                                Color.Black.copy(alpha = 0.85f),
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
                                Color.Black.copy(alpha = 0.90f),
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
                    onResizeModeClick = viewModel::cycleResizeMode,
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
            "appearance" -> AppearanceSettingsScreen(
                onBack = { settingsRoute = "settings" },
                appPreferences = appPreferences
            )
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


