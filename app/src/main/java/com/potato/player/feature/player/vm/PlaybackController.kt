package com.potato.player.feature.player.vm

import com.potato.player.data.UserPreferencesRepository
import com.potato.player.engine.MpvWrapper
import com.potato.player.feature.player.ActiveDialog
import com.potato.player.feature.player.OrientationMode
import com.potato.player.feature.player.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class PlaybackController(
    private val wrapper: MpvWrapper,
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val isActive: AtomicBoolean
) {
    var wasPlayingBeforePause: Boolean = false
    var lastLoadedUri: String? = null

    fun togglePlay() {
        if (!isActive.get()) return
        wrapper.togglePlay()
    }

    fun pause() {
        if (!isActive.get()) return
        wrapper.pause()
    }

    fun onPlayerPause() {
        wasPlayingBeforePause = uiState.value.isPlaying
        pause()
    }

    fun onPlayerResume() {
        if (wasPlayingBeforePause) {
            wrapper.resume()
        }
    }

    fun setDecoder(mode: String) {
        if (!isActive.get()) return
        val hwdec = when (mode) { "no" -> "SW"; "mediacodec" -> "HW"; else -> "HW+" }
        uiState.update { it.copy(hwdecCurrent = hwdec) }
        wrapper.setDecoder(mode)
    }

    fun setPlaybackSpeed(speed: Double, isFastForwarding: Boolean, onNormalSpeedStored: (Double) -> Unit) {
        if (!isActive.get()) return
        val clamped = speed.coerceIn(0.25, 4.0)
        onNormalSpeedStored(clamped)
        if (!isFastForwarding) {
            wrapper.setSpeed(clamped)
        }
    }

    fun startFastForward(normalPlaybackSpeed: Double) {
        if (!isActive.get()) return
        if (!uiState.value.isFastForwarding) {
            uiState.update { it.copy(isFastForwarding = true) }
            wrapper.setSpeed(2.0)
        }
    }

    fun stopFastForward(normalPlaybackSpeed: Double, onSpeedRestored: (Double) -> Unit) {
        if (!isActive.get()) return
        if (uiState.value.isFastForwarding) {
            uiState.update { it.copy(isFastForwarding = false) }
            wrapper.setSpeed(normalPlaybackSpeed)
            uiState.update { it.copy(playbackSpeed = normalPlaybackSpeed) }
            onSpeedRestored(normalPlaybackSpeed)
        }
    }

    fun showDialog(dialog: ActiveDialog) {
        uiState.update { it.copy(activeDialog = dialog) }
    }

    fun dismissDialog() {
        uiState.update { it.copy(activeDialog = ActiveDialog.None) }
    }

    fun cycleOrientationMode() {
        val next = when (uiState.value.orientationMode) {
            OrientationMode.AUTO -> OrientationMode.LOCK_LANDSCAPE
            OrientationMode.LOCK_LANDSCAPE -> OrientationMode.LOCK_PORTRAIT
            OrientationMode.LOCK_PORTRAIT -> OrientationMode.AUTO
        }
        uiState.update { it.copy(orientationMode = next) }
    }

    fun toggleAutoRotation(prefsRepository: UserPreferencesRepository, scope: CoroutineScope) {
        val next = !uiState.value.isAutoRotation
        uiState.update { it.copy(isAutoRotation = next) }
        scope.launch { prefsRepository.setAutoRotation(next) }
    }
}
