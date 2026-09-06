package com.potato.player.feature.player

import android.view.Surface
import com.potato.player.engine.MpvProp
import com.potato.player.engine.MpvWrapper

class SurfaceManager(
    private val wrapper: MpvWrapper,
    private val onReadyToLoad: () -> Unit,
) {
    private var surface: Surface? = null

    fun onSurfaceReady(surface: Surface) {
        this.surface = surface
        wrapper.attachSurface(surface)
        onReadyToLoad()
    }

    fun onSurfaceDestroyed() {
        if (surface != null) {
            wrapper.detachSurface()
            surface = null
        }
    }

    fun setSurfaceSize(width: Int, height: Int) {
        wrapper.setPropertyString(MpvProp.ANDROID_SURFACE_SIZE, "${width}x${height}")
    }

    fun hasSurface(): Boolean = surface != null
}
