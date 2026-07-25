package com.potato.player.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun VolumeIndicator(
    volume: Int,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (visible) {
        Box(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Volume: $volume%",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun BrightnessIndicator(
    brightness: Float,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (visible) {
        val percentage = (brightness * 100).roundToInt()
        Box(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Brightness: $percentage%",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ZoomIndicator(
    zoom: Float,
    modifier: Modifier = Modifier
) {
    if (zoom > 1.0f) {
        Box(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%.1fx", zoom),
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}
