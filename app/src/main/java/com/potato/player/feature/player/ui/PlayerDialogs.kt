package com.potato.player.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.potato.player.feature.player.PlayerViewModel
import com.potato.player.feature.player.state.*
import com.potato.player.feature.player.controls.AudioTrackDialog
import com.potato.player.feature.player.controls.PlayerDecoderDialog
import com.potato.player.feature.player.controls.PlayerRightSideSheet
import com.potato.player.feature.player.controls.SubtitleTrackDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ponytail: move only, zero new logic
@Composable
fun PlayerModals(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onLaunchFilePicker: () -> Unit
) {
    val context = LocalContext.current
    val activeDialog by viewModel.activeDialog.collectAsStateWithLifecycle()
    val trackState by viewModel.trackManager.trackState.collectAsStateWithLifecycle()

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
