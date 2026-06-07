package com.potato.player.player.ui.bottom

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.potato.player.player.ui.state.OrientationMode

/**
 * Icon button for the rotation-lock control.
 *
 * Cycles between AUTO, LOCKED_LANDSCAPE, and LOCKED_PORTRAIT.
 *
 * @param orientationMode Current orientation mode.
 * @param onClick  Invoked when the user taps the button.
 * @param modifier Optional [Modifier] applied to the icon.
 * @param size     Touch / icon size. Default 36.dp.
 */
@Composable
fun RotationLockButton(
    orientationMode: OrientationMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) PRESSED_SCALE else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rotationLockScale",
    )

    val description = when (orientationMode) {
        OrientationMode.AUTO -> "Auto-rotate"
        OrientationMode.LOCKED_LANDSCAPE -> "Locked landscape"
        OrientationMode.LOCKED_PORTRAIT -> "Locked portrait"
    }

    val icon = when (orientationMode) {
        OrientationMode.AUTO -> Icons.Filled.ScreenRotation
        OrientationMode.LOCKED_LANDSCAPE -> Icons.Filled.StayCurrentLandscape
        OrientationMode.LOCKED_PORTRAIT -> Icons.Filled.StayCurrentPortrait
    }

    val tint = when (orientationMode) {
        OrientationMode.AUTO -> Color.White.copy(alpha = 0.60f)
        else -> Color.White
    }

    Icon(
        imageVector = icon,
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
                role = Role.Button
                contentDescription = description
            },
    )
}

private const val PRESSED_SCALE = 0.82f
