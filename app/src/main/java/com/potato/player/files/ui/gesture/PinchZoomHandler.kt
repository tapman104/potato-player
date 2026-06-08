package com.potato.player.player.ui.gesture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PinchZoomHandler {
    private val _zoomScale = MutableStateFlow(1f)
    val zoomScale: StateFlow<Float> = _zoomScale.asStateFlow()

    /** Called on every pinch gesture tick with the cumulative scale factor. */
    fun onZoom(scaleFactor: Float, maxZoom: Float = MAX_ZOOM) {
        val current = _zoomScale.value
        val next = (current * scaleFactor).coerceIn(MIN_ZOOM, maxZoom)
        _zoomScale.value = next
    }

    /** Reset to fit mode. */
    fun resetZoom() {
        _zoomScale.value = 1f
    }

    companion object {
        const val MIN_ZOOM = 1f   // never smaller than fit
        const val MAX_ZOOM = 3f   // 3× is generous ceiling, screen edge is visual cap
    }
}
