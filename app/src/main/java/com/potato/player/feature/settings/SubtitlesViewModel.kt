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

data class SubtitlesUiState(
    val preferredSubLang: String = "eng",
    val subScale: Double         = UserPreferencesRepository.DEFAULT_SUB_SCALE,
    val subPos: Int              = UserPreferencesRepository.DEFAULT_SUB_POS
)

@HiltViewModel
class SubtitlesViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<SubtitlesUiState> = combine(
        prefsRepository.preferredSubLangFlow,
        prefsRepository.subScaleFlow,
        prefsRepository.subPosFlow
    ) { lang, scale, pos ->
        SubtitlesUiState(
            preferredSubLang = lang,
            subScale         = scale,
            subPos           = pos
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SubtitlesUiState()
    )

    fun setPreferredSubLang(code: String) {
        viewModelScope.launch { prefsRepository.setPreferredSubLang(code) }
    }
}
