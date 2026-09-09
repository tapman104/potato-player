package com.potato.player.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.BuildConfig
import com.potato.player.data.LogRepository
import com.potato.player.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AboutUiState(
    val appVersion: String = "",
    val buildType: String = "",       // "release" or "debug"
    val androidVersion: String = "",  // Build.VERSION.RELEASE
    val apiLevel: Int = 0,            // Build.VERSION.SDK_INT
    val manufacturer: String = "",    // Build.MANUFACTURER
    val model: String = "",           // Build.MODEL
    val device: String = "",          // Build.DEVICE
    val logFile: java.io.File? = null, // set after dump, consumed by screen
    val logError: String? = null       // set if dump fails
)

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val logRepository: LogRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    /** Mirrors the verbose-logging pref so dumpLogs() uses the correct logcat args. */
    private val verboseLoggingEnabled: StateFlow<Boolean> =
        prefsRepository.verboseLoggingEnabledFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserPreferencesRepository.DEFAULT_VERBOSE_LOGGING_ENABLED
        )

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

    /**
     * Capture logcat output via [LogRepository], respecting the current verbose-logging
     * preference, then surface the resulting [File] (or any error) via [uiState].
     */
    fun dumpLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = logRepository.dumpLogs(verboseLoggingEnabled.value)
                _uiState.update { it.copy(logFile = file, logError = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(logFile = null, logError = e.message) }
            }
        }
    }

    /** Call after the screen has consumed the log file intent to reset the trigger. */
    fun clearLogFile() {
        _uiState.update { it.copy(logFile = null, logError = null) }
    }
}
