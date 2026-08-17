package com.potato.player.feature.player.vm

import com.potato.player.data.UserPreferencesRepository
import com.potato.player.engine.MpvWrapper
import com.potato.player.feature.player.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class SubtitleController(
    private val wrapper: MpvWrapper,
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val isActive: AtomicBoolean,
    private val prefsRepository: UserPreferencesRepository,
    private val scope: CoroutineScope
) {
    fun setSubScale(scale: Double) { 
        if (!isActive.get()) return
        wrapper.setSubScale(scale) 
    }

    fun setSubPos(pos: Int) { 
        if (!isActive.get()) return
        wrapper.setSubPos(pos) 
    }

    fun setSubtitleAppearance(scale: Double, pos: Int) {
        if (!isActive.get()) return
        wrapper.setSubScale(scale)
        wrapper.setSubPos(pos)
        scope.launch {
            prefsRepository.setSubScale(scale)
            prefsRepository.setSubPos(pos)
        }
    }

    fun resetSubtitleAppearance() {
        if (!isActive.get()) return
        uiState.update { it.copy(subScale = 1.0, subPos = 100) }
        wrapper.setSubScale(1.0)
        wrapper.setSubPos(100)
        scope.launch {
            prefsRepository.setSubScale(1.0)
            prefsRepository.setSubPos(100)
        }
    }
}
