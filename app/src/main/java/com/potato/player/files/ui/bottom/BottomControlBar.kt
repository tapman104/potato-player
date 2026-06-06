package com.potato.player.player.ui.bottom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import com.potato.player.player.ui.PlayerSeekBar
import com.potato.player.player.viewmodel.PlayerUiState

/**
 * Bottom control bar that assembles the full player transport UI.
 *
 * Layout:
 * ```
 * ┌─────────────────────────────────────────────────────┐
 * │ [SeekBar ───────────────────────────────────────────]│
 * │ [RotationLock]                                      │
 * └─────────────────────────────────────────────────────┘
 * ```
 *
 * This composable is the intended **replacement for [ControlsRow]** at the
 * bottom of [PlayerScreen]. It adds the rotation-lock toggle on the leading
 * edge while preserving the existing seek bar and play/pause layout verbatim.
 *
 * The composable is stateless: all state arrives as parameters and all intents
 * propagate up through lambdas. No ViewModel references are held here.
 *
 * @param uiState          Current player state snapshot. Drives the seek bar
 *                         and play/pause button appearance.
 * @param isRotationLocked `true` when orientation is pinned, `false` for
 *                         auto-rotate. Controls the icon shown by
 *                         [RotationLockButton].
 * @param onSeek           Called with the target position (ms) during scrub
 *                         or tap on the seek bar.
 * @param onToggleRotationLock Called when [RotationLockButton] is tapped.
 * @param modifier         Optional [Modifier] applied to the outer [Column].
 */
@Composable
fun BottomControlBar(
    uiState: PlayerUiState,
    isRotationLocked: Boolean,
    // [CHANGE 1] onTogglePlayPause removed — play/pause no longer lives here
    onSeek: (positionMs: Long) -> Unit,
    onToggleRotationLock: () -> Unit,
    onSeekFinished: (() -> Unit)? = null, // Plumbed from PlayerScreen for rate reset
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // [CHANGE 2] Row 1: seek bar spans full width
        PlayerSeekBar(
            positionMs = uiState.positionMs,
            durationMs = uiState.durationMs,
            bufferedPositionMs = uiState.bufferedPositionMs,
            onSeek = onSeek,
            onSeekFinished = onSeekFinished,
            modifier = Modifier.fillMaxWidth(), // [CHANGE 3]
            trackHeight = 4.dp,
            thumbRadius = 7.dp,
            showTimeLabels = true,
        )

        // [CHANGE 4] Row 2: rotation lock, start-aligned
        Row(
            modifier = Modifier.fillMaxWidth(), // [CHANGE 5]
            horizontalArrangement = Arrangement.Start, // [CHANGE 6]
        ) {
            RotationLockButton( // [CHANGE 7]
                isLocked = isRotationLocked,
                onClick = onToggleRotationLock,
                tint = Color.White,
                size = 20.dp, // Fix 2: smallest practical touch target
            )
        }
    }
}
