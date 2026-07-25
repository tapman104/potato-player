package com.potato.player.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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
    viewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val activeSheet by viewModel.dialogs.activeSheet.collectAsStateWithLifecycle()

    when (activeSheet) {
        ActiveSheet.DECODER -> {
            PlayerDecoderDialog(
                currentDecoder = uiState.hwdecCurrent,
                onSelectDecoder = { mode -> viewModel.setDecoder(mode) },
                onDismiss = { viewModel.dialogs.onDismissDecoderDialog() }
            )
        }
        ActiveSheet.AUDIO -> {
            AudioTrackDialog(
                tracks = uiState.audioTracks,
                currentTrackId = uiState.currentAudioTrackId,
                onSelectTrack = { viewModel.onSelectAudioTrack(it) },
                onDismiss = { viewModel.dialogs.onDismissAudioDialog() }
            )
        }
        ActiveSheet.SUBTITLE -> {
            SubtitleTrackDialog(
                tracks = uiState.subtitleTracks,
                currentTrackId = uiState.currentSubtitleTrackId,
                onSelectTrack = { viewModel.onSelectSubtitleTrack(it) },
                onLoadExternal = { uri -> viewModel.onLoadExternalSubtitle(uri, context) },
                onDismiss = { viewModel.dialogs.onDismissSubtitleDialog() },
                uiState = uiState,
                onSetSubtitleAppearance = { scale, pos -> viewModel.setSubtitleAppearance(scale, pos) },
                onResetSubtitleAppearance = { viewModel.resetSubtitleAppearance() }
            )
        }
        else -> Unit
    }

    // ponytail: gate sheet on fileLoaded so it never appears on an empty player
    if (uiState.fileLoaded) {
        PlayerRightSideSheet(
            visible = activeSheet == ActiveSheet.MORE_MENU || activeSheet == ActiveSheet.SPEED,
            currentSpeed = uiState.playbackSpeed,
            onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
            onShowAudioDialog = { viewModel.dialogs.onShowAudioDialog() },
            onShowSubtitleDialog = { viewModel.dialogs.onShowSubtitleDialog() },
            onDismiss = {
                if (activeSheet == ActiveSheet.MORE_MENU) viewModel.dialogs.onMoreMenuDismiss()
                else if (activeSheet == ActiveSheet.SPEED) viewModel.dialogs.onDismissSpeedDialog()
                else viewModel.dialogs.onMoreMenuDismiss()
            }
        )
    }
}

