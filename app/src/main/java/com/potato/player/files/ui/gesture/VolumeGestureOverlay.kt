package com.potato.player.player.ui.gesture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Overlay shown while the user is swiping up/down on the **right** half of the
 * screen to adjust media volume.
 *
 * The pill is anchored to [Alignment.CenterStart] (left edge) so it sits on the
 * opposite side from where the finger is dragging — keeping the indicator visible
 * and the finger out of the way.
 *
 * @param fraction Normalised volume level in [0f, 1f].
 * @param modifier Applied to the root composable.
 */
@Composable
fun VolumeGestureOverlay(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(150)),
        modifier = modifier,
    ) {
        GestureLevelPill(
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = "Volume",
            fraction = fraction,
            alignment = Alignment.CenterStart, // indicator on LEFT while dragging RIGHT side
        )
    }
}
