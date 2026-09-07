package com.potato.player.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.AppDatabase
import com.potato.player.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdvancedUiState(
    val recentlyPlayedEnabled: Boolean = true,
    val verboseLoggingEnabled: Boolean = false,
    val logFile: java.io.File? = null,  // set after dump, consumed by screen
    val logError: String? = null         // set if dump fails
)

@HiltViewModel
class AdvancedViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _logState = MutableStateFlow(AdvancedUiState())

    val recentlyPlayedEnabled: StateFlow<Boolean> =
        prefsRepository.recentlyPlayedEnabledFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferencesRepository.DEFAULT_RECENTLY_PLAYED_ENABLED
        )

    val verboseLoggingEnabled: StateFlow<Boolean> =
        prefsRepository.verboseLoggingEnabledFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferencesRepository.DEFAULT_VERBOSE_LOGGING_ENABLED
        )

    val logState: StateFlow<AdvancedUiState> = _logState.asStateFlow()

    fun setRecentlyPlayedEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setRecentlyPlayedEnabled(enabled) }
    }

    fun setVerboseLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setVerboseLoggingEnabled(enabled) }
    }

    /**
     * Capture logcat output filtered to com.potato.player / potato tags,
     * write to cacheDir/potato_debug_logs.txt, then surface the File via logState.
     */
    fun dumpLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-d", "-t", "500", "*:V")
                )
                val output = process.inputStream.bufferedReader().readText()
                val filtered = output.lines()
                    .filter { it.contains("com.potato.player") || it.contains("potato") }
                    .joinToString("\n")
                val file = java.io.File(context.cacheDir, "potato_debug_logs.txt")
                file.writeText(filtered)
                _logState.update { it.copy(logFile = file, logError = null) }
            } catch (e: Exception) {
                _logState.update { it.copy(logFile = null, logError = e.message) }
            }
        }
    }

    /** Call after the screen has consumed the log file intent to reset the trigger. */
    fun clearLogFile() {
        _logState.update { it.copy(logFile = null, logError = null) }
    }

    /**
     * Permanently delete all playback history entries from the Room database.
     */
    fun clearPlaybackHistory(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(context).videoHistoryDao().deleteAll()
            launch(Dispatchers.Main) { onComplete() }
        }
    }
}
