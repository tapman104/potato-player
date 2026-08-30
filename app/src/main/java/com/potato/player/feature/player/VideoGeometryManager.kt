package com.potato.player.feature.player

import com.potato.player.engine.MpvWrapper
import com.potato.player.engine.MpvProp

class VideoGeometryManager(private val wrapper: MpvWrapper) {

    fun cycleFitMode(currentFitMode: VideoFitMode, screenWidth: Int, screenHeight: Int): VideoFitMode {
        val next = when (currentFitMode) {
            VideoFitMode.FIT -> VideoFitMode.FILL
            VideoFitMode.FILL -> VideoFitMode.STRETCH
            VideoFitMode.STRETCH -> VideoFitMode.FIT
        }
        when (next) {
            VideoFitMode.FIT -> {
                wrapper.setPropertyString(MpvProp.VIDEO_ASPECT_OVERRIDE, "-1")
                wrapper.setPropertyString(MpvProp.PANSCAN, "0.0")
            }
            VideoFitMode.FILL -> {
                wrapper.setPropertyString(MpvProp.PANSCAN, "1.0")
                wrapper.setPropertyString(MpvProp.VIDEO_ASPECT_OVERRIDE, "-1")
            }
            VideoFitMode.STRETCH -> {
                wrapper.setPropertyString(MpvProp.PANSCAN, "0.0")
                wrapper.setPropertyString(MpvProp.VIDEO_ASPECT_OVERRIDE, "${screenWidth}/${screenHeight}")
            }
        }
        return next
    }

    fun setVideoZoom(zoom: Float, panX: Float, panY: Float, onGestureStateUpdate: (panX: Float, panY: Float, zoom: Float) -> Unit) {
        val clampedZoom = zoom.coerceIn(0.5f, 5.0f)
        val finalPanX = if (clampedZoom == 1.0f) 0f else panX
        val finalPanY = if (clampedZoom == 1.0f) 0f else panY
        
        val mpvZoom = kotlin.math.ln(clampedZoom.toDouble()) / kotlin.math.ln(2.0)
        wrapper.setPropertyDouble(MpvProp.VIDEO_ZOOM, mpvZoom)
        wrapper.setPropertyDouble(MpvProp.VIDEO_PAN_X, finalPanX.toDouble())
        wrapper.setPropertyDouble(MpvProp.VIDEO_PAN_Y, finalPanY.toDouble())
        
        onGestureStateUpdate(finalPanX, finalPanY, clampedZoom)
    }

    fun resetZoom(onGestureStateUpdate: (panX: Float, panY: Float, zoom: Float) -> Unit) {
        setVideoZoom(1.0f, 0f, 0f, onGestureStateUpdate)
    }
}
