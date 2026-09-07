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

data class AudioUiState(
    val preferredAudioLang: String = UserPreferencesRepository.DEFAULT_AUDIO_LANG,
    val audioChannels: String      = UserPreferencesRepository.DEFAULT_AUDIO_CHANNELS
)

@HiltViewModel
class AudioViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<AudioUiState> = combine(
        prefsRepository.preferredAudioLangFlow,
        prefsRepository.audioChannelsFlow
    ) { lang, channels ->
        AudioUiState(
            preferredAudioLang = lang,
            audioChannels      = channels
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AudioUiState()
    )

    fun setPreferredAudioLang(lang: String) {
        viewModelScope.launch { prefsRepository.setPreferredAudioLang(lang) }
    }

    fun setAudioChannels(channels: String) {
        viewModelScope.launch { prefsRepository.setAudioChannels(channels) }
    }
}
