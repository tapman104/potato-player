package com.potato.player.feature.player

import com.potato.player.engine.MpvWrapper
import java.util.concurrent.atomic.AtomicBoolean

class SeekController(
    private val wrapper: MpvWrapper,
    private val isActive: AtomicBoolean,
    private val onDragPositionChanged: (Double?) -> Unit,
    private val onSwipeTargetChanged: (Double?) -> Unit,
    private val onFastForwardChanged: (Boolean) -> Unit,
    private val onSpeedChanged: (Double) -> Unit
) {
    private var isDragging: Boolean = false
    private var lastDragPositionSec: Double = 0.0
    private var swipeSeekTargetSec: Double? = null

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

    fun onSliderDragStart(posSec: Double) {
        isDragging = true
        lastDragPositionSec = posSec
        onDragPositionChanged(posSec)
    }

    fun onSliderDragChange(posSec: Double) {
        if (!isActive.get()) return
        lastDragPositionSec = posSec
        // intentionally does NOT call wrapper or emit state
    }

    fun onSliderDragEnd(posSec: Double) {
        if (!isActive.get()) return
        isDragging = false
        lastDragPositionSec = posSec
        val ms = (posSec * 1000).toLong()
        onDragPositionChanged(null)
        wrapper.seekFast(ms)
    }

    fun onSwipeSeek(positionSec: Double) {
        if (!isActive.get()) return
        swipeSeekTargetSec = positionSec
        onSwipeTargetChanged(positionSec)
    }

    fun onSwipeSeekFinished() {
        if (!isActive.get()) return
        val target = swipeSeekTargetSec
        swipeSeekTargetSec = null
        onSwipeTargetChanged(null)
        if (target != null) {
            val ms = (target * 1000).toLong()
            wrapper.seekFast(ms)
        }
    }

    fun seekExactRelative(offsetSec: Int) {
        if (!isActive.get()) return
        wrapper.seekRelative(offsetSec.toDouble())
    }
}
