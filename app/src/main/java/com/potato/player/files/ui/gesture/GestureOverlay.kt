package com.potato.player.player.ui.gesture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.player.ui.toTimeString

/**
 * Stateless overlay that routes the active [GestureState] to the appropriate
 * gesture-specific composable.
 *
 * Each gesture type lives in its own dedicated file:
 *  - [VolumeGestureOverlay]     — right-side swipe for media volume
 *  - [BrightnessGestureOverlay] — left-side swipe for screen brightness
 *  - [DoubleTapSeekOverlay]     — double-tap to seek ±10 s
 *  - [LongPressSpeedOverlay]    — long-press hold for 2× speed
 *  - [SeekScrub]                — horizontal swipe to scrub seek position
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

            // Horizontal swipe → seek scrub: show target time centred on screen
            is ActiveGesture.SeekScrub -> {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(100)),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.60f),
                                shape = RoundedCornerShape(50),
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = gesture.targetMs.toTimeString(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
