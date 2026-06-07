package com.potato.player.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontVariation.weight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong

/**
 * Seek bar with three visual layers:
 *  1. Background track (dim).
 *  2. Buffered-progress track (mid-tone).
 *  3. Playback-position track + thumb (accent).
 *
 * Handles both tap-to-seek and drag-to-scrub. While the user is
 * dragging, the displayed position follows the finger immediately;
 * [onSeek] is called continuously so the engine can buffer ahead.
 * When the drag ends, [onSeekFinished] is called (optional) for any
 * final clean-up the caller needs (e.g. re-enabling position updates).
 *
 * @param positionMs Current playback position in milliseconds.
 * @param durationMs Total duration in milliseconds. 0 means unknown.
 * @param bufferedPositionMs Furthest buffered position in milliseconds.
 * @param onSeek Called with the target position (ms) during scrub and tap.
 * @param onSeekFinished Called once when a drag gesture ends.
 * @param trackHeight Height of the progress track. Default 4.dp.
 * @param thumbRadius Radius of the scrub thumb. Default 6.dp.
 * @param trackColor Background track colour.
 * @param bufferColor Buffered-progress colour.
 * @param progressColor Played-position colour.
 * @param thumbColor Thumb colour.
 * @param showTimeLabels Whether to render position/duration labels below the track.
 */
@Composable
fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    onSeekFinished: (() -> Unit)? = null,
    trackHeight: Dp = 2.dp,
    thumbRadius: Dp = 5.dp,
    trackColor: Color = Color.White.copy(alpha = 0.15f),
    bufferColor: Color = Color.White.copy(alpha = 0.28f),
    progressColor: Color = Color.White,
    thumbColor: Color = Color.White,
    showTimeLabels: Boolean = true,
) {
    // Raw fractional progress [0f, 1f]. Unknown duration â†’ 0.
    val playedFraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val bufferedFraction = if (durationMs > 0L) {
        (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // While dragging, override the displayed fraction with the finger position.
    var isDragging by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val displayFraction = if (isDragging) scrubFraction else playedFraction

    // Smoothly animate the playback head when NOT scrubbing.
    val animatedFraction by animateFloatAsState(
        targetValue = displayFraction,
        animationSpec = if (isDragging) tween(durationMillis = 0) else tween(durationMillis = 200),
        label = "seekBarProgress",
    )

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbRadius * 2 + trackHeight)
                .semantics {
                    contentDescription = "Seek bar, position ${positionMs.toTimeString()} of ${durationMs.toTimeString()}"
                }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs <= 0L) return@detectTapGestures
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val targetMs = (fraction * durationMs).roundToLong()
                        onSeek(targetMs)
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            if (durationMs > 0L) {
                                onSeek((scrubFraction * durationMs).roundToLong())
                            }
                            onSeekFinished?.invoke()
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            scrubFraction = fraction
                            if (durationMs > 0L) {
                                onSeek((fraction * durationMs).roundToLong())
                            }
                        },
                    )
                },
        ) {
            val trackHeightPx = trackHeight.toPx()
            val thumbRadiusPx = thumbRadius.toPx()
            val centerY = size.height / 2f
            val trackStartX = thumbRadiusPx
            val trackEndX = size.width - thumbRadiusPx
            val trackWidth = trackEndX - trackStartX
            val cornerRadius = CornerRadius(trackHeightPx / 2f)

            // 1. Background track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(trackStartX, centerY - trackHeightPx / 2f),
                size = Size(trackWidth, trackHeightPx),
                cornerRadius = cornerRadius,
            )

            // 2. Buffered track
            if (bufferedFraction > 0f) {
                drawRoundRect(
                    color = bufferColor,
                    topLeft = Offset(trackStartX, centerY - trackHeightPx / 2f),
                    size = Size(trackWidth * bufferedFraction, trackHeightPx),
                    cornerRadius = cornerRadius,
                )
            }

            // 3. Played track
            val played = trackWidth * animatedFraction
            if (played > 0f) {
                drawRoundRect(
                    color = progressColor,
                    topLeft = Offset(trackStartX, centerY - trackHeightPx / 2f),
                    size = Size(played, trackHeightPx),
                    cornerRadius = cornerRadius,
                )
            }

            // 4. Thumb â€” slightly larger while dragging for tactile feedback
            if (isDragging) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = thumbRadiusPx * 2.2f,
                    center = Offset(trackStartX + played, centerY),
                )
            }
            val currentThumbRadius = if (isDragging) thumbRadiusPx * 1.5f else thumbRadiusPx
            drawCircle(
                color = thumbColor,
                radius = currentThumbRadius,
                center = Offset(trackStartX + played, centerY),
            )
        }

        if (showTimeLabels && durationMs > 0L) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = thumbRadius),
            ) {
                val displayPosition = if (isDragging) {
                    (scrubFraction * durationMs).roundToLong()
                } else positionMs

                Text(
                    text = displayPosition.toTimeString(),
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = durationMs.toTimeString(),
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------

/**
 * Format milliseconds as `m:ss` or `h:mm:ss`.
 */
private fun Long.toTimeString(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
