package com.potato.player.home.components

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.home.FolderGroup
import com.potato.player.data.toFormattedDuration

/**
 * Renders only the folder header row (name, count, duration, chevron).
 * The expanded child rows are rendered as separate keyed items in the
 * parent LazyColumn — this keeps the list flat and enables full recycling.
 */
@Composable
fun FolderCard(
    folderGroup: FolderGroup,
    onToggleExpand: () -> Unit,
    // onFilePicked is no longer used here; kept for API compat if needed
    onFilePicked: (Uri) -> Unit = {}
) {
    val rotation by animateFloatAsState(
        targetValue = if (folderGroup.isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chevronRotation"
    )

    // Total duration of all files in the folder
    val totalDurationMs = folderGroup.files.sumOf { it.durationMs }
    val totalDurationText = if (totalDurationMs > 0) totalDurationMs.toFormattedDuration() else ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp)
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(52.dp)
                .align(Alignment.CenterStart)
                .background(
                    brush = if (folderGroup.isExpanded)
                        Brush.verticalGradient(listOf(Color(0xFF6C63FF), Color(0xFFAA77FF)))
                    else
                        Brush.verticalGradient(listOf(Color(0xFF333340), Color(0xFF333340))),
                    shape = RoundedCornerShape(2.dp)
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (folderGroup.isExpanded) Color(0xFF1C1C26) else Color(0xFF161620),
                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = if (folderGroup.isExpanded) 0.dp else 12.dp)
                )
                .clickable(
                    indication = ripple(color = Color(0xFF6C63FF).copy(alpha = 0.12f)),
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onToggleExpand
                )
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderGroup.folderName,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (folderGroup.isExpanded) Color.White else Color(0xFFCCCCDD)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${folderGroup.files.size} files",
                        fontSize = 11.5.sp,
                        color = Color(0xFF66667A)
                    )
                    if (totalDurationText.isNotEmpty()) {
                        Text(
                            text = "  ·  $totalDurationText",
                            fontSize = 11.5.sp,
                            color = Color(0xFF55556A)
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (folderGroup.isExpanded) "Collapse" else "Expand",
                tint = if (folderGroup.isExpanded) Color(0xFF8A83FF) else Color(0xFF444455),
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotation)
            )
        }
    }
}

/**
 * Bottom container rendered after the expanded items — gives the folder
 * a rounded bottom edge when open.
 */
@Composable
fun FolderExpandedFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                Color(0xFF131318),
                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
            )
            .height(6.dp)
    )
}

/**
 * Divider rendered between expanded folder items (inset to align with text).
 */
@Composable
fun FolderItemDivider() {
    HorizontalDivider(
        color = Color(0xFF1E1E28),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(start = 78.dp) // inset to align past thumbnail
    )
}
