package com.potato.player.player.ui.center

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Horizontal row of center-screen playback controls.
 *
 * Layout (left â†’ right):
 * ```
 * [SeekTen(BACKWARD)]   [CenterPlayPause]   [SeekTen(FORWARD)]
 * ```
 * All three children are vertically centered. [CenterPlayPauseButton] is the
 * dominant touch target; the seek buttons flank it with equal horizontal
 * spacing defined by [BUTTON_SPACING].
 *
 * This is the composable [PlayerScreen] imports â€” individual pieces inside the
 * `center` package are not referenced directly from the screen layer.
 *
 * The row is stateless. Callers provide the full playback state and wire up
 * the three intent callbacks:
 *
 * ```kotlin
 * CenterControlsRow(
 *     isPlaying  = uiState.isPlaying,
 *     isLoading  = uiState.isLoading,
 *     isEnded    = uiState.isEnded,
 *     onPlayPauseClick  = viewModel::togglePlayPause,
 *     onSeekBackward    = { viewModel.seekTo(uiState.positionMs - SEEK_INTERVAL_MS) },
 *     onSeekForward     = { viewModel.seekTo(uiState.positionMs + SEEK_INTERVAL_MS) },
 * )
 * ```
 *
 * @param isPlaying         Forwarded to [CenterPlayPauseButton].
 * @param isLoading         Forwarded to [CenterPlayPauseButton].
 * @param isEnded           Forwarded to [CenterPlayPauseButton].
 * @param onPlayPauseClick  Invoked when the center button is tapped.
 * @param onSeekBackward    Invoked when the backward seek button is tapped.
 * @param onSeekForward     Invoked when the forward seek button is tapped.
 * @param modifier          Applied to the outer [Row]. Typically used by
 *                          [PlayerScreen] to center the row in the overlay.
 * @param tint              Colour forwarded to all three children.
 */
@Composable
fun CenterControlsRow(
    isPlaying: Boolean,
    isLoading: Boolean,
    isEnded: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BUTTON_SPACING),
        modifier = modifier,
    ) {
        SeekTenButton(
            direction = SeekDirection.BACKWARD,
            onClick = onSeekBackward,
            tint = tint,
        )

        CenterPlayPauseButton(
            isPlaying = isPlaying,
            isLoading = isLoading,
            isEnded = isEnded,
            onClick = onPlayPauseClick,
            tint = tint,
        )

        SeekTenButton(
            direction = SeekDirection.FORWARD,
            onClick = onSeekForward,
            tint = tint,
        )
    }
}

// ---------------------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------------------

/** Horizontal gap between each button pair. */
private val BUTTON_SPACING = 32.dp
