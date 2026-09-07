package com.potato.player.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun SwipeSeekOverlay(
    targetSec: Double?,
    dragStartSec: Double,
    isPipMode: Boolean
) {
    // ── Swipe Seek Overlay ───────────────────────────────────────────────
    if (targetSec != null && !isPipMode) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                val target = targetSec!!
                val delta = target - dragStartSec
                val sign = if (delta >= 0) "+" else "-"
                
                val hours = (target / 3600).toInt()
                val minutes = ((target % 3600) / 60).toInt()
                val seconds = (target % 60).toInt()
                val timeString = if (hours > 0) {
                    String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
                }
                
                Text(
                    text = timeString,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "[${sign}${abs(delta).toInt()}s]",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
    }
}
