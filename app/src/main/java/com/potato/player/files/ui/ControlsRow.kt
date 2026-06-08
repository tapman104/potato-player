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

/**
 * Horizontal control bar rendered at the bottom of the player.
 *
 * Layout (left â†’ right):
 *
 *   [PlayPause]   [SeekBar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€]
 *
 * The seek bar fills all remaining horizontal space. Time labels are
 * rendered inside [PlayerSeekBar] below the track.
 *
 * This composable is stateless: all state comes from [uiState] and all
 * user intents propagate up via [onTogglePlayPause] and [onSeek].
 *
 * @param uiState Current player state snapshot.
 * @param onTogglePlayPause Called when the play/pause button is tapped.
 * @param onSeek Called with a target position (ms) during scrub or tap.
 */
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
                positionMs = uiState.positionMs,
                durationMs = uiState.durationMs,
                bufferedPositionMs = uiState.bufferedPositionMs,
                onSeek = onSeek,
                modifier = Modifier.weight(1f),
                trackHeight = 4.dp,
                thumbRadius = 7.dp,
                showTimeLabels = true,
            )
        }
    }
}
