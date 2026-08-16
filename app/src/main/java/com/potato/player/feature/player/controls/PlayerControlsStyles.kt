package com.potato.player.feature.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object PlayerControlsStyles {

    val iconButtonModifier: Modifier = Modifier
        .size(40.dp)
        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)

    val centerPlayPauseModifier: Modifier = Modifier
        .size(64.dp)
        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)

    val dialogIconButtonModifier: Modifier = Modifier
        .size(40.dp)
        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)

    val accentColor = Color(0xFF90CAF9)

}
