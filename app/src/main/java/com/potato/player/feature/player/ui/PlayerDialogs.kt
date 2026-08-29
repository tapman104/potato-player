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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ponytail: move only, zero new logic
@Composable
fun PlayerModals(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onLaunchFilePicker: () -> Unit
) {
    val context = LocalContext.current
    val activeDialog = uiState.activeDialog

    PlayerDecoderDialog(
        visible = activeDialog == ActiveDialog.Decoder,
        currentDecoder = uiState.hwdecCurrent,
        onSelectDecoder = { mode -> viewModel.setDecoder(mode) },
        onDismiss = { viewModel.dismissDialog() }
    )

    AudioTrackDialog(
        visible = activeDialog == ActiveDialog.Audio,
        tracks = uiState.audioTracks,
        currentTrackId = uiState.currentAudioTrackId,
        onSelectTrack = { viewModel.onSelectAudioTrack(it) },
        onDismiss = { viewModel.dismissDialog() }
    )

    SubtitleTrackDialog(
        visible = activeDialog == ActiveDialog.Subtitle,
        tracks = uiState.subtitleTracks,
        currentTrackId = uiState.currentSubtitleTrackId,
        onSelectTrack = { viewModel.onSelectSubtitleTrack(it) },
        onLaunchFilePicker = onLaunchFilePicker,
        onDismiss = { viewModel.dismissDialog() },
        uiState = uiState,
        onSetSubtitleAppearance = { scale, pos -> viewModel.setSubtitleAppearance(scale, pos) },
        onPreviewSubtitleAppearance = { scale, pos -> 
            viewModel.setSubScale(scale)
            viewModel.setSubPos(pos)
        }
    )

    // ponytail: gate sheet on fileLoaded so it never appears on an empty player
    if (uiState.fileLoaded) {
        PlayerRightSideSheet(
            visible = activeDialog == ActiveDialog.MoreMenu || activeDialog == ActiveDialog.Speed,
            currentSpeed = uiState.playbackSpeed,
            onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
            onShowAudioDialog = { viewModel.showDialog(ActiveDialog.Audio) },
            onShowSubtitleDialog = { viewModel.showDialog(ActiveDialog.Subtitle) },
            onDismiss = { viewModel.dismissDialog() }
        )
    }
}
