package com.potato.player.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.geometry.Offset
import com.potato.player.feature.player.ControlsVisibilityState
import com.potato.player.feature.player.controls.DoubleTapSeekState
import androidx.compose.ui.Alignment

@Composable
fun PlayerGestureBox(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    controlsState: ControlsVisibilityState,
    onBrightnessChange: (Float) -> Unit,
    fileLoaded: Boolean,
    doubleTapSeekState: DoubleTapSeekState?,
    onDoubleTapSeekState: (DoubleTapSeekState?) -> Unit,
    activity: Activity?
) {
    var isLongPressActive by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var tempVolume by remember { mutableStateOf(100f) }
    var currentZoom by remember { mutableStateOf(1.0f) }
    var currentPanX by remember { mutableStateOf(0f) }
    var currentPanY by remember { mutableStateOf(0f) }

    LaunchedEffect(fileLoaded) {
        if (fileLoaded) {
            currentZoom = 1.0f
            currentPanX = 0f
            currentPanY = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                        if (currentZoom > 1.0f) {
                            viewModel.resetZoom()
                            currentZoom = 1.0f
                            currentPanX = 0f
                            currentPanY = 0f
                            return@detectTapGestures
                        }
                        val current = doubleTapSeekState 
                        val screenWidth = size.width
                        if (offset.x < screenWidth / 2f) {
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
            .pointerInput(Unit) {
                if (activity?.isInPictureInPictureMode == true) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    val screenW = size.width.toFloat()
                    val screenH = size.height.toFloat()
                    
                    val edgeDeadZone = screenW * 0.08f
                    val topDeadZone = screenH * 0.10f
                    val bottomDeadZone = screenH * 0.10f
                    if (startX < edgeDeadZone || startX > screenW - edgeDeadZone) {
                        return@awaitEachGesture
                    }
                    if (startY < topDeadZone || startY > screenH - bottomDeadZone) {
                        return@awaitEachGesture
                    }
                    
                    var totalDragY = 0f
                    var totalDragX = 0f
                    val minDragThreshold = 20f
                    var gestureStarted = false
                    val isRightSide = startX > screenW / 2f
                    
                    var pointerId = down.id
                    
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        if (change == null || !change.pressed) break

                        totalDragY += change.positionChange().y
                        totalDragX += change.positionChange().x
                        
                        if (!gestureStarted && 
                            kotlin.math.abs(totalDragY) > minDragThreshold &&
                            kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX) * 1.5f) {
                            gestureStarted = true
                            if (isRightSide) {
                                showVolumeIndicator = true
                                tempVolume = viewModel.uiState.value.volume.toFloat()
                            } else {
                                showBrightnessIndicator = true
                            }
                        }
                        
                        if (gestureStarted) {
                            val delta = change.positionChange().y
                            change.consume()
                            if (isRightSide) {
                                tempVolume += -(delta / screenH) * 150f
                                viewModel.setVolume(tempVolume.toInt())
                            } else {
                                val brightnessDelta = -(delta / screenH) * 1.0f
                                brightnessLevel = (brightnessLevel + brightnessDelta).coerceIn(0.01f, 1.0f)
                                onBrightnessChange(brightnessLevel)
                            }
                        }
                    } while (true)

                    
                    showVolumeIndicator = false
                    showBrightnessIndicator = false
                }
            }
            .pointerInput(Unit) {
                if (activity?.isInPictureInPictureMode == true) return@pointerInput
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                        if (event.changes.size >= 2 && !event.changes.any { it.isConsumed }) break
                    } while (event.changes.any { it.pressed })

                    
                    var localZoom = currentZoom
                    var localPanX = currentPanX
                    var localPanY = currentPanY
                    
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        
                        event.changes.forEach {
                            if (it.positionChange() != Offset.Zero) {
                                it.consume()
                            }
                        }

                        localZoom = (localZoom * zoomChange).coerceIn(1.0f, 4.0f)
                        if (localZoom < 1.1f) {
                            localZoom = 1.0f
                            localPanX = 0f
                            localPanY = 0f
                        }
                        currentZoom = localZoom
                        if (localZoom > 1.0f) {
                            val screenWidth = size.width.toFloat()
                            val screenHeight = size.height.toFloat()
                            localPanX += pan.x / screenWidth
                            localPanY += pan.y / screenHeight
                            currentPanX = localPanX
                            currentPanY = localPanY
                        } else {
                            localPanX = 0f
                            localPanY = 0f
                            currentPanX = 0f
                            currentPanY = 0f
                        }
                        viewModel.setVideoZoom(localZoom, localPanX, localPanY)
                    } while (event.changes.any { it.pressed } && event.changes.size >= 2)
                }
            }
    ) {
        if (!(activity?.isInPictureInPictureMode == true)) {
            VolumeIndicator(
                volume = uiState.volume,
                visible = showVolumeIndicator,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
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