package com.potato.player.player.ui.gesture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Shared reusable pill composable for level-style gesture indicators.
 *
 * Rendered at [alignment] inside a full-size [Box] so callers can position it
 * at either edge of the screen (e.g. [Alignment.CenterStart] for volume,
 * [Alignment.CenterEnd] for brightness).
 *
 * @param icon               Icon to show above the percentage label.
 * @param contentDescription Accessibility description for the icon.
 * @param fraction           Normalised level in [0f, 1f]; displayed as a percentage.
 * @param alignment          Where inside the full-size host [Box] to anchor the pill.
 * @param modifier           Applied to the outer full-size [Box].
 */
@Composable
internal fun GestureLevelPill(
    icon: ImageVector,
    contentDescription: String,
    fraction: Float,
    alignment: Alignment,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = alignment,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "${(fraction * 100).roundToInt()}%",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
