package com.potato.player.feature.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.feature.player.controls.DoubleTapSeekState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerGestureBox(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onToggleControls: () -> Unit,
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
                val wb = activity?.window?.attributes?.screenBrightness ?: -1f
                if (wb >= 0f) wb
                else try {
                    android.provider.Settings.System.getInt(
                        activity?.contentResolver,
                        android.provider.Settings.System.SCREEN_BRIGHTNESS
                    ) / 255f
                } catch (e: Exception) { 0.5f }
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
        mutableStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 0f)
    }

    var currentZoom by remember { mutableStateOf(1.0f) }
    var currentPanX by remember { mutableStateOf(0f) }
    var currentPanY by remember { mutableStateOf(0f) }
    var showZoomIndicator by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var hideZoomJob by remember { mutableStateOf<Job?>(null) }

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
            // ── Pass 1: multi-touch pinch/pan — intercepts BEFORE children ──
            .pointerInput(Unit) {
                if (activity?.isInPictureInPictureMode == true) return@pointerInput
                awaitEachGesture {
                    // Wait for first finger down; don't consume it so tap detection still fires
                    awaitFirstDown(requireUnconsumed = false)
                    // Poll until we see a second pointer or all fingers lift
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pointers = event.changes.filter { it.pressed }
                        if (pointers.size >= 2) {
                            // Two fingers — handle pinch/pan until all lift
                            showZoomIndicator = true
                            hideZoomJob?.cancel()
                            do {
                                val e2 = awaitPointerEvent(PointerEventPass.Main)
                                val zoom = e2.calculateZoom()
                                val pan = e2.calculatePan()
                                e2.changes.forEach { it.consume() }

                                currentZoom = (currentZoom * zoom).coerceIn(1.0f, 5.0f)
                                val maxPanX = (currentZoom - 1f) * 0.5f
                                val maxPanY = (currentZoom - 1f) * 0.5f
                                currentPanX = (currentPanX + pan.x / size.width).coerceIn(-maxPanX, maxPanX)
                                currentPanY = (currentPanY + pan.y / size.height).coerceIn(-maxPanY, maxPanY)
                                viewModel.setZoom(currentZoom)
                                viewModel.setPan(currentPanX, currentPanY)
                            } while (e2.changes.any { it.pressed })
                            hideZoomJob = coroutineScope.launch {
                                delay(1500)
                                showZoomIndicator = false
                            }
                            break
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            // ── Pass 2: single-finger vertical swipe — brightness / volume ──
            .pointerInput(Unit) {
                if (activity?.isInPictureInPictureMode == true) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    val isLeftSide = startX < size.width / 2f
                    var totalDx = 0f
                    var totalDy = 0f
                    var gestureConsumed = false

                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            // Abort if a second finger joins — pinch/pan loop owns it
                            if (event.changes.count { it.pressed } >= 2) break
    
                            val change = event.changes.firstOrNull() ?: break
                            totalDx += abs(change.positionChange().x)
                            totalDy += abs(change.positionChange().y)
    
                            // Only claim this gesture once it's clearly more vertical than horizontal
                            if (!gestureConsumed && totalDy > 12f && totalDy > totalDx * 1.5f) {
                                gestureConsumed = true
                                viewModel.setSwipingVolumeOrBrightness(true)
                                if (isLeftSide) showBrightnessIndicator = true
                                else showVolumeIndicator = true
                            }
    
                            if (gestureConsumed) {
                                val dy = change.positionChange().y
                                change.consume()
                                if (isLeftSide) {
                                    brightnessLevel = (brightnessLevel - dy / size.height).coerceIn(0.01f, 1.0f)
                                    onBrightnessChange(brightnessLevel)
                                } else {
                                    tempVolume = (tempVolume - (dy / size.height) * maxVolume).coerceIn(0f, maxVolume)
                                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, tempVolume.toInt(), 0)
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        viewModel.setSwipingVolumeOrBrightness(false)
                        if (gestureConsumed) {
                            showBrightnessIndicator = false
                            showVolumeIndicator = false
                        }
                    }
                }
            }
            // ── Pass 3: horizontal swipe seek ──────────────────────────────
            .pointerInput(Unit) {
                if (activity?.isInPictureInPictureMode == true) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDx = 0f
                    var totalDy = 0f
                    var swipeStartSec = 0.0
                    var accumulatedDrag = 0f
                    var gestureConsumed = false

                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.changes.count { it.pressed } >= 2) break
    
                            val change = event.changes.firstOrNull() ?: break
                            val dx = change.positionChange().x
                            val dy = change.positionChange().y
                            totalDx += abs(dx)
                            totalDy += abs(dy)
    
                            if (!gestureConsumed && totalDx > 12f && totalDx > totalDy * 1.5f) {
                                gestureConsumed = true
                                swipeStartSec = viewModel.uiState.value.progressState.positionSec
                                accumulatedDrag = 0f
                                onSwipeSeekStart(swipeStartSec)
                            }
    
                            if (gestureConsumed) {
                                change.consume()
                                accumulatedDrag += dx
                                val seekDelta = (accumulatedDrag / size.width) * 120.0
                                val target = (swipeStartSec + seekDelta)
                                    .coerceIn(0.0, viewModel.uiState.value.progressState.durationSec)
                                viewModel.onSwipeSeek(target)
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        if (gestureConsumed) viewModel.onSwipeSeekFinished()
                    }
                }
            }
            // ── Pass 4: taps, double-tap, long-press ───────────────────────
            .pointerInput(activity?.isInPictureInPictureMode == true) {
                if (activity?.isInPictureInPictureMode == true) return@pointerInput
                detectTapGestures(
                    onPress = {
                        try { tryAwaitRelease() }
                        finally {
                            if (isLongPressActive) {
                                isLongPressActive = false
                                viewModel.stopFastForward()
                            }
                        }
                    },
                    onLongPress = {
                        isLongPressActive = true
                        viewModel.startFastForward()
                    },
                    onDoubleTap = { offset ->
                        val current = doubleTapSeekState
                        val thirdWidth = size.width / 3f
                        if (offset.x < thirdWidth) {
                            viewModel.seekExactRelative(-10)
                            val accum = if (current != null && !current.isForward)
                                current.totalSeconds + 10
                            else 10
                            onDoubleTapSeekState(DoubleTapSeekState(isForward = false, totalSeconds = accum))
                        } else if (offset.x > 2 * thirdWidth) {
                            viewModel.seekExactRelative(10)
                            val accum = if (current != null && current.isForward)
                                current.totalSeconds + 10
                            else 10
                            onDoubleTapSeekState(DoubleTapSeekState(isForward = true, totalSeconds = accum))
                        } else {
                            viewModel.togglePlay()
                        }
                    },
                    onTap = { onToggleControls() }
                )
            }
    ) {
        if (activity?.isInPictureInPictureMode != true) {
            VolumeIndicator(
                volume = ((tempVolume / maxVolume) * 100).toInt(),
                visible = showVolumeIndicator,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
            )
            BrightnessIndicator(
                brightness = brightnessLevel,
                visible = showBrightnessIndicator,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
            )
            ZoomIndicator(
                zoom = currentZoom,
                visible = showZoomIndicator,
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
        androidx.compose.foundation.layout.Column(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .height(100.dp)
                    .width(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((volume / 100f).coerceIn(0f, 1f))
                        .background(Color.White, RoundedCornerShape(2.dp))
                )
            }
            Text(
                text = "$volume%",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
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
        androidx.compose.foundation.layout.Column(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Brightness6,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .height(100.dp)
                    .width(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(brightness.coerceIn(0f, 1f))
                        .background(Color.White, RoundedCornerShape(2.dp))
                )
            }
            Text(
                text = "${(brightness * 100).roundToInt()}%",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun ZoomIndicator(
    zoom: Float,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (visible && zoom > 1.0f) {
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