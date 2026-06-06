package com.potato.player.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Play / Pause / Replay button with press-scale feedback.
 *
 * Renders one of three states:
 *  - Loading: indeterminate spinner (non-interactive).
 *  - Playing: Pause icon â€” tap pauses.
 *  - Paused / ended: Play or Replay icon â€” tap plays.
 *
 * @param isPlaying Whether the engine is currently playing.
 * @param isLoading Whether the engine is buffering/preparing.
 * @param isEnded Whether playback has reached the end of the source.
 * @param onClick Invoked on tap (ignored while [isLoading]).
 * @param size Diameter of the circular button. Default 64.dp.
 * @param tint Icon and stroke colour.
 * @param backgroundColor Fill colour of the circle.
 */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    isEnded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    tint: Color = Color.White,
    backgroundColor: Color = Color.White.copy(alpha = 0.15f),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) PRESSED_SCALE else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "playPauseScale",
    )

    val bgColor by animateColorAsState(
        targetValue = if (isPressed) backgroundColor.copy(alpha = 0.30f) else backgroundColor,
        animationSpec = tween(durationMillis = 100),
        label = "playPauseBg",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(bgColor)
            .then(
                if (!isLoading) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).semantics {
                    role = Role.Button
                    contentDescription = when {
                        isEnded -> "Replay"
                        isPlaying -> "Pause"
                        else -> "Play"
                    }
                } else Modifier
            ),
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                color = tint,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(size * SPINNER_SIZE_FRACTION),
            )

            isEnded -> Icon(
                imageVector = Icons.Filled.Replay,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * ICON_SIZE_FRACTION),
            )

            isPlaying -> Icon(
                imageVector = Icons.Filled.Pause,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * ICON_SIZE_FRACTION),
            )

            else -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * ICON_SIZE_FRACTION),
            )
        }
    }
}

private const val ICON_SIZE_FRACTION = 0.56f
private const val SPINNER_SIZE_FRACTION = 0.50f
private const val PRESSED_SCALE = 0.88f
