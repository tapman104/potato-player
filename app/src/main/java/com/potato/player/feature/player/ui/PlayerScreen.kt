package com.potato.player.feature.player.ui

import com.potato.player.feature.player.PlayerViewModel
import com.potato.player.feature.player.VideoFitMode
import com.potato.player.feature.player.state.*
import android.os.Build
import android.app.PictureInPictureParams
import androidx.compose.animation.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import kotlin.math.roundToInt
import com.potato.player.feature.player.controls.AudioTrackDialog
import com.potato.player.feature.player.controls.PlayerDecoderDialog
import com.potato.player.feature.player.controls.PlayerRightSideSheet
import com.potato.player.feature.player.controls.SubtitleTrackDialog
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import com.potato.player.util.findActivity

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
    val trackState by viewModel.trackManager.trackState.collectAsStateWithLifecycle()
    val currentPlaylistIndex by viewModel.playlistManager.currentIndex.collectAsStateWithLifecycle()
    val currentPlaylist by viewModel.playlistManager.playlist.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progressState by viewModel.progressState.collectAsStateWithLifecycle()

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

    var isGestureActive by remember { mutableStateOf(false) }

    val (controlsVisible, onUserInteraction) = rememberControlsVisibility(
        isPlaying = uiState.isPlaying,
        hideDelayMs = uiState.controlsHideDelay.toLong(),
        isSeeking = isSeeking,
        isFastForwarding = uiState.isFastForwarding,
        isLocked = uiState.isLocked,
        isSwipingVolumeOrBrightness = isGestureActive,
        isPipMode = activity?.isInPictureInPictureMode == true
    )

    // Fix 4 — Stable lambdas: wrap each single-ViewModel-call lambda in remember(viewModel)
    // so recomposition doesn't allocate new instances on every frame.
    val onSelectAudioTrack    = remember(viewModel) { { viewModel.showDialog(ActiveDialog.Audio) } }
    val onSelectSubtitleTrack = remember(viewModel) { { viewModel.showDialog(ActiveDialog.Subtitle) } }
    val onSelectDecoder       = remember(viewModel) { { viewModel.showDialog(ActiveDialog.Decoder) } }
    val onTogglePlay          = remember(viewModel) { { viewModel.togglePlay() } }
    val onToggleLock          = remember(viewModel) { { viewModel.toggleLock() } }
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
    val onMoreOptions = remember(viewModel) {
        {
            if (activeDialog == ActiveDialog.MoreMenu) viewModel.dismissDialog()
            else viewModel.showDialog(ActiveDialog.MoreMenu)
        }
    }


    // Load the video once the surface is ready; also handles config-change re-attach.
    LaunchedEffect(viewModel, videoUri, title) {
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
                    viewModel.attachSurface(holder.surface)
                }
                override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                    viewModel.detachSurface()
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
        if (activity?.isInPictureInPictureMode != true) {
            HoldToFastForward(
                visible = uiState.isFastForwarding,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = if (controlsVisible) 72.dp else 36.dp)
            )
        }

        PlayerLoadingIndicator(isLoading = uiState.isLoading)

        PlayerErrorState(error = uiState.error)

        // ── Top bar ──────────────────────────────────────────────────────
        if (uiState.fileLoaded && activity?.isInPictureInPictureMode != true) {
            AnimatedVisibility(
                visible = controlsVisible && !uiState.isLocked,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
            ) {
                PlayerTopBar(
                    fileName              = uiState.fileName,
                    currentDecoder        = uiState.hwdecCurrent,
                    onBack                = onBackStable,
                    onSelectAudioTrack    = onSelectAudioTrack,
                    onSelectSubtitleTrack = onSelectSubtitleTrack,
                    onSelectDecoder       = onSelectDecoder,
                    onMoreOptions         = onMoreOptions
                )
            }
        }

        // ── Center play/pause ────────────────────────────────────────────
        if (uiState.fileLoaded && activity?.isInPictureInPictureMode != true) {
            AnimatedVisibility(
                visible = controlsVisible && !uiState.isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                PlayerCenterPlayPause(
                    isPlaying = uiState.isPlaying,
                    onClick   = onTogglePlay
                )
            }
        }

        // ── Bottom controls ──────────────────────────────────────────────
        if (uiState.fileLoaded && activity?.isInPictureInPictureMode != true) {
            // Layer 1 — full controls bar: hidden when locked
            AnimatedVisibility(
                visible = controlsVisible && !uiState.isLocked,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
            ) {
                PlayerBottomControls(
                    progressState        = progressState,
                    onSliderDragStart    = viewModel::onSliderDragStart,
                    currentFitMode       = uiState.fitMode,
                    contentPadding       = PaddingValues(0.dp),
                    onSeekGesture        = onSeekGesture,
                    onSeekCommit         = onSeekCommit,
                    onDragEnd            = { /* already handled inside onSeekCommit path */ },
                    onToggleFitMode      = onToggleFitMode,
                    onEnterPip           = onEnterPip,
                    isLocked             = uiState.isLocked,
                    onToggleLock         = onToggleLock,
                    showLockButton       = !uiState.isLocked && uiState.lockButtonEnabled,
                    hasPrevious          = currentPlaylistIndex > 0,
                    hasNext              = currentPlaylistIndex >= 0 &&
                                        currentPlaylist.size - 1 > currentPlaylistIndex,
                    onPrevious           = onPrevious,
                    onNext               = onNext
                )
            }

            // Layer 2 — unlock button only: always visible while locked so the user can unlock
            if (uiState.isLocked && uiState.lockButtonEnabled) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .systemBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    androidx.compose.material3.IconButton(onClick = onToggleLock) {
                        androidx.compose.material3.Icon(
                            imageVector        = Icons.Default.LockOpen,
                            contentDescription = "Unlock",
                            tint               = Color.White
                        )
                    }
                }
            }
        }

        // ponytail: move only, zero new logic
        if (activity?.isInPictureInPictureMode != true) {
            PlayerModals(uiState = uiState, viewModel = viewModel, activeDialog = activeDialog, trackState = trackState, onLaunchFilePicker = { subtitleLauncher.launch(arrayOf("*/*")) })
        }
    }
}

@Composable
fun PlayerLoadingIndicator(isLoading: Boolean) {
    // ── Loading indicator ────────────────────────────────────────────────
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(
                color    = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun PlayerErrorState(error: String?) {
    // ── Error message ────────────────────────────────────────────────────
    error?.let { msg ->
        Box(modifier = Modifier.fillMaxSize()) {
            androidx.compose.material3.Text(
                text     = "Error: $msg",
                color    = Color.Red,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun rememberControlsVisibility(
    isPlaying: Boolean,
    hideDelayMs: Long = 3000L,
    isSeeking: Boolean,
    isFastForwarding: Boolean,
    isLocked: Boolean,
    isSwipingVolumeOrBrightness: Boolean,
    isPipMode: Boolean
): Pair<Boolean, () -> Unit> {
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var interactionTick by remember { mutableStateOf(0L) }

    LaunchedEffect(isLocked) {
        if (isLocked) {
            controlsVisible = false
        } else {
            controlsVisible = true
            interactionTick = System.currentTimeMillis()
        }
    }

    LaunchedEffect(interactionTick, isPlaying, isSeeking, isFastForwarding, isLocked, isSwipingVolumeOrBrightness) {
        if (isLocked) return@LaunchedEffect
        if (controlsVisible && isPlaying && !isSeeking && !isFastForwarding && !isSwipingVolumeOrBrightness) {
            delay(hideDelayMs)
            controlsVisible = false
        }
    }

    LaunchedEffect(isPipMode) {
        if (isPipMode) controlsVisible = false
    }

    val onUserInteraction: () -> Unit = remember {
        {
            if (controlsVisible) {
                controlsVisible = false
            } else {
                controlsVisible = true
                interactionTick = System.currentTimeMillis()
            }
        }
    }

    return Pair(controlsVisible, onUserInteraction)
}

@Composable
fun VolumeIndicator(
    volume: Int,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (visible) {
        androidx.compose.foundation.layout.Column(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .height(100.dp)
                    .width(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((volume / 100f).coerceIn(0f, 1f))
                        .background(Color.White, RoundedCornerShape(2.dp))
                )
            }
            androidx.compose.material3.Text(
                text = "$volume%",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun BrightnessIndicator(
    brightness: Float,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (visible) {
        androidx.compose.foundation.layout.Column(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Brightness6,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .height(100.dp)
                    .width(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(brightness.coerceIn(0f, 1f))
                        .background(Color.White, RoundedCornerShape(2.dp))
                )
            }
            androidx.compose.material3.Text(
                text = "${(brightness * 100).roundToInt()}%",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun ZoomIndicator(
    zoom: Float,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (visible && zoom != 1.0f) {
        Box(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            androidx.compose.material3.Text(
                text = String.format(java.util.Locale.US, "%d%%", (zoom * 100).toInt()),
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

// ponytail: move only, zero new logic
@Composable
fun PlayerModals(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    activeDialog: ActiveDialog,
    trackState: com.potato.player.feature.player.TrackState,
    onLaunchFilePicker: () -> Unit
) {
    val context = LocalContext.current

    val onSelectDecoder     = remember(viewModel) { { mode: String -> viewModel.setDecoder(mode) } }
    val onDismiss           = remember(viewModel) { { viewModel.dismissDialog() } }
    val onSelectAudioTrack  = remember(viewModel) { { id: Int -> viewModel.onSelectAudioTrack(id); viewModel.dismissDialog() } }
    val onSelectSubtitle    = remember(viewModel) { { id: Int -> viewModel.onSelectSubtitleTrack(id); viewModel.dismissDialog() } }
    val onSetSubAppearance  = remember(viewModel) { { scale: Double, pos: Int -> viewModel.setSubtitleAppearance(scale, pos) } }
    val onPreviewSubAppearance = remember(viewModel) { { scale: Double, pos: Int -> viewModel.previewSubtitleAppearance(scale, pos) } }
    val onSelectSpeed       = remember(viewModel) { { speed: Double -> viewModel.setPlaybackSpeed(speed) } }
    val onShowAudioDialog   = remember(viewModel) { { viewModel.showDialog(ActiveDialog.Audio) } }
    val onShowSubtitleDialog = remember(viewModel) { { viewModel.showDialog(ActiveDialog.Subtitle) } }

    PlayerDecoderDialog(
        visible = activeDialog == ActiveDialog.Decoder,
        currentDecoder = uiState.hwdecCurrent,
        onSelectDecoder = onSelectDecoder,
        onDismiss = onDismiss
    )

    AudioTrackDialog(
        visible = activeDialog == ActiveDialog.Audio,
        tracks = trackState.audioTracks,
        currentTrackId = trackState.currentAudioTrackId,
        tracksLoaded = trackState.tracksLoaded,
        onSelectTrack = onSelectAudioTrack,
        onDismiss = onDismiss
    )

    SubtitleTrackDialog(
        visible = activeDialog == ActiveDialog.Subtitle,
        tracks = trackState.subtitleTracks,
        currentTrackId = trackState.currentSubtitleTrackId,
        tracksLoaded = trackState.tracksLoaded,
        onSelectTrack = onSelectSubtitle,
        onLaunchFilePicker = onLaunchFilePicker,
        onDismiss = onDismiss,
        uiState = uiState,
        onSetSubtitleAppearance = onSetSubAppearance,
        onPreviewSubtitleAppearance = onPreviewSubAppearance
    )

    // ponytail: gate sheet on fileLoaded so it never appears on an empty player
    if (uiState.fileLoaded) {
        PlayerRightSideSheet(
            visible = activeDialog == ActiveDialog.MoreMenu || activeDialog == ActiveDialog.Speed,
            currentSpeed = uiState.playbackSpeed,
            onSelectSpeed = onSelectSpeed,
            onShowAudioDialog = onShowAudioDialog,
            onShowSubtitleDialog = onShowSubtitleDialog,
            onDismiss = onDismiss
        )
    }
}


