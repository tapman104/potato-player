package com.potato.player.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.potato.player.viewmodel.PlayerUiState

@Composable
fun ControlsRow(
    uiState: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayPauseButton(
                isPlaying = uiState.isPlaying,
                isLoading = uiState.isLoading,
                isEnded = uiState.isEnded,
                onClick = onTogglePlayPause,
                size = 52.dp,
                tint = Color.White,
                backgroundColor = Color.White.copy(alpha = 0.15f),
            )

            PlayerSeekBar(
                positionMs = 0L,
                durationMs = 0L,
                bufferedPositionMs = 0L,
                onSeek = onSeek,
                modifier = Modifier.weight(1f),
                trackHeight = 4.dp,
                thumbRadius = 7.dp,
                showTimeLabels = true,
            )
        }
    }
}
