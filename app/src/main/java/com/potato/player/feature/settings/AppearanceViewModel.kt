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

data class AppearanceUiState(
    val themeMode: String = UserPreferencesRepository.DEFAULT_THEME_MODE
)

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<AppearanceUiState> = prefsRepository.themeModeFlow
        .map { themeMode -> AppearanceUiState(themeMode = themeMode) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppearanceUiState()
        )

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefsRepository.setThemeMode(mode) }
    }
}
