package com.potato.player.feature.player.ui

import com.potato.player.feature.player.PlayerViewModel
import com.potato.player.feature.player.VideoFitMode
import com.potato.player.feature.player.state.*
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
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
    playlist: List<String> = emptyList(),
    playlistTitles: List<String> = emptyList(),
    onBack: () -> Unit,
    onBrightnessChange: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progressState by viewModel.progressState.collectAsStateWithLifecycle()
    val gestureState by viewModel.gestureState.collectAsStateWithLifecycle()

    BackHandler {
        if (!uiState.isLocked) {
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
    val swipeSeekTargetSec = gestureState.swipeSeekTargetSec
    var swipeDragStartSec by remember { mutableStateOf(0.0) }
    
    val isSeeking = progressState.dragPositionSec != null
    LaunchedEffect(
        controlsVisible, 
        uiState.isPlaying, 
        isSeeking,
        doubleTapSeekState,
        uiState.isFastForwarding,
        uiState.isLocked,
        gestureState.isSwipingVolumeOrBrightness
    ) {
        if (controlsVisible && uiState.isPlaying && !isSeeking) {
            if (!uiState.isFastForwarding && !gestureState.isSwipingVolumeOrBrightness) {
                delay(3000L)
                controlsVisible = false
            }
        }
    }

    LaunchedEffect(activity?.isInPictureInPictureMode) {
        if (activity?.isInPictureInPictureMode == true) {
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
    LaunchedEffect(viewModel, videoUri) {
        viewModel.prepareUri(videoUri, title)
    }

    // Initialise playlist so Prev/Next buttons know their neighbours.
    LaunchedEffect(videoUri) {
        viewModel.setPlaylist(playlist, playlistTitles, videoUri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // ── Video surface ────────────────────────────────────────────────────
        val surfaceCallback = remember(viewModel) {
            object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                    viewModel.handleSurfaceReady(holder.surface)
                }
                override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                    if (width > 0 && height > 0) {
                        viewModel.setSurfaceSize(width, height)
                    }
                }
                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                    viewModel.handleSurfaceDestroyed()
                }
            }
        }

        PlayerSurface(
            callback = surfaceCallback,
            modifier = Modifier.fillMaxSize().semantics(mergeDescendants = false) {}
        )

        // ── Gesture & Tap Overlay ────────────────────────────────────────────
        if (!uiState.isLocked) {
            Box(modifier = Modifier.clearAndSetSemantics {}) {
                PlayerGestureBox(
                    gestureState = gestureState,
                    viewModel = viewModel,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onBrightnessChange = onBrightnessChange,
                    onVolumeChange = { viewModel.setVolume(it) },
                    fileLoaded = uiState.fileLoaded,
                    doubleTapSeekState = doubleTapSeekState,
                    onDoubleTapSeekState = { doubleTapSeekState = it },
                    onSwipeSeekStart = { startSec -> swipeDragStartSec = startSec },
                    activity = activity
                )
            }
        }

        // ── Double-Tap Seek Overlay ──────────────────────────────────────────
        if (!(activity?.isInPictureInPictureMode == true)) {
            DoubleTapSeekOverlay(seekState = doubleTapSeekState)
        }

        SwipeSeekOverlay(
            targetSec = swipeSeekTargetSec,
            dragStartSec = swipeDragStartSec,
            isPipMode = activity?.isInPictureInPictureMode == true
        )

        // ── Top Hold for 2x Fast-Forward Banner ──────────────────────────────
        if (!(activity?.isInPictureInPictureMode == true)) {
            HoldToFastForward(
                visible = uiState.isFastForwarding,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (controlsVisible) 72.dp else 36.dp)
            )
        }

        PlayerLoadingIndicator(isLoading = uiState.isLoading)

        PlayerErrorState(error = uiState.error)

        if (uiState.fileLoaded && !(activity?.isInPictureInPictureMode == true)) {

            // ── Top bar ──────────────────────────────────────────────────────
            PlayerTopBarContainer(
                controlsVisible = controlsVisible,
                isLocked = uiState.isLocked,
                swipeSeekTargetSec = swipeSeekTargetSec,
                fileName = uiState.fileName,
                hwdecCurrent = uiState.hwdecCurrent,
                onBack = onBack,
                onSelectAudioTrack = { viewModel.showDialog(ActiveDialog.Audio) },
                onSelectSubtitleTrack = { viewModel.showDialog(ActiveDialog.Subtitle) },
                onSelectDecoder = { viewModel.showDialog(ActiveDialog.Decoder) },
                onMoreOptions = { 
                    if (uiState.activeDialog == ActiveDialog.MoreMenu) viewModel.dismissDialog()
                    else viewModel.showDialog(ActiveDialog.MoreMenu)
                },
                onHideControls = { controlsVisible = false },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // ── Center play/pause ────────────────────────────────────────────
            PlayerCenterContainer(
                controlsVisible = controlsVisible,
                isLocked = uiState.isLocked,
                swipeSeekTargetSec = swipeSeekTargetSec,
                isPlaying = uiState.isPlaying,
                onTogglePlay = viewModel::togglePlay,
                modifier = Modifier.align(Alignment.Center)
            )

            // ── Bottom controls ──────────────────────────────────────────────
            PlayerBottomContainer(
                controlsVisible = controlsVisible,
                isLocked = uiState.isLocked,
                swipeSeekTargetSec = swipeSeekTargetSec,
                progressState = progressState,
                isAutoRotation = uiState.isAutoRotation,
                currentFitMode = uiState.fitMode,
                hasPrevious = uiState.currentPlaylistIndex > 0,
                hasNext = uiState.currentPlaylistIndex >= 0 &&
                                    uiState.currentPlaylistIndex < uiState.playlist.size - 1,
                onSeekGesture = { ms -> viewModel.onSliderDragChange(ms / 1000.0) },
                onSeekCommit = { ms -> viewModel.onSliderDragEnd(ms / 1000.0) },
                onDragStart = { viewModel.onSliderDragStart(progressState.positionSec) },
                onToggleAutoRotation = { viewModel.toggleAutoRotation() },
                onToggleFitMode = { viewModel.cycleFitMode() },
                onEnterPip = { enterPip(activity) },
                onToggleLock = { viewModel.toggleLock() },
                onPrevious = { viewModel.playPrevious() },
                onNext = { viewModel.playNext() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        PlayerUnlockButton(
            isLocked = uiState.isLocked,
            isPipMode = activity?.isInPictureInPictureMode == true,
            onUnlock = viewModel::toggleLock
        )

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

@Composable
private fun PlayerTopBarContainer(
    controlsVisible: Boolean,
    isLocked: Boolean,
    swipeSeekTargetSec: Double?,
    fileName: String,
    hwdecCurrent: String,
    onBack: () -> Unit,
    onSelectAudioTrack: () -> Unit,
    onSelectSubtitleTrack: () -> Unit,
    onSelectDecoder: () -> Unit,
    onMoreOptions: () -> Unit,
    onHideControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(swipeSeekTargetSec) {
        if (swipeSeekTargetSec != null) {
            onHideControls()
        }
    }
    AnimatedVisibility(
        visible = controlsVisible && !isLocked && swipeSeekTargetSec == null,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
            .systemBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        PlayerTopBar(
            fileName              = fileName,
            currentDecoder        = hwdecCurrent,
            onBack                = onBack,
            onSelectAudioTrack    = onSelectAudioTrack,
            onSelectSubtitleTrack = onSelectSubtitleTrack,
            onSelectDecoder       = onSelectDecoder,
            onMoreOptions         = onMoreOptions
        )
    }
}

@Composable
private fun PlayerCenterContainer(
    controlsVisible: Boolean,
    isLocked: Boolean,
    swipeSeekTargetSec: Double?,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = controlsVisible && !isLocked && swipeSeekTargetSec == null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        PlayerCenterPlayPause(
            isPlaying = isPlaying,
            onClick   = onTogglePlay
        )
    }
}

@Composable
private fun PlayerBottomContainer(
    controlsVisible: Boolean,
    isLocked: Boolean,
    swipeSeekTargetSec: Double?,
    progressState: PlaybackProgressState,
    isAutoRotation: Boolean,
    currentFitMode: VideoFitMode,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onSeekGesture: (Long) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onDragStart: () -> Unit,
    onToggleAutoRotation: () -> Unit,
    onToggleFitMode: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleLock: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = controlsVisible && !isLocked && swipeSeekTargetSec == null,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
            .systemBarsPadding()
    ) {
        PlayerBottomControls(
            progressState        = progressState,
            isAutoRotation       = isAutoRotation,
            currentFitMode       = currentFitMode,
            contentPadding       = WindowInsets.displayCutout.asPaddingValues(),
            onSeekGesture        = onSeekGesture,
            onSeekCommit         = onSeekCommit,
            onDragStart          = onDragStart,
            onDragEnd            = { /* already handled inside onSeekCommit path */ },
            onToggleAutoRotation = onToggleAutoRotation,
            onToggleFitMode      = onToggleFitMode,
            onEnterPip           = onEnterPip,
            isLocked             = isLocked,
            onToggleLock         = onToggleLock,
            hasPrevious          = hasPrevious,
            hasNext              = hasNext,
            onPrevious           = onPrevious,
            onNext               = onNext
        )
    }
}

