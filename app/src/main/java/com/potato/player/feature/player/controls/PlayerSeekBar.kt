package com.potato.player.feature.player.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSeekBar(
    progress: Float,
    buffered: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    modifier: Modifier = Modifier
) {

    Slider(
        value = progress.coerceIn(0f, 1f),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        // All color slots are transparent — we own every pixel via the custom
        // track and thumb lambdas below; no M3 defaults should bleed through.
        colors = SliderDefaults.colors(
            thumbColor         = Color.Transparent,
            activeTrackColor   = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
            activeTickColor    = Color.Transparent,
            inactiveTickColor  = Color.Transparent
        ),
        track = { sliderState ->
            // Normalise progress fraction from the slider's own value range so
            // the active segment is always in the exact same coordinate space as
            // the thumb — no BoxWithConstraints offset mismatch possible.
            val range = sliderState.valueRange
            val fraction = if (range.endInclusive > range.start) {
                ((sliderState.value - range.start) /
                        (range.endInclusive - range.start)).coerceIn(0f, 1f)
            } else 0f
            val bufferFraction = buffered.coerceIn(0f, 1f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                val w = size.width
                val h = size.height
                val r = h / 2f

                // 1. Inactive (background) track — full width, dim
                drawRoundRect(
                    color        = Color.White.copy(alpha = 0.2f),
                    size         = Size(w, h),
                    cornerRadius = CornerRadius(r, r)
                )

                // 2. Buffer segment — on top of inactive, brighter
                if (bufferFraction > 0f) {
                    drawRoundRect(
                        color        = Color.White.copy(alpha = 0.4f),
                        size         = Size(w * bufferFraction, h),
                        cornerRadius = CornerRadius(r, r)
                    )
                }

                // 3. Active (played) segment — full white, on top of buffer
                if (fraction > 0f) {
                    drawRoundRect(
                        color        = Color.White,
                        size         = Size(w * fraction, h),
                        cornerRadius = CornerRadius(r, r)
                    )
                }
            }
        },
        thumb = {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        },
        modifier = modifier.fillMaxWidth()
    )
}
