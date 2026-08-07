package com.potato.player.feature.player

import android.os.Build
import android.app.PictureInPictureParams
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potato.player.feature.player.controls.DoubleTapSeekOverlay
import com.potato.player.feature.player.controls.DoubleTapSeekState
import com.potato.player.feature.player.controls.HoldToFastForward
import com.potato.player.feature.player.controls.PlayerBottomControls
import com.potato.player.feature.player.controls.PlayerCenterPlayPause
import com.potato.player.feature.player.controls.PlayerTopBar
import androidx.activity.compose.BackHandler
import com.potato.player.util.findActivity
import kotlinx.coroutines.delay

private fun enterPip(activity: android.app.Activity?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
    }
}


@Composable
fun PlayerScreen(
    videoUri: String,
    title: String = "",
    viewModel: PlayerViewModel,
    isExternalIntent: Boolean = false,
    onBack: () -> Unit,
    onBrightnessChange: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        if (!uiState.isLocked) {
            viewModel.pause()
            if (isExternalIntent) {
                activity?.finish()
            } else {
                onBack()
            }
        }
    }

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

    var controlsVisible by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    var doubleTapSeekState by remember { mutableStateOf<DoubleTapSeekState?>(null) }
    val swipeSeekTargetSec = uiState.swipeSeekTargetSec
    var swipeDragStartSec by remember { mutableStateOf(0.0) }
    
    val isSeeking = uiState.progressState.dragPositionSec != null
    LaunchedEffect(
        controlsVisible, 
        uiState.isPlaying, 
        isSeeking,
        doubleTapSeekState,
        uiState.isFastForwarding,
        uiState.isLocked,
        uiState.isSwipingVolumeOrBrightness
    ) {
        if (controlsVisible && uiState.isPlaying && !isSeeking) {
            if (!uiState.isFastForwarding && !uiState.isSwipingVolumeOrBrightness) {
                delay(4000L)
                controlsVisible = false
            }
        }
    }

    LaunchedEffect(activity?.isInPictureInPictureMode) {
        if (activity?.isInPictureInPictureMode == true) {
            controlsVisible = false
        }
    }


    LaunchedEffect(swipeSeekTargetSec) {
        if (swipeSeekTargetSec != null) {
            controlsVisible = false
        }
    }

    // Clear double-tap seek overlay after animation
    LaunchedEffect(doubleTapSeekState?.triggerId) {
        if (doubleTapSeekState != null) {
            delay(1200L)
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
        if (!uiState.isLocked) {
            PlayerGestureBox(
                uiState = uiState,
                viewModel = viewModel,
                onToggleControls = { controlsVisible = !controlsVisible },
                onBrightnessChange = onBrightnessChange,
                fileLoaded = uiState.fileLoaded,
                doubleTapSeekState = doubleTapSeekState,
                onDoubleTapSeekState = { doubleTapSeekState = it },
                onSwipeSeekStart = { startSec -> swipeDragStartSec = startSec },
                activity = activity
            )
        }

        // ── Double-Tap Seek Overlay ──────────────────────────────────────────
        if (!(activity?.isInPictureInPictureMode == true)) {
            DoubleTapSeekOverlay(seekState = doubleTapSeekState)
        }

        // ── Swipe Seek Overlay ───────────────────────────────────────────────
        if (swipeSeekTargetSec != null && !(activity?.isInPictureInPictureMode == true)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    val target = swipeSeekTargetSec!!
                    val delta = target - swipeDragStartSec
                    val sign = if (delta >= 0) "+" else "-"
                    
                    val hours = (target / 3600).toInt()
                    val minutes = ((target % 3600) / 60).toInt()
                    val seconds = (target % 60).toInt()
                    val timeString = if (hours > 0) {
                        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
                    }
                    
                    Text(
                        text = timeString,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "[$sign${kotlin.math.abs(delta).toInt()}s]",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // ── Top Hold for 2x Fast-Forward Banner ──────────────────────────────
        if (!(activity?.isInPictureInPictureMode == true)) {
            HoldToFastForward(
                visible = uiState.isFastForwarding,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (controlsVisible) 72.dp else 36.dp)
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
                visible = controlsVisible && !uiState.isLocked && swipeSeekTargetSec == null,
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
                    onSelectAudioTrack    = { viewModel.showDialog(ActiveDialog.Audio) },
                    onSelectSubtitleTrack = { viewModel.showDialog(ActiveDialog.Subtitle) },
                    onSelectDecoder       = { viewModel.showDialog(ActiveDialog.Decoder) },
                    onMoreOptions         = { 
                        if (uiState.activeDialog == ActiveDialog.MoreMenu) viewModel.dismissDialog()
                        else viewModel.showDialog(ActiveDialog.MoreMenu)
                    }
                )
            }

            // ── Center play/pause ────────────────────────────────────────────
            AnimatedVisibility(
                visible = controlsVisible && !uiState.isLocked && swipeSeekTargetSec == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                PlayerCenterPlayPause(
                    isPlaying = uiState.isPlaying,
                    onClick   = viewModel::togglePlay
                )
            }

            // ── Bottom controls ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = controlsVisible && !uiState.isLocked && swipeSeekTargetSec == null,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
            ) {
                PlayerBottomControls(
                    progressState     = uiState.progressState,
                    isAutoRotation    = uiState.isAutoRotation,
                    currentFitMode    = uiState.fitMode,
                    contentPadding    = WindowInsets.displayCutout.asPaddingValues(),
                    onSeekGesture     = { ms -> viewModel.onSliderDragChange(ms / 1000.0) },
                    onSeekCommit      = { ms -> viewModel.onSliderDragEnd(ms / 1000.0) },
                    onDragStart       = { viewModel.onSliderDragStart(uiState.progressState.positionSec) },
                    onDragEnd         = { /* already handled inside onSeekCommit path */ },
                    onToggleAutoRotation = { viewModel.toggleAutoRotation() },
                    onToggleFitMode   = { viewModel.cycleFitMode() },
                    onEnterPip        = { enterPip(activity) },
                    isLocked          = uiState.isLocked,
                    onToggleLock      = { viewModel.toggleLock() }
                )
            }
        }

        // ── Centered Unlock Button ───────────────────────────────────────────
        if (uiState.isLocked && !(activity?.isInPictureInPictureMode == true)) {
            IconButton(
                onClick = viewModel::toggleLock,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Unlock",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp)
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

