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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {},
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

    val onValueChange = remember {
        { fraction: Float ->
            if (dragFraction < 0f) {
                // First movement of this gesture — notify repository to suppress MPV echo-backs
                onDragStart()
            }
            dragFraction = fraction
            val targetMs = (fraction * durationMs).toLong()
            onSeekGesture(targetMs)
        }
    }

    val onValueChangeFinished = remember {
        {
            val finalFraction = if (dragFraction >= 0f) dragFraction else sliderValue
            val targetMs = (finalFraction.coerceIn(0f, 1f) * durationMs).toLong()
            onSeekCommit(targetMs)
            onDragEnd()
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
                IconButton(onClick = onToggleLock, modifier = buttonModifier) {
                    Icon(
                        imageVector        = if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = if (isLocked) "Unlock" else "Lock",
                        tint               = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onToggleFitMode, modifier = buttonModifier) {
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

                IconButton(onClick = onToggleAutoRotation, modifier = buttonModifier) {
                    Icon(
                        imageVector        = if (isAutoRotation) Icons.Default.ScreenRotation else Icons.Default.ScreenLockLandscape,
                        contentDescription = if (isAutoRotation) "Auto-rotation on" else "Rotation locked",
                        tint               = if (isAutoRotation) Color(0xFF90CAF9) else Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    IconButton(onClick = onEnterPip, modifier = buttonModifier) {
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
