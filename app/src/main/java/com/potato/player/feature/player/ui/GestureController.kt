package com.potato.player.feature.player.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import com.potato.player.feature.player.controls.DoubleTapSeekState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

enum class GestureType { NONE, SEEK, VOLUME_BRIGHTNESS, PAN, PINCH, LONG_PRESS }

data class GestureUiState(
    val doubleTapSeekState: DoubleTapSeekState? = null,
    val swipeSeekTargetSec: Double? = null,
    val swipeDragStartSec: Double = 0.0,
    val brightnessLevel: Float = 0.5f,
    val showBrightnessIndicator: Boolean = false,
    val volumeLevel: Int = 100,
    val showVolumeIndicator: Boolean = false,
    val zoomLevel: Float = 1f,
    val showZoomIndicator: Boolean = false,
)

class GestureController(
    private val positionProvider: () -> Double,
    private val durationProvider: () -> Double,
    private val gesturesEnabled: () -> Boolean,
    private val scope: CoroutineScope,
    private val setVideoZoom: (zoom: Float, panX: Float, panY: Float) -> Unit,
    private val setVolume: (volume: Int) -> Unit,
    private val onSeek: (positionSec: Double) -> Unit,
    private val onSeekFinished: (targetSec: Double) -> Unit,
    private val stopFastForward: () -> Unit,
    private val startFastForward: () -> Unit,
    private val seekExactRelative: (offsetSec: Int) -> Unit,
    private val togglePlay: () -> Unit,
    private val applyBrightness: (Float) -> Unit,
    private val onToggleControls: () -> Unit,
    private val performHapticFeedback: () -> Unit,
    private val onGestureActive: (Boolean) -> Unit,
    initialBrightness: Float
) {
    private val _uiState = MutableStateFlow(GestureUiState(brightnessLevel = initialBrightness))
    val uiState: StateFlow<GestureUiState> = _uiState.asStateFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(), _uiState.value)

    private val gestureTypeRef = AtomicReference(GestureType.NONE)

    private var zoom = 1.0f
    private var panX = 0f
    private var panY = 0f
    private var brightnessLevel = initialBrightness
    private var tempVolume = 100f
    private var isLongPressActive = false

    private var hideZoomJob: Job? = null
    private var doubleTapJob: Job? = null

    fun resetZoom() {
        val currentGesture = gestureTypeRef.get()
        if (currentGesture == GestureType.PINCH || currentGesture == GestureType.PAN) {
            releaseGesture(currentGesture)
        }
        zoom = 1.0f
        panX = 0f
        panY = 0f
        setVideoZoom(1.0f, 0f, 0f)
        _uiState.update { it.copy(zoomLevel = 1.0f, showZoomIndicator = false) }
    }

    suspend fun handlePointerInput(inputScope: PointerInputScope) {
        kotlinx.coroutines.coroutineScope {
            launch { handlePinch(inputScope) }
            launch { handleDrag(inputScope) }
            launch { handleTaps(inputScope) }
        }
    }

    private fun tryAcquireGesture(type: GestureType): Boolean {
        if (gestureTypeRef.compareAndSet(GestureType.NONE, type)) {
            onGestureActive(true)
            return true
        }
        return false
    }

    private fun releaseGesture(type: GestureType) {
        if (gestureTypeRef.compareAndSet(type, GestureType.NONE)) {
            onGestureActive(false)
        }
    }

    private suspend fun handlePinch(inputScope: PointerInputScope) {
        inputScope.awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val pointers = ArrayList<androidx.compose.ui.input.pointer.PointerInputChange>(event.changes.size)
                for (i in 0 until event.changes.size) {
                    if (event.changes[i].pressed) pointers.add(event.changes[i])
                }
                if (pointers.size >= 2) {
                    val current = gestureTypeRef.get()
                    if (current == GestureType.NONE || current == GestureType.PINCH) {
                        if (current == GestureType.NONE) {
                            if (gestureTypeRef.compareAndSet(GestureType.NONE, GestureType.PINCH)) {
                                onGestureActive(true)
                            } else {
                                continue
                            }
                        }
                        
                        _uiState.update { it.copy(showZoomIndicator = true) }
                        hideZoomJob?.cancel()

                        var isCancelled = true
                        try {
                            do {
                                val e2 = awaitPointerEvent(PointerEventPass.Main)
                                val zoomChange = e2.calculateZoom()
                                val panChange = e2.calculatePan()
                                e2.changes.forEach { it.consume() }

                                zoom = (zoom * zoomChange).coerceIn(0.5f, 5.0f)
                                val maxPanX = ((zoom - 1f) * 0.5f).coerceAtLeast(0f)
                                val maxPanY = ((zoom - 1f) * 0.5f).coerceAtLeast(0f)
                                panX = (panX + panChange.x / inputScope.size.width).coerceIn(-maxPanX, maxPanX)
                                panY = (panY + panChange.y / inputScope.size.height).coerceIn(-maxPanY, maxPanY)
                                
                                setVideoZoom(zoom, panX, panY)
                                _uiState.update { it.copy(zoomLevel = zoom) }
                                
                                var anyPressed = false
                                for (i in 0 until e2.changes.size) {
                                    if (e2.changes[i].pressed) {
                                        anyPressed = true
                                        break
                                    }
                                }
                                if (!anyPressed) break
                            } while (true)

                            isCancelled = false
                            hideZoomJob = scope.launch {
                                delay(1500)
                                _uiState.update { it.copy(showZoomIndicator = false) }
                            }
                        } finally {
                            releaseGesture(GestureType.PINCH)
                            if (isCancelled) {
                                hideZoomJob?.cancel()
                                _uiState.update { it.copy(showZoomIndicator = false) }
                            }
                        }
                        break
                    }
                }
                
                var anyPressed = false
                for (i in 0 until event.changes.size) {
                    if (event.changes[i].pressed) {
                        anyPressed = true
                        break
                    }
                }
                if (!anyPressed) break
            } while (true)
        }
    }

    private suspend fun handleDrag(inputScope: PointerInputScope) {
        inputScope.awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startX = down.position.x
            val isLeftSide = startX < inputScope.size.width / 2f
            
            var totalDx = 0f
            var totalDy = 0f
            var lockedInType = GestureType.NONE
            var seekAnchorSec = 0.0
            var gestureDurationSec = 0.0
            var accumulatedDragX = 0f

            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    var pressedCount = 0
                    for (i in 0 until event.changes.size) {
                        if (event.changes[i].pressed) pressedCount++
                    }
                    if (pressedCount >= 2) break // Let pinch take over
                    if (gestureTypeRef.get() == GestureType.PINCH) break // Abort if pinch won
                    
                    val change = event.changes.firstOrNull() ?: break
                    if (lockedInType == GestureType.NONE) {
                        totalDx += abs(change.positionChange().x)
                        totalDy += abs(change.positionChange().y)

                        val isPanGesture = zoom > 1.05f
                        
                        if (isPanGesture && (totalDx > 12f || totalDy > 12f)) {
                            if (tryAcquireGesture(GestureType.PAN)) {
                                lockedInType = GestureType.PAN
                            }
                        } else if (totalDx > 12f && totalDx > totalDy * 2.0f) {
                            if (tryAcquireGesture(GestureType.SEEK)) {
                                lockedInType = GestureType.SEEK
                                seekAnchorSec = positionProvider()
                                gestureDurationSec = durationProvider()
                                _uiState.update { it.copy(swipeDragStartSec = seekAnchorSec) }
                            }
                        } else if (totalDy > 12f && totalDy > totalDx * 2.0f) {
                            if (tryAcquireGesture(GestureType.VOLUME_BRIGHTNESS)) {
                                lockedInType = GestureType.VOLUME_BRIGHTNESS
                                if (isLeftSide) {
                                    _uiState.update { it.copy(showBrightnessIndicator = true) }
                                } else {
                                    _uiState.update { it.copy(showVolumeIndicator = true) }
                                }
                            }
                        }
                    }

                    if (lockedInType != GestureType.NONE) {
                        change.consume()
                        val dx = change.positionChange().x
                        val dy = change.positionChange().y

                        when (lockedInType) {
                            GestureType.PAN -> {
                                val maxPanX = ((zoom - 1f) * 0.5f).coerceAtLeast(0f)
                                val maxPanY = ((zoom - 1f) * 0.5f).coerceAtLeast(0f)
                                panX = (panX + dx / inputScope.size.width).coerceIn(-maxPanX, maxPanX)
                                panY = (panY + dy / inputScope.size.height).coerceIn(-maxPanY, maxPanY)
                                setVideoZoom(zoom, panX, panY)
                            }
                            GestureType.SEEK -> {
                                accumulatedDragX += dx
                                val seekDelta = (accumulatedDragX / inputScope.size.width) * 120.0
                                val target = (seekAnchorSec + seekDelta).coerceIn(0.0, gestureDurationSec)
                                _uiState.update { it.copy(swipeSeekTargetSec = target) }
                                onSeek(target)
                            }
                            GestureType.VOLUME_BRIGHTNESS -> {
                                if (isLeftSide) {
                                    val newBrightness = (brightnessLevel - dy / inputScope.size.height).coerceIn(0.01f, 1.0f)
                                    if (newBrightness != brightnessLevel && (newBrightness == 0.01f || newBrightness == 1.0f)) {
                                        performHapticFeedback()
                                    }
                                    brightnessLevel = newBrightness
                                    _uiState.update { it.copy(brightnessLevel = brightnessLevel) }
                                    applyBrightness(brightnessLevel)
                                } else {
                                    val newVolume = (tempVolume - (dy / inputScope.size.height) * 100f).coerceIn(0f, 100f)
                                    if (newVolume != tempVolume && (newVolume == 0f || newVolume == 100f)) {
                                        performHapticFeedback()
                                    }
                                    tempVolume = newVolume
                                    _uiState.update { it.copy(volumeLevel = tempVolume.toInt()) }
                                    setVolume(tempVolume.toInt())
                                }
                            }
                            else -> {}
                        }
                    }
                    var dragAnyPressed = false
                    for (i in 0 until event.changes.size) {
                        if (event.changes[i].pressed) {
                            dragAnyPressed = true
                            break
                        }
                    }
                    if (!dragAnyPressed) break
                } while (true)
            } finally {
                if (lockedInType == GestureType.SEEK) {
                    val finalTarget = _uiState.value.swipeSeekTargetSec
                    if (finalTarget != null) {
                        onSeekFinished(finalTarget)
                    }
                    _uiState.update { it.copy(swipeSeekTargetSec = null) }
                }
                if (lockedInType == GestureType.VOLUME_BRIGHTNESS) {
                    _uiState.update { it.copy(showBrightnessIndicator = false, showVolumeIndicator = false) }
                }
                if (lockedInType != GestureType.NONE) {
                    releaseGesture(lockedInType)
                }
            }
        }
    }

    private suspend fun handleTaps(inputScope: PointerInputScope) {
        inputScope.detectTapGestures(
            onPress = {
                try { 
                    tryAwaitRelease() 
                } finally {
                    if (isLongPressActive) {
                        isLongPressActive = false
                        stopFastForward()
                        releaseGesture(GestureType.LONG_PRESS)
                    }
                }
            },
            onLongPress = {
                if (gesturesEnabled()) {
                    if (tryAcquireGesture(GestureType.LONG_PRESS)) {
                        isLongPressActive = true
                        startFastForward()
                    }
                }
            },
            onDoubleTap = { offset ->
                if (gesturesEnabled()) {
                    scope.launch {
                        if (gestureTypeRef.get() == GestureType.NONE) {
                            doubleTapJob?.cancel()
                            val current = _uiState.value.doubleTapSeekState
                            val thirdWidth = inputScope.size.width / 3f
                            if (offset.x < thirdWidth) {
                                seekExactRelative(-10)
                                val accum = if (current != null && !current.isForward) current.totalSeconds + 10 else 10
                                _uiState.update { it.copy(doubleTapSeekState = DoubleTapSeekState(isForward = false, totalSeconds = accum)) }
                            } else if (offset.x > 2 * thirdWidth) {
                                seekExactRelative(10)
                                val accum = if (current != null && current.isForward) current.totalSeconds + 10 else 10
                                _uiState.update { it.copy(doubleTapSeekState = DoubleTapSeekState(isForward = true, totalSeconds = accum)) }
                            } else {
                                togglePlay()
                            }
                            
                            doubleTapJob = scope.launch {
                                delay(1200L)
                                _uiState.update { it.copy(doubleTapSeekState = null) }
                            }
                        }
                    }
                } else if (!gesturesEnabled()) {
                    togglePlay()
                }
            },
            onTap = { 
                scope.launch {
                    if (gestureTypeRef.get() == GestureType.NONE) {
                        onToggleControls() 
                    }
                }
            }
        )
    }
}
