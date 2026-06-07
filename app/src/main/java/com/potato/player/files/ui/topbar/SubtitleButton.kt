package com.potato.player.player.ui.topbar

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClosedCaption
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
 * Top-bar icon button that signals intent to open the subtitle track picker.
 *
 * Like [AudioTrackButton], this composable is logic-free. Dialog state lives
 * in the caller:
 * ```kotlin
 * var showSubtitleDialog by remember { mutableStateOf(false) }
 *
 * SubtitleButton(
 *     isSubtitleActive = currentSubtitleTrack != null,
 *     onClick = { showSubtitleDialog = true },
 * )
 *
 * if (showSubtitleDialog) {
 *     SubtitleDialog(onDismiss = { showSubtitleDialog = false }, â€¦)
 * }
 * ```
 *
 * ### Active-track indicator
 * When [isSubtitleActive] is `true`, the button background transitions to a
 * soft white pill, making the active state immediately legible against the
 * dark video surface without relying on colour alone.
 *
 * ### Animation
 * - Press scale: [Spring.StiffnessMediumLow] (matches all other player controls).
 * - Background: fast [tween] (150 ms) between transparent and active-pill colour.
 *
 * @param isSubtitleActive `true` when a subtitle track is currently selected and
 *                          rendering on screen. Drives the active-state indicator.
 * @param onClick           Invoked on tap. Should set `showSubtitleDialog = true`.
 * @param onLongPress       Optional. Invoked on long-press. Callers can use this
 *                          to open the subtitle appearance dialog directly from
 *                          the button without relying on a global gesture heuristic.
 * @param modifier          Applied to the touch-target [Box].
 * @param size              Icon size. Touch target is always [TOUCH_TARGET_SIZE].
 * @param tint              Icon colour. Applied in both active and inactive states.
 */
@Composable
fun SubtitleButton(
    isSubtitleActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_ICON_SIZE,
    tint: Color = Color.White,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Scale spring â€” same spec as every other player button.
    val scale by animateFloatAsState(
        targetValue = if (isPressed) PRESSED_SCALE else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "subtitleButtonScale",
    )

    val iconTint by animateColorAsState(
        targetValue = if (isSubtitleActive) Color(0xFF00D4FF) else tint,
        animationSpec = tween(durationMillis = 150),
        label = "subtitleButtonTint",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(TOUCH_TARGET_SIZE)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = if (isSubtitleActive) {
                    "Subtitles on — tap to change track"
                } else {
                    "Select subtitle track"
                }
            },
    ) {
        Icon(
            imageVector = Icons.Outlined.ClosedCaption,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size),
        )
    }
}

// ---------------------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------------------

private val TOUCH_TARGET_SIZE   = 48.dp
private val DEFAULT_ICON_SIZE   = 24.dp
private const val PRESSED_SCALE = 0.88f
