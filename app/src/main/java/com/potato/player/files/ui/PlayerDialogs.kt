package com.potato.player.player.ui

import androidx.compose.runtime.Composable
import androidx.media3.ui.PlayerView
import com.potato.player.player.ui.dialog.AudioTrackDialog
import com.potato.player.player.ui.dialog.SpeedDialog
import com.potato.player.player.ui.dialog.SubtitleTrackDialog
import com.potato.player.player.ui.subtitle.SubtitleSettings
import com.potato.player.player.ui.subtitle.SubtitleSizeDialog
import com.potato.player.player.ui.subtitle.applySubtitleSettings
import com.potato.player.viewmodel.PlayerViewModel
import com.potato.player.viewmodel.PlayerUiState

@Composable
fun PlayerDialogs(
    uiState: PlayerUiState,
    subtitleSettings: SubtitleSettings,
    onSubtitleSettingsChange: (SubtitleSettings) -> Unit,
    playerViewRef: PlayerView?,
    showAudioDialog: Boolean,
    showSubtitleDialog: Boolean,
    showSubtitleSizeDialog: Boolean,
    showSpeedDialog: Boolean,
    onAudioDialogDismiss: () -> Unit,
    onSubtitleDialogDismiss: () -> Unit,
    onSubtitleSizeDismiss: () -> Unit,
    onSpeedDialogDismiss: () -> Unit,
    viewModel: PlayerViewModel,
    onShowSubtitleSizeDialog: () -> Unit,
) {
    if (showAudioDialog) {
        AudioTrackDialog(
            tracks = uiState.audioTracks,
            selectedId = uiState.selectedAudioTrackId,
            onSelect = { id ->
                viewModel.selectAudioTrack(id)
                onAudioDialogDismiss()
            },
            onDismiss = onAudioDialogDismiss,
        )
    }

    if (showSubtitleDialog) {
        SubtitleTrackDialog(
            tracks = uiState.subtitleTracks,
            selectedId = uiState.selectedSubtitleTrackId,
            onSelect = { id ->
                viewModel.selectSubtitleTrack(id)
                onSubtitleDialogDismiss()
            },
            onDisable = {
                viewModel.disableSubtitles()
                onSubtitleDialogDismiss()
            },
            onDismiss = onSubtitleDialogDismiss,
            onAppearanceClick = onShowSubtitleSizeDialog,
        )
    }

    if (showSubtitleSizeDialog) {
        SubtitleSizeDialog(
            settings = subtitleSettings,
            onConfirm = { newSettings ->
                onSubtitleSettingsChange(newSettings)
                playerViewRef?.subtitleView?.let {
                    applySubtitleSettings(it, newSettings)
                }
                onSubtitleSizeDismiss()
            },
            onDismiss = onSubtitleSizeDismiss,
        )
    }

    if (showSpeedDialog) {
        SpeedDialog(
            currentSpeed = uiState.playbackSpeed,
            onSpeedSelected = { speed -> viewModel.setPlaybackSpeed(speed) },
            onDismiss = onSpeedDialogDismiss
        )
    }
}
