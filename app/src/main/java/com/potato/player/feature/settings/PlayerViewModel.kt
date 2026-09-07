package com.potato.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val defaultSpeed: Double      = UserPreferencesRepository.DEFAULT_SPEED_VALUE,
    val controlsHideDelay: Int    = UserPreferencesRepository.DEFAULT_HIDE_DELAY_MS,
    val gesturesEnabled: Boolean  = UserPreferencesRepository.DEFAULT_GESTURES_ENABLED,
    val lockButtonEnabled: Boolean = UserPreferencesRepository.DEFAULT_LOCK_BUTTON,
    val videoOrientation: String   = UserPreferencesRepository.DEFAULT_VIDEO_ORIENTATION
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = combine(
        prefsRepository.defaultSpeedFlow,
        prefsRepository.controlsHideDelayFlow,
        prefsRepository.gesturesEnabledFlow,
        prefsRepository.lockButtonEnabledFlow,
        prefsRepository.videoOrientationFlow
    ) { speed, hideDelay, gestures, lockButton, orientation ->
        PlayerUiState(
            defaultSpeed       = speed,
            controlsHideDelay  = hideDelay,
            gesturesEnabled    = gestures,
            lockButtonEnabled  = lockButton,
            videoOrientation   = orientation
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState()
    )

    fun setDefaultSpeed(speed: Double) {
        viewModelScope.launch { prefsRepository.setDefaultSpeed(speed) }
    }

    fun setControlsHideDelay(delayMs: Int) {
        viewModelScope.launch { prefsRepository.setControlsHideDelay(delayMs) }
    }

    fun setGesturesEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setGesturesEnabled(enabled) }
    }

    fun setLockButtonEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setLockButtonEnabled(enabled) }
    }

    fun setVideoOrientation(mode: String) {
        viewModelScope.launch { prefsRepository.setVideoOrientation(mode) }
    }
}
