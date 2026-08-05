package com.potato.player.feature.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSeekBar(progress: Float, buffered: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit, modifier: Modifier = Modifier) {
    val sliderColors = SliderDefaults.colors(
        thumbColor           = Color.White,
        activeTrackColor     = Color.White,
        inactiveTrackColor   = Color.Transparent,
        activeTickColor      = Color.Transparent,
        inactiveTickColor    = Color.Transparent
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Background inactive track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White.copy(alpha = 0.2f))
        )

        // Buffer indicator track
        if (buffered > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(maxWidth * buffered)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White.copy(alpha = 0.4f))
            )
        }

        // Slider on top
        val interactionSource = remember { MutableInteractionSource() }
        val isDragged by interactionSource.collectIsDraggedAsState()
        
        Slider(
            value                 = progress.coerceIn(0f, 1f),
            onValueChange         = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            colors                = sliderColors,
            interactionSource     = interactionSource,
            track = { sliderState ->
                SliderDefaults.Track(
                    colors = sliderColors,
                    sliderState = sliderState,
                    modifier = Modifier.height(3.dp)
                )
            },
            thumb                 = {
                Box(
                    modifier = Modifier
                        .size(if (isDragged) 13.dp else 10.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            },
            modifier              = Modifier.fillMaxWidth()
        )
    }
}
