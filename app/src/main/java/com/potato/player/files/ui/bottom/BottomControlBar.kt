package com.potato.player.player.ui.bottom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text

import com.potato.player.player.ui.PlayerSeekBar
import com.potato.player.player.ui.state.OrientationMode
import com.potato.player.viewmodel.PlayerPositionState
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.potato.player.player.ui.toTimeString

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
 * @param orientationMode  Current orientation mode. Controls the icon and tint shown by
 *                         [RotationLockButton].
 * @param onSeek           Called with the target position (ms) during scrub
 *                         or tap on the seek bar.
 * @param onCycleRotation  Called when [RotationLockButton] is tapped.
 * @param modifier         Optional [Modifier] applied to the outer [Column].
 */
@Composable
fun BottomControlBar(
    positionStateFlow: StateFlow<PlayerPositionState>,
    orientationMode: OrientationMode,
    // [CHANGE 1] onTogglePlayPause removed — play/pause no longer lives here
    onSeek: (positionMs: Long) -> Unit,
    onCycleRotation: () -> Unit,
    onResizeModeClick: () -> Unit,
    onSeekFinished: (() -> Unit)? = null, // Plumbed from PlayerScreen for rate reset
    modifier: Modifier = Modifier,
    enableHaptics: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp), // Added bottom padding to move up
        verticalArrangement = Arrangement.spacedBy(16.dp), // Increased gap between seekbar and bottom buttons
    ) {
        val positionState by positionStateFlow.collectAsState()

        // Row 1: seek bar spans full width
        PlayerSeekBar(
            positionMs = positionState.positionMs,
            durationMs = positionState.durationMs,
            bufferedPositionMs = positionState.bufferedPositionMs,
            onSeek = onSeek,
            onSeekFinished = onSeekFinished,
            modifier = Modifier.fillMaxWidth(),
            enableHaptics = enableHaptics,
            // Using default attractive sizes
        )

        // Row 2: rotation lock and placeholder
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RotationLockButton(
                orientationMode = orientationMode,
                onClick = onCycleRotation,
                size = 20.dp,
            )
            Spacer(modifier = Modifier)
        }
    }
}
