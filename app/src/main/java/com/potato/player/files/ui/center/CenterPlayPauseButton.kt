package com.potato.player.player.ui.center

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
 * Large circular play / pause / replay button intended for center-screen placement.
 *
 * Mirrors the visual and interaction contract of [com.potato.player.player.ui.PlayPauseButton]
 * but rendered at a larger default size to serve as the primary touch target when the
 * controls overlay is shown over the video surface.
 *
 * Animation pattern (identical to PlayPauseButton):
 *  - Press scale: [Spring.StiffnessMediumLow] spring, scales down to [PRESSED_SCALE].
 *  - Background tint: fast [tween] (100 ms) between rest and pressed alpha.
 *
 * Loading state renders a [CircularProgressIndicator] and suppresses click events.
 *
 * @param isPlaying  True while the engine is actively rendering frames.
 * @param isLoading  True while the source is buffering or preparing.
 * @param isEnded    True when playback has reached end-of-file.
 * @param onClick    Invoked on tap; ignored while [isLoading] is true.
 * @param modifier   Applied to the outermost [Box].
 * @param size       Diameter of the circular button. Defaults to [DEFAULT_SIZE].
 * @param tint       Icon and spinner stroke colour.
 * @param backgroundColor Rest-state fill colour of the circle.
 */
@Composable
fun CenterPlayPauseButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    isEnded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_SIZE,
    tint: Color = Color.White,
    backgroundColor: Color = Color.White.copy(alpha = 0.18f),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Scale spring â€” same stiffness as PlayPauseButton for a consistent feel.
    val scale by animateFloatAsState(
        targetValue = if (isPressed) PRESSED_SCALE else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "centerPlayPauseScale",
    )

    // Background brightens slightly on press.
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) backgroundColor.copy(alpha = 0.35f) else backgroundColor,
        animationSpec = tween(durationMillis = 100),
        label = "centerPlayPauseBg",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(bgColor)
            .then(
                if (!isLoading) Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                    .semantics {
                        role = Role.Button
                        contentDescription = when {
                            isEnded   -> "Replay"
                            isPlaying -> "Pause"
                            else      -> "Play"
                        }
                    }
                else Modifier
            ),
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                color = tint,
                strokeWidth = 3.dp,
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

// ---------------------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------------------

/** Default diameter. Larger than [com.potato.player.player.ui.PlayPauseButton]'s 64.dp default. */
private val DEFAULT_SIZE = 88.dp

/** Icon occupies 56 % of the button diameter â€” keeps padding consistent with PlayPauseButton. */
private const val ICON_SIZE_FRACTION = 0.56f

/** Spinner slightly smaller than icon to avoid clipping within the circle. */
private const val SPINNER_SIZE_FRACTION = 0.50f

/** Scale factor applied on press. Spring animation returns to 1f on release. */
private const val PRESSED_SCALE = 0.88f
