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
    val videoOrientation: String = UserPreferencesRepository.DEFAULT_VIDEO_ORIENTATION
)

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<AppearanceUiState> = prefsRepository.videoOrientationFlow
        .map { orientation -> AppearanceUiState(videoOrientation = orientation) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppearanceUiState()
        )

    fun setVideoOrientation(mode: String) {
        viewModelScope.launch { prefsRepository.setVideoOrientation(mode) }
    }
}
