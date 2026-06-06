package com.potato.player.player.ui.gesture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Overlay shown while the user is swiping up/down on the **left** half of the
 * screen to adjust screen brightness.
 *
 * The pill is anchored to [Alignment.CenterEnd] (right edge) so it sits on the
 * opposite side from where the finger is dragging — keeping the indicator visible
 * and the finger out of the way.
 *
 * @param fraction Normalised brightness level in [0f, 1f].
 * @param modifier Applied to the root composable.
 */
@Composable
fun BrightnessGestureOverlay(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(150)),
        modifier = modifier,
    ) {
        GestureLevelPill(
            icon = Icons.Outlined.BrightnessHigh,
            contentDescription = "Brightness",
            fraction = fraction,
            alignment = Alignment.CenterEnd, // indicator on RIGHT while dragging LEFT side
        )
    }
}
