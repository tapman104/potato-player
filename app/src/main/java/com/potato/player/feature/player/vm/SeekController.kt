package com.potato.player.feature.player.vm

import com.potato.player.engine.MpvWrapper
import com.potato.player.feature.player.PlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

class SeekController(
    private val wrapper: MpvWrapper,
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val isActive: AtomicBoolean
) {
    var isSliderSeeking: Boolean = false
    private var lastSeekTime: Long = 0L

    fun seekRelative(offsetSec: Double) {
        if (!isActive.get()) return
        val target = (uiState.value.progressState.positionSec + offsetSec).coerceIn(0.0, uiState.value.progressState.durationSec.takeIf { it > 0.0 } ?: Double.MAX_VALUE)
        wrapper.seekTo((target * 1000).toLong())
    }

    fun seekExactRelative(offsetSec: Int) {
        if (!isActive.get()) return
        wrapper.seekRelative(offsetSec.toDouble())
    }

    fun onSliderDragStart(posSec: Double) {
        isSliderSeeking = true
        uiState.update { it.copy(progressState = it.progressState.copy(dragPositionSec = posSec)) }
    }

    fun onSliderDragChange(posSec: Double) {
        if (!isActive.get()) return
        uiState.update { it.copy(progressState = it.progressState.copy(dragPositionSec = posSec)) }
        wrapper.seekTo((posSec * 1000).toLong())
    }

    fun onSliderDragEnd(posSec: Double) {
        if (!isActive.get()) return
        isSliderSeeking = false
        wrapper.seekTo((posSec * 1000).toLong())
        uiState.update { it.copy(progressState = it.progressState.copy(dragPositionSec = null)) }
    }

    fun onSwipeSeek(positionSec: Double) {
        if (!isActive.get()) return
        uiState.update { it.copy(swipeSeekTargetSec = positionSec) }
        if (System.currentTimeMillis() - lastSeekTime >= 100L) {
            wrapper.seekTo((positionSec * 1000).toLong())
            lastSeekTime = System.currentTimeMillis()
        }
    }

    fun onSwipeSeekFinished() {
        if (!isActive.get()) return
        val target = uiState.value.swipeSeekTargetSec
        uiState.update { it.copy(swipeSeekTargetSec = null) }
        if (target != null) {
            wrapper.seekTo((target * 1000).toLong())
            lastSeekTime = System.currentTimeMillis()
        }
    }

    fun setSwipingVolumeOrBrightness(isSwiping: Boolean) {
        uiState.update { it.copy(isSwipingVolumeOrBrightness = isSwiping) }
    }
}
