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
                wrapper.setAspectOverride("-1")
                wrapper.setPanScan("0.0")
            }
            VideoFitMode.FILL -> {
                wrapper.setPanScan("1.0")
                wrapper.setAspectOverride("-1")
            }
            VideoFitMode.STRETCH -> {
                wrapper.setPanScan("0.0")
                wrapper.setAspectOverride("${screenWidth}/${screenHeight}")
            }
        }
        return next
    }

    fun setVideoZoom(zoom: Float, panX: Float, panY: Float): Triple<Float, Float, Float> {
        val clampedZoom = zoom.coerceIn(0.5f, 5.0f)
        val finalPanX = if (clampedZoom == 1.0f) 0f else panX
        val finalPanY = if (clampedZoom == 1.0f) 0f else panY
        
        val mpvZoom = kotlin.math.ln(clampedZoom.toDouble()) / kotlin.math.ln(2.0)
        wrapper.setVideoZoom(mpvZoom)
        wrapper.setVideoPan(finalPanX.toDouble(), finalPanY.toDouble())
        
        return Triple(finalPanX, finalPanY, clampedZoom)
    }

    fun resetZoom(): Triple<Float, Float, Float> {
        return setVideoZoom(1.0f, 0f, 0f)
    }
}
