package com.potato.player.feature.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import com.potato.player.util.TimeFormatter

import com.potato.player.feature.player.PlaybackProgressState
import com.potato.player.feature.player.VideoFitMode

@Composable
fun PlayerBottomControls(
    progressState: PlaybackProgressState,
    isAutoRotation: Boolean = false,
    currentFitMode: VideoFitMode = VideoFitMode.FIT,
    onSeekGesture: (Long) -> Unit,    // called continuously during drag
    onSeekCommit: (Long) -> Unit,     // called once on finger lift
    onDragStart: () -> Unit,          // tells repository to suppress echo-backs
    onDragEnd: () -> Unit,            // tells repository to re-enable echo-backs
    onToggleAutoRotation: () -> Unit = {},
    onToggleFitMode: () -> Unit = {},
    onEnterPip: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val currentPositionMs = (progressState.positionSec * 1000.0).toLong()
    val durationMs = (progressState.durationSec * 1000.0).toLong()
    val cachedPositionMs = (progressState.cachedSec * 1000.0).toLong()
    val bufferDurationMs = (progressState.cacheDurationSec * 1000.0).toLong()

    // dragFraction: -1f means "not dragging"; any >= 0f means actively scrubbing
    var dragFraction by remember { mutableFloatStateOf(-1f) }

    var lastDragFraction by remember { mutableFloatStateOf(0f) }
    if (dragFraction >= 0f) {
        lastDragFraction = dragFraction
    }

    val sliderValue = if (durationMs > 0L) currentPositionMs.toFloat() / durationMs else 0f
    val displayFraction = if (dragFraction >= 0f) dragFraction else sliderValue

    val cachedAheadMs = if (cachedPositionMs > 0L) cachedPositionMs else bufferDurationMs
    val bufferEndMs = currentPositionMs + cachedAheadMs
    val bufferFraction = if (durationMs > 0L) (bufferEndMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val durationString = remember(durationMs) { TimeFormatter.formatMs(durationMs) }

    // rememberUpdatedState: lambdas captured in remember{} always see the latest values
    val onSeekGestureRef  = rememberUpdatedState(onSeekGesture)
    val onSeekCommitRef   = rememberUpdatedState(onSeekCommit)
    val onDragStartRef    = rememberUpdatedState(onDragStart)
    val onDragEndRef      = rememberUpdatedState(onDragEnd)
    val durationMsRef     = rememberUpdatedState(durationMs)
    val sliderValueRef    = rememberUpdatedState(sliderValue)

    val onValueChange = remember {
        { fraction: Float ->
            if (dragFraction < 0f) {
                // First movement of this gesture — notify repository to suppress MPV echo-backs
                onDragStartRef.value()
            }
            dragFraction = fraction
            val targetMs = (fraction * durationMsRef.value).toLong()
            onSeekGestureRef.value(targetMs)
        }
    }

    val onValueChangeFinished = remember {
        {
            val finalFraction = if (dragFraction >= 0f) dragFraction else sliderValueRef.value
            val targetMs = (finalFraction.coerceIn(0f, 1f) * durationMsRef.value).toLong()
            onSeekCommitRef.value(targetMs)
            onDragEndRef.value()
            dragFraction = -1f
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(horizontal = 12.dp)
    ) {
        // Play/Pause centered, and Auto-Rotation + PiP right-aligned above the seek area
        val onToggleAutoRotationRef = rememberUpdatedState(onToggleAutoRotation)
        val onEnterPipRef           = rememberUpdatedState(onEnterPip)
        val handleToggleAutoRotation = remember { { onToggleAutoRotationRef.value() } }
        val handleEnterPip           = remember { { onEnterPipRef.value() } }
        val buttonModifier = PlayerControlsStyles.iconButtonModifier

        // Floating Live Time Preview Bubble while scrubbing
        AnimatedVisibility(
            visible = dragFraction >= 0f,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            SeekPreviewBubble(
                durationMs = durationMs,
                displayFraction = lastDragFraction
            )
        }

        // Combined time and seek bar row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text  = TimeFormatter.formatMs(
                    if (dragFraction >= 0f) (dragFraction * durationMs).toLong()
                    else currentPositionMs
                ),
                color    = Color.White,
                fontSize = 13.sp
            )
            
            PlayerSeekBar(
                progress              = displayFraction,
                buffered              = bufferFraction,
                onValueChange         = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                modifier              = Modifier.weight(1f).padding(horizontal = 8.dp)
            )

            Text(
                text     = durationString,
                color    = Color.White,
                fontSize = 13.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            // Auto-Rotation + PiP — bottom-right corner
            Row(
                modifier              = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.End,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                val onToggleFitModeRef = rememberUpdatedState(onToggleFitMode)
                val handleToggleFitMode = remember { { onToggleFitModeRef.value() } }

                IconButton(onClick = handleToggleFitMode, modifier = buttonModifier) {
                    val icon = when (currentFitMode) {
                        VideoFitMode.FIT -> Icons.Default.FitScreen
                        VideoFitMode.FILL -> Icons.Default.Fullscreen
                        VideoFitMode.STRETCH -> Icons.Default.AspectRatio
                    }
                    Icon(
                        imageVector        = icon,
                        contentDescription = "Video Fit Mode",
                        tint               = if (currentFitMode != VideoFitMode.FIT) Color(0xFF90CAF9) else Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = handleToggleAutoRotation, modifier = buttonModifier) {
                    Icon(
                        imageVector        = if (isAutoRotation) Icons.Default.ScreenRotation else Icons.Default.ScreenLockLandscape,
                        contentDescription = if (isAutoRotation) "Auto-rotation on" else "Rotation locked",
                        tint               = if (isAutoRotation) Color(0xFF90CAF9) else Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    IconButton(onClick = handleEnterPip, modifier = buttonModifier) {
                        Icon(
                            imageVector        = Icons.Default.PictureInPicture,
                            contentDescription = "Picture-in-Picture",
                            tint               = Color.White
                        )
                    }
                }
            }
        }
    }
}
