package com.potato.player.feature.player

import android.os.Build
import android.app.PictureInPictureParams
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potato.player.feature.player.controls.DoubleTapSeekOverlay
import com.potato.player.feature.player.controls.DoubleTapSeekState
import com.potato.player.feature.player.controls.HoldToFastForward
import com.potato.player.feature.player.controls.PlayerBottomControls
import com.potato.player.feature.player.controls.PlayerTopBar
import androidx.activity.compose.BackHandler
import android.content.pm.ActivityInfo
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.potato.player.util.findActivity
import com.potato.player.util.lockOrientation
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    videoUri: String,
    title: String = "",
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onBrightnessChange: (Float) -> Unit = {}
) {
    BackHandler {
        viewModel.pause()
        onBack()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progressState by viewModel.progressState.collectAsStateWithLifecycle()
    val fitMode by viewModel.fitMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // FIX (Bug 1): rememberLauncherForActivityResult registered inside a conditional composable.
    // What was wrong: The launcher was inside SubtitleTrackDialog (a conditional overlay). If the process died while the file picker was open, the result was dropped upon recreation because the dialog wasn't initially composed.
    // Fix: Hoist the launcher to the screen level where it is unconditionally composed, so it can always receive the file picker result.
    val subtitleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onLoadExternalSubtitle(it, context) }
    }

    // ponytail: orientation + insets boilerplate extracted for readability
    PlayerLifecycleEffect(activity = activity, uiState = uiState, viewModel = viewModel)

    val controlsState = rememberControlsVisibilityState(
        isPlaying = uiState.isPlaying,
        dragPositionSec = progressState.dragPositionSec,
        isInPipMode = activity?.isInPictureInPictureMode == true
    )
    var doubleTapSeekState by remember { mutableStateOf<DoubleTapSeekState?>(null) }
    var currentZoom by remember { mutableStateOf(1.0f) }
    var currentPanX by remember { mutableStateOf(0f) }
    var currentPanY by remember { mutableStateOf(0f) }

    LaunchedEffect(uiState.fileLoaded) {
        if (uiState.fileLoaded) {
            currentZoom = 1.0f
            currentPanX = 0f
            currentPanY = 0f
        }
    }

    // Clear double-tap seek overlay after animation
    LaunchedEffect(doubleTapSeekState?.triggerId) {
        if (doubleTapSeekState != null) {
            delay(PlayerUiConstants.DOUBLE_TAP_OVERLAY_CLEAR_MS)
            doubleTapSeekState = null
        }
    }

    // Load the video once the surface is ready; also handles config-change re-attach.
    DisposableEffect(viewModel, videoUri) {
        viewModel.setSurfaceReadyCallback { viewModel.onSurfaceReady(videoUri, title) }
        onDispose {
            viewModel.setSurfaceReadyCallback(null)
            viewModel.onSurfaceDestroyed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // ── Video surface ────────────────────────────────────────────────────
        PlayerSurface(
            callback = viewModel.surfaceCallback,
            modifier = Modifier.fillMaxSize()
        )

        // ── Gesture & Tap Overlay ────────────────────────────────────────────
        PlayerGestureBox(
            uiState = uiState,
            viewModel = viewModel,
            controlsState = controlsState,
            onBrightnessChange = onBrightnessChange,
            currentZoom = currentZoom,
            onZoomChange = { currentZoom = it },
            currentPanX = currentPanX,
            onPanXChange = { currentPanX = it },
            currentPanY = currentPanY,
            onPanYChange = { currentPanY = it },
            doubleTapSeekState = doubleTapSeekState,
            onDoubleTapSeekState = { doubleTapSeekState = it },
            activity = activity
        )

        // ── Double-Tap Seek Overlay ──────────────────────────────────────────
        if (!(activity?.isInPictureInPictureMode == true)) {
            DoubleTapSeekOverlay(seekState = doubleTapSeekState)
        }

        // ── Top Hold for 2x Fast-Forward Banner ──────────────────────────────
        if (!(activity?.isInPictureInPictureMode == true)) {
            HoldToFastForward(
                visible = uiState.isFastForwarding,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (controlsState.isVisible) 72.dp else 36.dp)
            )
        }

        // ── Loading indicator ────────────────────────────────────────────────
        if (uiState.isLoading) {
            CircularProgressIndicator(
                color    = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // ── Error message ────────────────────────────────────────────────────
        uiState.error?.let { msg ->
            Text(
                text     = "Error: $msg",
                color    = Color.Red,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (uiState.fileLoaded && !(activity?.isInPictureInPictureMode == true)) {

            // ── Top bar ──────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = controlsState.isVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
            ) {
                PlayerTopBar(
                    fileName              = uiState.fileName,
                    currentDecoder        = uiState.hwdecCurrent,
                    onBack                = onBack,
                    onSelectAudioTrack    = { viewModel.dialogs.onShowAudioDialog() },
                    onSelectSubtitleTrack = { viewModel.dialogs.onShowSubtitleDialog() },
                    onSelectDecoder       = { viewModel.dialogs.onShowDecoderDialog() },
                    onMoreOptions         = { viewModel.dialogs.onMoreMenuToggle() }
                )
            }

            // ── Center play/pause ────────────────────────────────────────────
            AnimatedVisibility(
                visible = controlsState.isVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                IconButton(
                    onClick  = viewModel::togglePlay,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                ) {
                    Icon(
                        imageVector     = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                        tint            = Color.White,
                        modifier        = Modifier.size(36.dp)
                    )
                }
            }

            // ── Bottom controls ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = controlsState.isVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
            ) {
                PlayerBottomControls(
                    progressState     = progressState,
                    isAutoRotation    = uiState.isAutoRotation,
                    currentFitMode    = fitMode,
                    onSeekGesture     = { ms -> viewModel.onSliderDragChange(ms / 1000.0) },
                    onSeekCommit      = { ms -> viewModel.onSliderDragEnd(ms / 1000.0) },
                    onDragStart       = { viewModel.onSliderDragStart(progressState.positionSec) },
                    onDragEnd         = { /* already handled inside onSeekCommit path */ },
                    onToggleAutoRotation = { viewModel.toggleAutoRotation() },
                    onToggleFitMode   = { viewModel.cycleFitMode() },
                    onEnterPip        = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            activity?.enterPictureInPictureMode(
                                PictureInPictureParams.Builder().build()
                            )
                        }
                    }
                )
            }
        }

        // ponytail: move only, zero new logic
        if (!(activity?.isInPictureInPictureMode == true)) {
            PlayerModals(
                uiState = uiState,
                viewModel = viewModel,
                onLaunchFilePicker = { subtitleLauncher.launch(arrayOf("*/*")) }
            )
        }
    }
}

