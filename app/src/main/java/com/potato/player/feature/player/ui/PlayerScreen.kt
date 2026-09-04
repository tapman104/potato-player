package com.potato.player.feature.player.ui

import com.potato.player.feature.player.PlayerViewModel
import com.potato.player.feature.player.VideoFitMode
import com.potato.player.feature.player.state.*
import android.os.Build
import android.app.PictureInPictureParams
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    val isSeeking by viewModel.isSeekingFlow.collectAsStateWithLifecycle()
    val activeDialog by viewModel.activeDialog.collectAsStateWithLifecycle()
    val currentPlaylistIndex by viewModel.playlistManager.currentIndex.collectAsStateWithLifecycle()
    val currentPlaylist by viewModel.playlistManager.playlist.collectAsStateWithLifecycle()

    BackHandler {
        if (!viewModel.uiState.value.isLocked) {
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
    PlayerLifecycleEffectContainer(activity = activity, viewModel = viewModel)

    val swipeSeekTargetSec: Double? = null
    var isGestureActive by remember { mutableStateOf(false) }

    val (controlsVisible, onUserInteraction) = rememberControlsVisibility(
        isPlaying = viewModel.uiState.value.isPlaying,
        hideDelayMs = viewModel.uiState.value.controlsHideDelay.toLong(),
        isSeeking = isSeeking,
        isFastForwarding = viewModel.uiState.value.isFastForwarding,
        isLocked = viewModel.uiState.value.isLocked,
        isSwipingVolumeOrBrightness = isGestureActive,
        isPipMode = activity?.isInPictureInPictureMode == true,
        swipeSeekTargetSec = swipeSeekTargetSec
    )

    // Fix 4 — Stable lambdas: wrap each single-ViewModel-call lambda in remember(viewModel)
    // so recomposition doesn't allocate new instances on every frame.
    val onSelectAudioTrack    = remember(viewModel) { { viewModel.showDialog(ActiveDialog.Audio) } }
    val onSelectSubtitleTrack = remember(viewModel) { { viewModel.showDialog(ActiveDialog.Subtitle) } }
    val onSelectDecoder       = remember(viewModel) { { viewModel.showDialog(ActiveDialog.Decoder) } }
    val onTogglePlay          = remember(viewModel) { { viewModel.togglePlay() } }
    val onToggleLock          = remember(viewModel) { { viewModel.toggleLock() } }
    val onToggleAutoRotation  = remember(viewModel) { { viewModel.toggleAutoRotation() } }
    val onToggleFitMode       = remember(viewModel) { { viewModel.cycleFitMode() } }
    val onEnterPip            = remember(viewModel, activity) { { enterPip(activity) } }
    val onPrevious            = remember(viewModel) { { viewModel.playPrevious() } }
    val onNext                = remember(viewModel) { { viewModel.playNext() } }
    val onSeekGesture         = remember(viewModel) { { ms: Long -> viewModel.onSliderDragChange(ms / 1000.0) } }
    val onSeekCommit          = remember(viewModel) { { ms: Long -> viewModel.onSliderDragEnd(ms / 1000.0) } }
    // onBack: captures isExternalIntent (stable param) + activity (stable remembered) + onBack param
    val onBackStable          = remember(viewModel, activity, isExternalIntent, onBack) {
        {
            if (isExternalIntent) {
                activity?.finish()
            } else {
                onBack()
            }
            Unit
        }
    }
    // onMoreOptions: reads activeDialog (changes) — cannot be wrapped in remember(viewModel); left inline below


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
        if (!viewModel.uiState.value.isLocked) {
            Box(modifier = Modifier.clearAndSetSemantics {}) {
                PlayerGestureBox(
                    viewModel = viewModel,
                    positionProvider = { viewModel.progressState.value.positionSec },
                    durationProvider = { viewModel.progressState.value.durationSec },
                    onToggleControls = { onUserInteraction() },
                    onGestureActive = { isGestureActive = it }
                )
            }
        }

        // ── Top Hold for 2x Fast-Forward Banner ──────────────────────────────
        // Fix 5: controlsVisible-dependent padding moved inside PlayerHoldToFastForwardContainer
        // so PlayerScreen body has zero reads of controlsVisible outside param pass-throughs.
        PlayerHoldToFastForwardContainer(
            isPipMode = activity?.isInPictureInPictureMode == true,
            visible = { viewModel.uiState.value.isFastForwarding },
            controlsVisible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        PlayerLoadingIndicatorContainer(isLoading = { viewModel.uiState.value.isLoading })

        PlayerErrorStateContainer(error = { viewModel.uiState.value.error })

        // ── Top bar ──────────────────────────────────────────────────────
        PlayerTopBarContainer(
            fileLoaded = { viewModel.uiState.value.fileLoaded },
            isPipMode = activity?.isInPictureInPictureMode == true,
            controlsVisible = controlsVisible,
            isLocked = { viewModel.uiState.value.isLocked },
            swipeSeekTargetSec = swipeSeekTargetSec,
            fileName = { viewModel.uiState.value.fileName },
            hwdecCurrent = { viewModel.uiState.value.hwdecCurrent },
            onBack = onBackStable,
            onSelectAudioTrack = onSelectAudioTrack,
            onSelectSubtitleTrack = onSelectSubtitleTrack,
            onSelectDecoder = onSelectDecoder,
            onMoreOptions = {
                // reads activeDialog (changing state) — cannot be stable-wrapped
                if (activeDialog == ActiveDialog.MoreMenu) viewModel.dismissDialog()
                else viewModel.showDialog(ActiveDialog.MoreMenu)
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // ── Center play/pause ────────────────────────────────────────────
        PlayerCenterContainer(
            fileLoaded = { viewModel.uiState.value.fileLoaded },
            isPipMode = activity?.isInPictureInPictureMode == true,
            controlsVisible = controlsVisible,
            isLocked = { viewModel.uiState.value.isLocked },
            swipeSeekTargetSec = swipeSeekTargetSec,
            isPlaying = { viewModel.uiState.value.isPlaying },
            onTogglePlay = onTogglePlay,
            modifier = Modifier.align(Alignment.Center)
        )

        // ── Bottom controls ──────────────────────────────────────────────
        PlayerBottomContainer(
            fileLoaded = { viewModel.uiState.value.fileLoaded },
            isPipMode = activity?.isInPictureInPictureMode == true,
            controlsVisible = controlsVisible,
            isLocked = { viewModel.uiState.value.isLocked },
            showLockButton = { viewModel.uiState.value.lockButtonEnabled },
            swipeSeekTargetSec = swipeSeekTargetSec,
            viewModel = viewModel,
            isAutoRotation = { viewModel.uiState.value.isAutoRotation },
            currentFitMode = { viewModel.uiState.value.fitMode },
            hasPrevious = currentPlaylistIndex > 0,
            hasNext = currentPlaylistIndex >= 0 &&
                                currentPlaylistIndex < currentPlaylist.size - 1,
            onSeekGesture = onSeekGesture,
            onSeekCommit = onSeekCommit,
            onToggleAutoRotation = onToggleAutoRotation,
            onToggleFitMode = onToggleFitMode,
            onEnterPip = onEnterPip,
            onToggleLock = onToggleLock,
            onPrevious = onPrevious,
            onNext = onNext,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        PlayerUnlockButtonContainer(
            lockButtonEnabled = { viewModel.uiState.value.lockButtonEnabled },
            isLocked = { viewModel.uiState.value.isLocked },
            isPipMode = activity?.isInPictureInPictureMode == true,
            onUnlock = viewModel::toggleLock
        )

        // ponytail: move only, zero new logic
        PlayerModalsContainer(
            isPipMode = activity?.isInPictureInPictureMode == true,
            viewModel = viewModel,
            onLaunchFilePicker = { subtitleLauncher.launch(arrayOf("*/*")) }
        )
    }
}

// Fix 5 — HoldToFastForward wrapper that owns the controlsVisible-dependent padding.
// PlayerScreen body passes controlsVisible as a parameter; the Dp calculation stays here.
@Composable
private fun PlayerHoldToFastForwardContainer(
    isPipMode: Boolean,
    visible: () -> Boolean,
    controlsVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isPipMode) {
        HoldToFastForward(
            visible = visible(),
            modifier = modifier.padding(top = if (controlsVisible) 72.dp else 36.dp)
        )
    }
}

@Composable
private fun PlayerTopBarContainer(
    fileLoaded: () -> Boolean,
    isPipMode: Boolean,
    controlsVisible: Boolean,
    isLocked: () -> Boolean,
    swipeSeekTargetSec: Double?,
    fileName: () -> String,
    hwdecCurrent: () -> String,
    onBack: () -> Unit,
    onSelectAudioTrack: () -> Unit,
    onSelectSubtitleTrack: () -> Unit,
    onSelectDecoder: () -> Unit,
    onMoreOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (fileLoaded() && !isPipMode) {
        AnimatedVisibility(
            visible = controlsVisible && !isLocked() && swipeSeekTargetSec == null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = modifier
                .systemBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout)
        ) {
            PlayerTopBar(
                fileName              = fileName(),
                currentDecoder        = hwdecCurrent(),
                onBack                = onBack,
                onSelectAudioTrack    = onSelectAudioTrack,
                onSelectSubtitleTrack = onSelectSubtitleTrack,
                onSelectDecoder       = onSelectDecoder,
                onMoreOptions         = onMoreOptions
            )
        }
    }
}

@Composable
private fun PlayerCenterContainer(
    fileLoaded: () -> Boolean,
    isPipMode: Boolean,
    controlsVisible: Boolean,
    isLocked: () -> Boolean,
    swipeSeekTargetSec: Double?,
    isPlaying: () -> Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (fileLoaded() && !isPipMode) {
        AnimatedVisibility(
            visible = controlsVisible && !isLocked() && swipeSeekTargetSec == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = modifier
        ) {
            PlayerCenterPlayPause(
                isPlaying = isPlaying(),
                onClick   = onTogglePlay
            )
        }
    }
}

@Composable
private fun PlayerBottomContainer(
    fileLoaded: () -> Boolean,
    isPipMode: Boolean,
    controlsVisible: Boolean,
    isLocked: () -> Boolean,
    showLockButton: () -> Boolean,
    swipeSeekTargetSec: Double?,
    viewModel: PlayerViewModel,
    isAutoRotation: () -> Boolean,
    currentFitMode: () -> VideoFitMode,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onSeekGesture: (Long) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onToggleAutoRotation: () -> Unit,
    onToggleFitMode: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleLock: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressState by viewModel.progressState.collectAsStateWithLifecycle()

    if (fileLoaded() && !isPipMode) {
        AnimatedVisibility(
            visible = controlsVisible && !isLocked() && swipeSeekTargetSec == null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = modifier
                .systemBarsPadding()
        ) {
            PlayerBottomControls(
                progressState        = progressState,
                onSliderDragStart    = viewModel::onSliderDragStart,
                isAutoRotation       = isAutoRotation(),
                currentFitMode       = currentFitMode(),
                contentPadding       = WindowInsets.displayCutout.asPaddingValues(),
                onSeekGesture        = onSeekGesture,
                onSeekCommit         = onSeekCommit,
                onDragEnd            = { /* already handled inside onSeekCommit path */ },
                onToggleAutoRotation = onToggleAutoRotation,
                onToggleFitMode      = onToggleFitMode,
                onEnterPip           = onEnterPip,
                isLocked             = isLocked(),
                onToggleLock         = onToggleLock,
                showLockButton       = showLockButton(),
                hasPrevious          = hasPrevious,
                hasNext              = hasNext,
                onPrevious           = onPrevious,
                onNext               = onNext
            )
        }
    }
}

@Composable
private fun PlayerLoadingIndicatorContainer(isLoading: () -> Boolean) {
    PlayerLoadingIndicator(isLoading = isLoading())
}

@Composable
private fun PlayerErrorStateContainer(error: () -> String?) {
    PlayerErrorState(error = error())
}

@Composable
private fun PlayerUnlockButtonContainer(
    lockButtonEnabled: () -> Boolean,
    isLocked: () -> Boolean,
    isPipMode: Boolean,
    onUnlock: () -> Unit
) {
    if (lockButtonEnabled()) {
        PlayerUnlockButton(
            isLocked = isLocked(),
            isPipMode = isPipMode,
            onUnlock = onUnlock
        )
    }
}

@Composable
private fun PlayerLifecycleEffectContainer(activity: android.app.Activity?, viewModel: PlayerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerLifecycleEffect(activity = activity, uiState = uiState, viewModel = viewModel)
}

@Composable
private fun PlayerModalsContainer(isPipMode: Boolean, viewModel: PlayerViewModel, onLaunchFilePicker: () -> Unit) {
    if (!isPipMode) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        PlayerModals(uiState = uiState, viewModel = viewModel, onLaunchFilePicker = onLaunchFilePicker)
    }
}
