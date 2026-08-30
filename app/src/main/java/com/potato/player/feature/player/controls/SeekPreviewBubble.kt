package com.potato.player.feature.player.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.util.TimeFormatter

private val BUBBLE_WIDTH_DP = 64.dp

@Composable
fun SeekPreviewBubble(durationMs: Long, displayFraction: Float, modifier: Modifier = Modifier) {
    val fraction = displayFraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            border = BorderStroke(1.dp, Color(0xFF90CAF9)),
            modifier = Modifier
                .height(36.dp)
                .layout { measurable, constraints ->
                    val bubbleWidthPx = BUBBLE_WIDTH_DP.toPx().toInt()
                    val containerWidth = constraints.maxWidth
                    val maxOffsetPx = (containerWidth - bubbleWidthPx).coerceAtLeast(0)
                    val rawOffsetPx = (containerWidth * fraction - bubbleWidthPx / 2f).toInt()
                    val clampedOffsetPx = rawOffsetPx.coerceIn(0, maxOffsetPx)

                    val placeable = measurable.measure(
                        Constraints.fixed(bubbleWidthPx, constraints.maxHeight)
                    )
                    layout(containerWidth, constraints.maxHeight) {
                        placeable.placeRelative(clampedOffsetPx, 0)
                    }
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = TimeFormatter.formatMs((fraction * durationMs).toLong()),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
