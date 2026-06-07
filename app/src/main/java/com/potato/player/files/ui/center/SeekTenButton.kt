package com.potato.player.player.ui.center

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Direction of a fixed-interval seek action.
 *
 * The enum is consumed by [SeekTenButton] to select the correct icon and
 * semantic label, and by the caller to determine whether to add or subtract
 * [SEEK_INTERVAL_MS] from the current position.
 */
enum class SeekDirection {
    /** Skip forward by [SEEK_INTERVAL_MS]. */
    FORWARD,

    /** Skip backward by [SEEK_INTERVAL_MS]. */
    BACKWARD,
}

/** Duration skipped per tap, in milliseconds (10 seconds). */
const val SEEK_INTERVAL_MS = 10_000L

/**
 * A single seek-by-10-second button used for both forward and backward directions.
 *
 * Renders a stacked layout:
 * ```
 * [Replay10 / Forward10 icon]
 * ```
 * Direction-specific semantics are applied so screen readers announce "Skip back 10 seconds" /
 * "Skip forward 10 seconds" correctly.
 *
 * Press-scale animation uses the same [Spring.StiffnessMediumLow] spring as
 * [CenterPlayPauseButton] and [com.potato.player.player.ui.PlayPauseButton] for a
 * consistent tactile feel across all player controls.
 *
 * @param direction  Whether this button skips [SeekDirection.FORWARD] or
 *                   [SeekDirection.BACKWARD].
 * @param onClick    Invoked on tap. The caller is responsible for computing
 *                   the target position (current Â± [SEEK_INTERVAL_MS]).
 * @param modifier   Applied to the outermost [Column].
 * @param iconSize   Size of the seek icon. Defaults to [DEFAULT_ICON_SIZE].
 * @param tint       Icon colour.
 */
@Composable
fun SeekTenButton(
    direction: SeekDirection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = DEFAULT_ICON_SIZE,
    tint: Color = Color.White.copy(alpha = 0.75f),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Same spring spec as CenterPlayPauseButton / PlayPauseButton.
    val scale by animateFloatAsState(
        targetValue = if (isPressed) PRESSED_SCALE else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "seekTenScale_${direction.name}",
    )

    val semanticLabel = when (direction) {
        SeekDirection.BACKWARD -> "Skip back 10 seconds"
        SeekDirection.FORWARD  -> "Skip forward 10 seconds"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = semanticLabel
            },
    ) {
        Icon(
            imageVector = when (direction) {
                SeekDirection.BACKWARD -> Icons.Filled.Replay10
                SeekDirection.FORWARD  -> Icons.Filled.Forward10
            },
            contentDescription = null, // described by column semantics
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

// ---------------------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------------------

private val DEFAULT_ICON_SIZE = 32.dp
private const val PRESSED_SCALE = 0.88f
