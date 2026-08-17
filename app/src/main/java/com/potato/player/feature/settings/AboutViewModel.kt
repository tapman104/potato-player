package com.potato.player.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import com.potato.player.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class AboutUiState(
    val appVersion: String = "",
    val buildType: String = "",       // "release" or "debug"
    val androidVersion: String = "",  // Build.VERSION.RELEASE
    val apiLevel: Int = 0,            // Build.VERSION.SDK_INT
    val manufacturer: String = "",    // Build.MANUFACTURER
    val model: String = "",           // Build.MODEL
    val device: String = "",          // Build.DEVICE
)

@HiltViewModel
class AboutViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = AboutUiState(
            appVersion = BuildConfig.VERSION_NAME,
            buildType = BuildConfig.BUILD_TYPE,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE
        )
    }
}
