package com.potato.player.feature.player

import com.potato.player.engine.MpvWrapper
import java.util.concurrent.atomic.AtomicBoolean

class SeekController(
    private val wrapper: MpvWrapper,
    private val isActive: AtomicBoolean,
    private val onFastForwardChanged: (Boolean) -> Unit,
    private val onSpeedChanged: (Double) -> Unit
) {


    private var normalPlaybackSpeed = 1.0
    private var isFastForwarding = false

    fun startFastForward(currentSpeed: Double) {
        if (!isActive.get()) return
        if (!isFastForwarding) {
            normalPlaybackSpeed = currentSpeed
            isFastForwarding = true
            onFastForwardChanged(true)
            wrapper.setSpeed(2.0)
        }
    }

    fun stopFastForward() {
        if (!isActive.get()) return
        if (isFastForwarding) {
            isFastForwarding = false
            wrapper.setSpeed(normalPlaybackSpeed)
            onFastForwardChanged(false)
            onSpeedChanged(normalPlaybackSpeed)
        }
    }

    fun setPlaybackSpeed(speed: Double) {
        if (!isActive.get()) return
        val clamped = speed.coerceIn(0.25, 4.0)
        normalPlaybackSpeed = clamped
        if (!isFastForwarding) {
            wrapper.setSpeed(clamped)
            onSpeedChanged(clamped)
        }
    }

    fun resetFastForward() {
        if (isFastForwarding) {
            isFastForwarding = false
            wrapper.setSpeed(normalPlaybackSpeed)
            onFastForwardChanged(false)
        }
    }

}
