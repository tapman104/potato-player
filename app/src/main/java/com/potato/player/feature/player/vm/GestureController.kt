package com.potato.player.feature.player.vm

import android.content.Context
import com.potato.player.engine.MpvProp
import com.potato.player.engine.MpvWrapper
import com.potato.player.feature.player.PlayerUiState
import com.potato.player.feature.player.VideoFitMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

class GestureController(
    private val wrapper: MpvWrapper,
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val appContext: Context,
    private val isActive: AtomicBoolean
) {
    fun cycleFitMode() {
        if (!isActive.get()) return
        val next = when (uiState.value.fitMode) {
            VideoFitMode.FIT -> VideoFitMode.FILL
            VideoFitMode.FILL -> VideoFitMode.STRETCH
            VideoFitMode.STRETCH -> VideoFitMode.FIT
        }
        uiState.update { it.copy(fitMode = next) }
        when (next) {
            VideoFitMode.FIT -> {
                wrapper.setPropertyString(MpvProp.PROP_VIDEO_ASPECT_OVERRIDE, "-1")
                wrapper.setPropertyString(MpvProp.PROP_PANSCAN, "0.0")
            }
            VideoFitMode.FILL -> {
                wrapper.setPropertyString(MpvProp.PROP_PANSCAN, "1.0")
                wrapper.setPropertyString(MpvProp.PROP_VIDEO_ASPECT_OVERRIDE, "-1")
            }
            VideoFitMode.STRETCH -> {
                wrapper.setPropertyString(MpvProp.PROP_PANSCAN, "0.0")
                val metrics = appContext.resources.displayMetrics
                val screenWidth = metrics.widthPixels
                val screenHeight = metrics.heightPixels
                wrapper.setPropertyString(MpvProp.PROP_VIDEO_ASPECT_OVERRIDE, "${screenWidth}/${screenHeight}")
            }
        }
    }

    fun setZoom(zoom: Float) {
        setVideoZoom(zoom, uiState.value.videoPanX, uiState.value.videoPanY)
    }

    fun setPan(panX: Float, panY: Float) {
        setVideoZoom(uiState.value.videoZoom, panX, panY)
    }

    fun setVideoZoom(zoom: Float, panX: Float, panY: Float) {
        if (!isActive.get()) return
        val clampedZoom = zoom.coerceIn(1.0f, 4.0f)
        val finalPanX = if (clampedZoom == 1.0f) 0f else panX
        val finalPanY = if (clampedZoom == 1.0f) 0f else panY
        
        val mpvZoom = kotlin.math.ln(clampedZoom.toDouble()) / kotlin.math.ln(2.0)
        wrapper.setPropertyDouble(MpvProp.PROP_VIDEO_ZOOM, mpvZoom)
        wrapper.setPropertyDouble(MpvProp.PROP_VIDEO_PAN_X, finalPanX.toDouble())
        wrapper.setPropertyDouble(MpvProp.PROP_VIDEO_PAN_Y, finalPanY.toDouble())
        
        uiState.update { it.copy(videoZoom = clampedZoom, videoPanX = finalPanX, videoPanY = finalPanY) }
    }

    fun resetZoom() {
        setVideoZoom(1.0f, 0f, 0f)
    }

    fun setVolume(volume: Int) {
        if (!isActive.get()) return
        val clamped = volume.coerceIn(0, 150)
        wrapper.setPropertyInt(MpvProp.PROP_VOLUME, clamped)
    }

    fun toggleLock() {
        uiState.update { it.copy(isLocked = !it.isLocked) }
    }
}
