package com.potato.player.player.ui.topbar

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
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
 * Top-bar icon button that signals intent to open the audio track picker.
 *
 * This composable is deliberately logic-free. It owns no dialog state.
 * The caller (or a parent hoisting state) is responsible for setting
 * `showAudioDialog = true` inside [onClick] and rendering the dialog
 * conditionally.
 *
 * Example usage:
 * ```kotlin
 * var showAudioDialog by remember { mutableStateOf(false) }
 *
 * AudioTrackButton(onClick = { showAudioDialog = true })
 *
 * if (showAudioDialog) {
 *     AudioTrackDialog(onDismiss = { showAudioDialog = false }, â€¦)
 * }
 * ```
 *
 * Press-scale animation uses [Spring.StiffnessMediumLow] â€” identical to
 * every other interactive control in the player for a consistent feel.
 *
 * @param onClick  Invoked on tap. Should set `showAudioDialog = true` in the caller.
 * @param modifier Applied to the touch-target [Box].
 * @param size     Icon size. Touch target is always [TOUCH_TARGET_SIZE].
 * @param tint     Icon colour.
 */
@Composable
fun AudioTrackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_ICON_SIZE,
    tint: Color = Color.White,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) PRESSED_SCALE else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "audioTrackButtonScale",
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
                contentDescription = "Select audio track"
            },
    ) {
        Icon(
            imageVector = Icons.Outlined.Audiotrack,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

// ---------------------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------------------

private val TOUCH_TARGET_SIZE = 48.dp
private val DEFAULT_ICON_SIZE = 24.dp
private const val PRESSED_SCALE = 0.88f
