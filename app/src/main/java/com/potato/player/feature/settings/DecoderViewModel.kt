package com.potato.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DecoderUiState(
    val defaultDecoder: String = UserPreferencesRepository.DEFAULT_DECODER_VALUE
)

@HiltViewModel
class DecoderViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<DecoderUiState> = prefsRepository.defaultDecoderFlow
        .map { decoder -> DecoderUiState(defaultDecoder = decoder) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DecoderUiState()
        )

    fun setDefaultDecoder(mode: String) {
        viewModelScope.launch { prefsRepository.setDefaultDecoder(mode) }
    }
}
