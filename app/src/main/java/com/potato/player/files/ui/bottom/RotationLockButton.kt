package com.potato.player.player.ui.bottom

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icon toggle button for the rotation-lock control.
 *
 * When [isLocked] is `true` a filled **Lock** icon is shown, signalling that
 * the screen orientation is pinned. When `false` a **ScreenRotation** icon is
 * shown, signalling that auto-rotate is active.
 *
 * The button applies a spring-animated press-scale identical to the one used
 * by [com.potato.player.player.ui.PlayPauseButton] so the two feel consistent when
 * placed in the same control bar.
 *
 * This composable is stateless: the caller owns [isLocked] and must update it
 * in response to [onClick].
 *
 * @param isLocked `true` when rotation is locked; `false` for auto-rotate.
 * @param onClick  Invoked when the user taps the button.
 * @param modifier Optional [Modifier] applied to the icon.
 * @param size     Touch / icon size. Default 36.dp.
 * @param tint     Icon tint. Default [Color.White].
 */
@Composable
fun RotationLockButton(
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = Color.White,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) PRESSED_SCALE else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rotationLockScale",
    )

    val description = if (isLocked) "Rotation locked â€” tap to unlock" else "Auto-rotate â€” tap to lock"

    Icon(
        imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.ScreenRotation,
        contentDescription = null, // declared in semantics below
        tint = tint,
        modifier = modifier
            .size(size)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                role = Role.Switch
                contentDescription = description
                toggleableState = if (isLocked) ToggleableState.On else ToggleableState.Off
            },
    )
}

private const val PRESSED_SCALE = 0.82f
