package com.potato.player.player.ui.gesture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

/**
 * Overlay shown while the user holds a long-press on the player surface,
 * temporarily boosting playback speed to 2×.
 *
 * Renders a centred pill with "2× Speed" for as long as [LongPressSpeed] is
 * the active gesture. Dismissed automatically when the finger lifts and
 * [PlayerGestureHandler.onLongPressEnd] resets the active gesture to [None].
 *
 * @param modifier Applied to the [AnimatedVisibility] wrapper.
 */
@Composable
fun LongPressSpeedOverlay(
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(150)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.60f),
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "2× Speed",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
