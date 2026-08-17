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
    val preferredSubLang: String = "eng",
    val appVersion: String = ""
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
        _appVersion
    ) { preferredSubLang, appVersion ->
        SettingsUiState(
            preferredSubLang = preferredSubLang,
            appVersion = appVersion
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
}
