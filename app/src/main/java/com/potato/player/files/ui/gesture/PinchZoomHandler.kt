package com.potato.player.player.ui.gesture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ZoomState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

class PinchZoomHandler {
    var maxZoom: Float = 4f

    private val _zoomState = MutableStateFlow(ZoomState())
    val zoomState: StateFlow<ZoomState> = _zoomState.asStateFlow()

    fun onZoom(scaleChange: Float, viewportWidth: Float, viewportHeight: Float) {
        val current = _zoomState.value
        val newScale = (current.scale * scaleChange).coerceIn(MIN_ZOOM, maxZoom)
        
        val maxOffsetX = (newScale - 1f) * viewportWidth / 2f
        val maxOffsetY = (newScale - 1f) * viewportHeight / 2f
        
        val newOffsetX = current.offsetX.coerceIn(-maxOffsetX, maxOffsetX)
        val newOffsetY = current.offsetY.coerceIn(-maxOffsetY, maxOffsetY)
        
        _zoomState.value = ZoomState(newScale, newOffsetX, newOffsetY)
    }

    fun onPan(dx: Float, dy: Float, viewportWidth: Float, viewportHeight: Float) {
        val current = _zoomState.value
        if (current.scale <= 1f) return
        
        val maxOffsetX = (current.scale - 1f) * viewportWidth / 2f
        val maxOffsetY = (current.scale - 1f) * viewportHeight / 2f
        
        val newOffsetX = (current.offsetX + dx).coerceIn(-maxOffsetX, maxOffsetX)
        val newOffsetY = (current.offsetY + dy).coerceIn(-maxOffsetY, maxOffsetY)
        
        _zoomState.value = current.copy(offsetX = newOffsetX, offsetY = newOffsetY)
    }

    fun resetZoom() {
        _zoomState.value = ZoomState()
    }

    companion object {
        const val MIN_ZOOM = 1f
    }
}
