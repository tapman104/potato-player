package com.potato.player.player.ui.gesture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stateless overlay that routes the active [GestureState] to the appropriate
 * gesture-specific composable.
 *
 * Each gesture type lives in its own dedicated file:
 *  - [VolumeGestureOverlay]     — right-side swipe for media volume
 *  - [BrightnessGestureOverlay] — left-side swipe for screen brightness
 *  - [DoubleTapSeekOverlay]     — double-tap to seek ±10 s
 *  - [LongPressSpeedOverlay]    — long-press hold for 2× speed
 *
 * Renders nothing when [gestureState.active] is [ActiveGesture.None].
 *
 * @param gestureState Current gesture state from [PlayerGestureHandler.gestureState].
 * @param modifier     Applied to the root [Box].
 */
@Composable
fun GestureOverlay(
    gestureState: GestureState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val gesture = gestureState.active) {
            ActiveGesture.None -> Unit // nothing to render

            ActiveGesture.LongPressSpeed -> {
                LongPressSpeedOverlay(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
                )
            }

            is ActiveGesture.DoubleTapSeek -> {
                DoubleTapSeekOverlay(
                    isForward = gesture.isForward,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
                )
            }

            // Left-side drag → brightness indicator on the RIGHT edge
            is ActiveGesture.BrightnessSwipe -> {
                BrightnessGestureOverlay(
                    fraction = gesture.fraction,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Right-side drag → volume indicator on the LEFT edge
            is ActiveGesture.VolumeSwipe -> {
                VolumeGestureOverlay(
                    fraction = gesture.fraction,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
