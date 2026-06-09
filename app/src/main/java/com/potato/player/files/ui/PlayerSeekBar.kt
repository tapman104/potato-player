package com.potato.player.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 8.dp,
    trackColor: Color = Color.White.copy(alpha = 0.2f),
    bufferColor: Color = Color.White.copy(alpha = 0.4f),
    progressColor: Color = MaterialTheme.colorScheme.primary,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    showTimeLabels: Boolean = true,
) {
    // Raw fractional progress [0f, 1f]. Unknown duration -> 0.
    val playedFraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val bufferedFraction = if (durationMs > 0L) {
        (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val haptic = LocalHapticFeedback.current

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

    // Premium visual expansion when dragging
    val animatedTrackHeight by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isDragging) trackHeight * 2f else trackHeight,
        animationSpec = tween(durationMillis = 200),
        label = "trackHeightAnim"
    )
    val animatedThumbRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isDragging) thumbRadius * 1.8f else thumbRadius,
        animationSpec = tween(durationMillis = 200),
        label = "thumbRadiusAnim"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val displayPosition = if (isDragging) {
            (scrubFraction * durationMs).roundToLong()
        } else positionMs

        if (showTimeLabels && durationMs > 0L) {
            Text(
                text = displayPosition.toTimeString(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, end = 12.dp)
            )
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(48.dp) // Minimum recommended touch target size
                .semantics {
                    contentDescription = "Seek bar, position ${positionMs.toTimeString()} of ${durationMs.toTimeString()}"
                }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs <= 0L) return@detectTapGestures
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val baseThumbRadiusPx = thumbRadius.toPx()
                        val trackWidth = size.width - 2 * baseThumbRadiusPx
                        val fraction = ((offset.x - baseThumbRadiusPx) / trackWidth).coerceIn(0f, 1f)
                        val targetMs = (fraction * durationMs).roundToLong()
                        onSeek(targetMs)
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val baseThumbRadiusPx = thumbRadius.toPx()
                            val trackWidth = size.width - 2 * baseThumbRadiusPx
                            scrubFraction = ((offset.x - baseThumbRadiusPx) / trackWidth).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                            val baseThumbRadiusPx = thumbRadius.toPx()
                            val trackWidth = size.width - 2 * baseThumbRadiusPx
                            val fraction = ((change.position.x - baseThumbRadiusPx) / trackWidth).coerceIn(0f, 1f)
                            scrubFraction = fraction
                            if (durationMs > 0L) {
                                onSeek((fraction * durationMs).roundToLong())
                            }
                        },
                    )
                },
        ) {
            val trackHeightPx = animatedTrackHeight.toPx()
            val baseThumbRadiusPx = thumbRadius.toPx()
            val thumbRadiusPx = animatedThumbRadius.toPx()
            
            val centerY = size.height / 2f
            val trackStartX = baseThumbRadiusPx
            val trackEndX = size.width - baseThumbRadiusPx
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
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF00E5FF), Color(0xFFD500F9)),
                        startX = trackStartX,
                        endX = trackStartX + trackWidth
                    ),
                    topLeft = Offset(trackStartX, centerY - trackHeightPx / 2f),
                    size = Size(played, trackHeightPx),
                    cornerRadius = cornerRadius,
                )
            }

            // 4. Thumb — slightly larger while dragging for tactile feedback
            if (isDragging) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = thumbRadiusPx * 2.5f,
                    center = Offset(trackStartX + played, centerY),
                )
            }
            
            // Drop shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.4f),
                radius = thumbRadiusPx * 1.3f,
                center = Offset(trackStartX + played, centerY),
            )
            
            // Vibrant thumb color matching the end of the gradient
            drawCircle(
                color = Color(0xFFD500F9),
                radius = thumbRadiusPx,
                center = Offset(trackStartX + played, centerY),
            )
            
            // Subtle white border
            drawCircle(
                color = Color.White,
                radius = thumbRadiusPx,
                center = Offset(trackStartX + played, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
        }

        if (showTimeLabels && durationMs > 0L) {
            Text(
                text = durationMs.toTimeString(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp, end = 8.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------

/**
 * Format milliseconds as `m:ss` or `h:mm:ss`.
 */
internal fun Long.toTimeString(): String {
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
