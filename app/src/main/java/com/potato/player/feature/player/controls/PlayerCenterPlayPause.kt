package com.potato.player.feature.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerCenterPlayPause(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick  = onClick,
        modifier = PlayerControlsStyles.centerPlayPauseModifier
    ) {
        Icon(
            imageVector        = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint               = Color.White,
            modifier           = Modifier.size(36.dp)
        )
    }
}
