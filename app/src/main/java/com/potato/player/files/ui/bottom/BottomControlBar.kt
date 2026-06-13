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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign

import com.potato.player.player.ui.state.ResizeMode

import com.potato.player.player.ui.PlayerSeekBar
import com.potato.player.player.ui.state.OrientationMode
import com.potato.player.viewmodel.PlayerPositionState
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.potato.player.player.ui.toTimeString

import com.potato.player.player.ui.toTimeString

/**
 * Bottom control bar that assembles the full player transport UI.
 *
 * Layout:
 * ```
 * ┌─────────────────────────────────────────────────────┐
 * │ [RotationLock]    [CenterControls]    [Placeholder] │
 * │ [SeekBar ───────────────────────────────────────────]│
 * └─────────────────────────────────────────────────────┘
 * ```
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
    resizeMode: ResizeMode,
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
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val positionState by positionStateFlow.collectAsState()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RotationLockButton(
                orientationMode = orientationMode,
                onClick = onCycleRotation,
                size = 20.dp,
            )
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onResizeModeClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (resizeMode) {
                        ResizeMode.FIT -> "FIT"
                        ResizeMode.FILL -> "CROP"
                        ResizeMode.STRETCH -> "FILL"
                        ResizeMode.FIXED_WIDTH -> "100%"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        PlayerSeekBar(
            positionMs = positionState.positionMs,
            durationMs = positionState.durationMs,
            bufferedPositionMs = positionState.bufferedPositionMs,
            onSeek = onSeek,
            onSeekFinished = onSeekFinished,
            modifier = Modifier.fillMaxWidth(),
            enableHaptics = enableHaptics,
        )
    }
}
