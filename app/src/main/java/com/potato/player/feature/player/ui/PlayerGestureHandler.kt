package com.potato.player.feature.player.ui

import com.potato.player.feature.player.ui.BrightnessIndicator
import com.potato.player.feature.player.state.*
import com.potato.player.feature.player.PlayerViewModel
import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import com.potato.player.feature.player.controls.DoubleTapSeekOverlay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerGestureBox(
    gestureState: PlayerGestureState,
    viewModel: PlayerViewModel,
    onToggleControls: () -> Unit,
    fileLoaded: Boolean,
    activity: Activity?,
    gesturesEnabled: Boolean
) {
    val progressState by viewModel.progressState.collectAsStateWithLifecycle()
    val currentPositionSec = progressState.positionSec
    val durationSec = progressState.durationSec

    var doubleTapSeekState by remember { mutableStateOf<DoubleTapSeekState?>(null) }
    var swipeDragStartSec by remember { mutableStateOf(0.0) }

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

    val maxVolume = 100f
    var tempVolume by remember { mutableStateOf(100f) }

    var currentZoom by remember { mutableStateOf(1.0f) }
    var currentPanX by remember { mutableStateOf(0f) }
    var currentPanY by remember { mutableStateOf(0f) }
    var showZoomIndicator by remember { mutableStateOf(false) }
    var isPinchActive by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var hideZoomJob by remember { mutableStateOf<Job?>(null) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(fileLoaded) {
        if (fileLoaded) {
            currentZoom = 1.0f
            currentPanX = 0f
            currentPanY = 0f
        }
    }

    LaunchedEffect(doubleTapSeekState?.triggerId) {
        if (doubleTapSeekState != null) {
            delay(1200L)
            doubleTapSeekState = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ── Pass 1: multi-touch pinch/pan — intercepts BEFORE children ──
            .pointerInput(gesturesEnabled) {
                if (!gesturesEnabled || activity?.isInPictureInPictureMode == true) return@pointerInput
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
                            isPinchActive = true
                            do {
                                val e2 = awaitPointerEvent(PointerEventPass.Main)
                                val zoom = e2.calculateZoom()
                                val pan = e2.calculatePan()
                                e2.changes.forEach { it.consume() }

                                currentZoom = (currentZoom * zoom).coerceIn(0.5f, 5.0f)
                                val maxPanX = ((currentZoom - 1f) * 0.5f).coerceAtLeast(0f)
                                val maxPanY = ((currentZoom - 1f) * 0.5f).coerceAtLeast(0f)
                                currentPanX = (currentPanX + pan.x / size.width).coerceIn(-maxPanX, maxPanX)
                                currentPanY = (currentPanY + pan.y / size.height).coerceIn(-maxPanY, maxPanY)
                                viewModel.setVideoZoom(currentZoom, currentPanX, currentPanY)
                            } while (e2.changes.any { it.pressed })
                            isPinchActive = false
                            hideZoomJob = coroutineScope.launch {
                                delay(1500)
                                showZoomIndicator = false
                            }
                            break
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            // ── Pass 2: single-finger vertical swipe — brightness / volume / pan ──
            .pointerInput(gesturesEnabled) {
                if (!gesturesEnabled || activity?.isInPictureInPictureMode == true) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    val isLeftSide = startX < size.width / 2f
                    val isPanGesture = currentZoom > 1.0f && isPinchActive
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
    
                            if (!gestureConsumed && (isPanGesture || (totalDy > 12f && totalDy > totalDx * 1.5f))) {
                                gestureConsumed = true
                                if (!isPanGesture) {
                                    viewModel.setSwipingVolumeOrBrightness(true)
                                    if (isLeftSide) showBrightnessIndicator = true
                                    else showVolumeIndicator = true
                                }
                            }
    
                            if (gestureConsumed) {
                                val dy = change.positionChange().y
                                val dx = change.positionChange().x
                                change.consume()
                                if (isPanGesture) {
                                    val maxPanX = ((currentZoom - 1f) * 0.5f).coerceAtLeast(0f)
                                    val maxPanY = ((currentZoom - 1f) * 0.5f).coerceAtLeast(0f)
                                    currentPanX = (currentPanX + dx / size.width).coerceIn(-maxPanX, maxPanX)
                                    currentPanY = (currentPanY + dy / size.height).coerceIn(-maxPanY, maxPanY)
                                    viewModel.setVideoZoom(currentZoom, currentPanX, currentPanY)
                                } else {
                                    if (isLeftSide) {
                                        // LEFT = brightness
                                        val newBrightness = (brightnessLevel - dy / size.height).coerceIn(0.01f, 1.0f)
                                        if (newBrightness != brightnessLevel && (newBrightness == 0.01f || newBrightness == 1.0f)) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        brightnessLevel = newBrightness
                                        val window = activity?.window
                                        if (window != null) {
                                            val lp = window.attributes
                                            lp.screenBrightness = brightnessLevel
                                            window.attributes = lp
                                        }
                                    } else {
                                        // RIGHT = volume
                                        val newVolume = (tempVolume - (dy / size.height) * maxVolume).coerceIn(0f, maxVolume)
                                        if (newVolume != tempVolume && (newVolume == 0f || newVolume == maxVolume)) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        tempVolume = newVolume
                                        viewModel.setVolume(tempVolume.toInt())
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        if (!isPanGesture) viewModel.setSwipingVolumeOrBrightness(false)
                        if (gestureConsumed && !isPanGesture) {
                            showBrightnessIndicator = false
                            showVolumeIndicator = false
                        }
                    }
                }
            }
            // ── Pass 3: horizontal swipe seek ──────────────────────────────
            .pointerInput(gesturesEnabled) {
                if (!gesturesEnabled || activity?.isInPictureInPictureMode == true) return@pointerInput
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
                                swipeStartSec = currentPositionSec
                                accumulatedDrag = 0f
                                swipeDragStartSec = swipeStartSec
                            }
    
                            if (gestureConsumed) {
                                change.consume()
                                accumulatedDrag += dx
                                val seekDelta = (accumulatedDrag / size.width) * 120.0
                                val target = (swipeStartSec + seekDelta)
                                    .coerceIn(0.0, durationSec)
                                viewModel.onSwipeSeek(target)
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        if (gestureConsumed) viewModel.onSwipeSeekFinished()
                    }
                }
            }
            // ── Pass 4: taps, double-tap, long-press ───────────────────────
            .pointerInput(gesturesEnabled, activity?.isInPictureInPictureMode == true) {
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
                        if (gesturesEnabled) {
                            isLongPressActive = true
                            viewModel.startFastForward()
                        }
                    },
                    onDoubleTap = { offset ->
                        if (gesturesEnabled) {
                            val current = doubleTapSeekState
                            val thirdWidth = size.width / 3f
                            if (offset.x < thirdWidth) {
                                viewModel.seekExactRelative(-10)
                                val accum = if (current != null && !current.isForward)
                                    current.totalSeconds + 10
                                else 10
                                doubleTapSeekState = DoubleTapSeekState(isForward = false, totalSeconds = accum)
                            } else if (offset.x > 2 * thirdWidth) {
                                viewModel.seekExactRelative(10)
                                val accum = if (current != null && current.isForward)
                                    current.totalSeconds + 10
                                else 10
                                doubleTapSeekState = DoubleTapSeekState(isForward = true, totalSeconds = accum)
                            } else {
                                viewModel.togglePlay()
                            }
                        } else {
                            viewModel.togglePlay()
                        }
                    },
                    onTap = { onToggleControls() }
                )
            }
    ) {
        if (activity?.isInPictureInPictureMode != true) {
            DoubleTapSeekOverlay(seekState = doubleTapSeekState)
        }

        SwipeSeekOverlay(
            targetSec = gestureState.swipeSeekTargetSec,
            dragStartSec = swipeDragStartSec,
            isPipMode = activity?.isInPictureInPictureMode == true
        )

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
