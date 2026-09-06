package com.potato.player.feature.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.Surface
import com.potato.player.feature.player.state.OrientationMode

class OrientationManager {

    var activity: Activity? = null

    private var lastVideoWidth: Int = 0
    private var lastVideoHeight: Int = 0

    fun onDimensionsChanged(width: Int, height: Int, rotate: Long, orientationMode: OrientationMode, videoOrientation: String) {
        if (width > 0 && height > 0) {
            lastVideoWidth = width
            lastVideoHeight = height
        }
        apply(orientationMode, videoOrientation, rotate)
    }

    fun apply(orientationMode: OrientationMode, videoOrientation: String, videoRotate: Long) {
        val w = lastVideoWidth
        val h = lastVideoHeight

        when (orientationMode) {
            OrientationMode.LOCK_LANDSCAPE -> {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                return
            }
            OrientationMode.LOCK_PORTRAIT -> {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                return
            }
            OrientationMode.AUTO -> Unit
        }

        if (w == 0 || h == 0) return

        fun effectiveLandscape(): Boolean {
            val swapped = videoRotate == 90L || videoRotate == 270L
            return if (swapped) h > w else w >= h
        }

        activity?.requestedOrientation = when (videoOrientation) {
            "landscape"        -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait"         -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "sensor"           -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            "sensor_landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "sensor_portrait"  -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "locked"           -> {
                val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                    activity?.display
                else
                    @Suppress("DEPRECATION") activity?.windowManager?.defaultDisplay
                when (display?.rotation) {
                    Surface.ROTATION_0,
                    Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }
            else -> // "auto"
                if (effectiveLandscape()) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    fun reset() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    fun clearDimensions() {
        lastVideoWidth = 0
        lastVideoHeight = 0
    }
}
