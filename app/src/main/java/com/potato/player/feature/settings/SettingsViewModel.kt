package com.potato.player.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.BuildConfig
import com.potato.player.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    // existing
    val preferredSubLang: String = "eng",
    val appVersion: String = "",
    // playback
    val defaultDecoder: String = UserPreferencesRepository.DEFAULT_DECODER_VALUE,
    val defaultSpeed: Double = UserPreferencesRepository.DEFAULT_SPEED_VALUE,
    // interface
    val controlsHideDelay: Int = UserPreferencesRepository.DEFAULT_HIDE_DELAY_MS,
    val gesturesEnabled: Boolean = UserPreferencesRepository.DEFAULT_GESTURES_ENABLED,
    val lockButtonEnabled: Boolean = UserPreferencesRepository.DEFAULT_LOCK_BUTTON,
    val autoRotation: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context
) : ViewModel() {

    private val prefsRepository = UserPreferencesRepository(context)
    
    // Using a flow for app version to cleanly combine with preferences,
    // even though it's static
    private val _appVersion = MutableStateFlow(BuildConfig.VERSION_NAME)

    val uiState: StateFlow<SettingsUiState> = combine(
        prefsRepository.preferredSubLangFlow,
        prefsRepository.defaultDecoderFlow,
        prefsRepository.defaultSpeedFlow,
        prefsRepository.controlsHideDelayFlow,
        prefsRepository.gesturesEnabledFlow,
        prefsRepository.lockButtonEnabledFlow,
        prefsRepository.autoRotationFlow,
        _appVersion
    ) { values ->
        SettingsUiState(
            preferredSubLang  = values[0] as String,
            defaultDecoder    = values[1] as String,
            defaultSpeed      = values[2] as Double,
            controlsHideDelay = values[3] as Int,
            gesturesEnabled   = values[4] as Boolean,
            lockButtonEnabled = values[5] as Boolean,
            autoRotation      = values[6] as Boolean,
            appVersion        = values[7] as String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(appVersion = BuildConfig.VERSION_NAME)
    )

    fun setPreferredSubLang(code: String) {
        viewModelScope.launch {
            prefsRepository.setPreferredSubLang(code)
        }
    }

    fun setDefaultDecoder(mode: String) {
        viewModelScope.launch { prefsRepository.setDefaultDecoder(mode) }
    }

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

    fun setAutoRotation(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setAutoRotation(enabled) }
    }
}
