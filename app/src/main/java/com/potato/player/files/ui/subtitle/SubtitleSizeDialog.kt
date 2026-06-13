package com.potato.player.player.ui.subtitle

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.ui.SubtitleView

/**
 * A Material3 [AlertDialog] that allows the user to adjust subtitle rendering
 * settings — size and vertical position.
 *
 * Changes are staged in a local [draft] state and only committed when the user
 * taps **Apply**. Cancelling or dismissing the dialog discards the draft without
 * affecting the live [settings].
 *
 * @param settings  The current [SubtitleSettings] shown as the initial slider values.
 * @param onConfirm Called with the updated [SubtitleSettings] when the user taps "Apply".
 * @param onDismiss Called when the user taps "Cancel" or outside the dialog.
 */
@Composable
fun SubtitleSizeDialog(
    settings: SubtitleSettings,
    onConfirm: (SubtitleSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(settings) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(16.dp),
        title = {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = "Subtitle Appearance",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            }
        },
        text = {
            androidx.compose.foundation.layout.Column {
                // ── Row 1: Size ───────────────────────────────────────────────────
                Text(
                    text = "Size",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Size",
                        modifier = Modifier.width(72.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = draft.sizeFraction / SubtitleView.DEFAULT_TEXT_SIZE_FRACTION,
                        onValueChange = { multiplier ->
                            draft = draft.copy(
                                sizeFraction = multiplier * SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
                            )
                        },
                        valueRange = 0.5f..1.8f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "%.1f×".format(
                            draft.sizeFraction / SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
                        ),
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // ── Row 2: Position ───────────────────────────────────────────────
                Text(
                    text = "Position  ↑ higher = further from bottom",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Position",
                        modifier = Modifier.width(72.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = draft.bottomPaddingFraction,
                        onValueChange = { padding ->
                            draft = draft.copy(bottomPaddingFraction = padding)
                        },
                        valueRange = 0.02f..0.40f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "%.2f".format(draft.bottomPaddingFraction),
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFAAAAAA))
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .background(Color(0xFF7B2FBE), RoundedCornerShape(8.dp))
                    .clickable { onConfirm(draft) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Apply", color = Color.White)
            }
        },
    )
}
