package com.potato.player.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt
import android.app.Activity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import com.potato.player.feature.player.ControlsVisibilityState
import com.potato.player.feature.player.controls.DoubleTapSeekState
import androidx.compose.ui.Alignment
import android.media.AudioManager
import android.content.Context

@Composable
fun PlayerGestureBox(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    controlsState: ControlsVisibilityState,
    onBrightnessChange: (Float) -> Unit,
    fileLoaded: Boolean,
    doubleTapSeekState: DoubleTapSeekState?,
    onDoubleTapSeekState: (DoubleTapSeekState?) -> Unit,
    onSwipeSeekStart: (Double) -> Unit,
    activity: Activity?
) {
    var isLongPressActive by remember { mutableStateOf(false) }
    var brightnessLevel by remember {
        mutableStateOf(
            run {
                val windowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                if (windowBrightness >= 0f) {
                    windowBrightness
                } else {
                    try {
                        android.provider.Settings.System.getInt(
                            activity?.contentResolver,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS
                        ) / 255f
                    } catch (e: Exception) { 0.5f }
                }
            }
        )
    }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    val audioManager = remember {
        activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val maxVolume = remember {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 15f
    }
    var tempVolume by remember {
        mutableStateOf(
            audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 0f
        )
    }
    var currentZoom by remember { mutableStateOf(1.0f) }
    var currentPanX by remember { mutableStateOf(0f) }
    var currentPanY by remember { mutableStateOf(0f) }
    var swipeDragStartSec by remember { mutableStateOf(0.0) }

    LaunchedEffect(fileLoaded) {
        if (fileLoaded) {
            currentZoom = 1.0f
            currentPanX = 0f
            currentPanY = 0f
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                if (activity?.isInPictureInPictureMode == true) return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    currentZoom = (currentZoom * zoom).coerceIn(1.0f, 5.0f)
                    val maxPanX = (currentZoom - 1f) * 0.5f
                    val maxPanY = (currentZoom - 1f) * 0.5f
                    currentPanX = (currentPanX + pan.x / size.width).coerceIn(-maxPanX, maxPanX)
                    currentPanY = (currentPanY + pan.y / size.height).coerceIn(-maxPanY, maxPanY)
                    viewModel.setZoom(currentZoom)
                    viewModel.setPan(currentPanX, currentPanY)
                }
            }
    ) {
        val boxMaxHeight = maxHeight.value
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            brightnessLevel = (brightnessLevel - delta / boxMaxHeight).coerceIn(0.01f, 1.0f)
                            onBrightnessChange(brightnessLevel)
                        }
                    )
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        if (activity?.isInPictureInPictureMode == true) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { _ ->
                                swipeDragStartSec = viewModel.progressState.value.positionSec
                                onSwipeSeekStart(swipeDragStartSec)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val delta = (dragAmount / size.width) * 120.0
                                val target = (swipeDragStartSec + delta).coerceIn(0.0, viewModel.progressState.value.durationSec)
                                swipeDragStartSec += delta
                                viewModel.onSwipeSeek(target)
                            },
                            onDragEnd = {
                                viewModel.onSwipeSeekFinished()
                            },
                            onDragCancel = {
                                viewModel.onSwipeSeekFinished()
                            }
                        )
                    }
                    .pointerInput(activity?.isInPictureInPictureMode == true) {
                        if (activity?.isInPictureInPictureMode == true) return@pointerInput
                        detectTapGestures(
                            onPress = { offset ->
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    if (isLongPressActive) {
                                        isLongPressActive = false
                                        viewModel.stopFastForward()
                                    }
                                }
                            },
                            onLongPress = { offset ->
                                isLongPressActive = true
                                viewModel.startFastForward()
                            },
                            onDoubleTap = { offset ->
                                val current = doubleTapSeekState 
                                val isRight = offset.x > size.width / 2f
                                if (!isRight) {
                                    viewModel.seekExactRelative(-PlayerUiConstants.DOUBLE_TAP_SEEK_SECONDS)
                                    val accum = if (current != null && !current.isForward) {
                                        current.totalSeconds + PlayerUiConstants.DOUBLE_TAP_SEEK_SECONDS
                                    } else PlayerUiConstants.DOUBLE_TAP_SEEK_SECONDS
                                    onDoubleTapSeekState(DoubleTapSeekState(isForward = false, totalSeconds = accum))
                                } else {
                                    viewModel.seekExactRelative(PlayerUiConstants.DOUBLE_TAP_SEEK_SECONDS)
                                    val accum = if (current != null && current.isForward) {
                                        current.totalSeconds + PlayerUiConstants.DOUBLE_TAP_SEEK_SECONDS
                                    } else PlayerUiConstants.DOUBLE_TAP_SEEK_SECONDS
                                    onDoubleTapSeekState(DoubleTapSeekState(isForward = true, totalSeconds = accum))
                                }
                            },
                            onTap = {
                                controlsState.toggle()
                            }
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 15f
                            tempVolume = (tempVolume - delta / boxMaxHeight * maxVolume).coerceIn(0f, maxVolume)
                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, tempVolume.toInt(), 0)
                        }
                    )
            )
        }

        if (!(activity?.isInPictureInPictureMode == true)) {
            VolumeIndicator(
                volume = ((tempVolume / maxVolume) * 100).toInt(),
                visible = showVolumeIndicator,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
            )
            BrightnessIndicator(
                brightness = brightnessLevel,
                visible = showBrightnessIndicator,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
            )
            ZoomIndicator(
                zoom = currentZoom,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 32.dp)
            )
        }
    }
}

@Composable
fun VolumeIndicator(
    volume: Int,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (visible) {
        Box(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Volume: $volume%",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun BrightnessIndicator(
    brightness: Float,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (visible) {
        val percentage = (brightness * 100).roundToInt()
        Box(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Brightness: $percentage%",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ZoomIndicator(
    zoom: Float,
    modifier: Modifier = Modifier
) {
    if (zoom > 1.0f) {
        Box(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%.1fx", zoom),
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}