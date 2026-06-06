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
 * Overlay shown when the user double-taps the left or right third of the screen
 * to seek backward or forward by 10 seconds.
 *
 * Renders a centred pill with "⏪ -10s" or "+10s ⏩" depending on direction.
 * The caller is responsible for dismissing this composable after the animation
 * period (typically ~600 ms).
 *
 * @param isForward `true` → seeking forward (+10s); `false` → seeking backward (-10s).
 * @param modifier  Applied to the [AnimatedVisibility] wrapper.
 */
@Composable
fun DoubleTapSeekOverlay(
    isForward: Boolean,
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
                text = if (isForward) "+10s ⏩" else "⏪ -10s",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
