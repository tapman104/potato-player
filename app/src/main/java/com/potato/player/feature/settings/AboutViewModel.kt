package com.potato.player.feature.settings

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    @ApplicationContext private val context: Context
) : ViewModel() {

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

    /**
     * Capture logcat output filtered to com.potato.player / potato tags,
     * write to cacheDir/potato_debug_logs.txt, then surface the File via uiState.
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
