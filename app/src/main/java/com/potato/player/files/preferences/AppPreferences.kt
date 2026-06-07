package com.potato.player.files.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.ui.SubtitleView
import com.potato.player.player.ui.subtitle.SubtitleSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _subtitleSettings = MutableStateFlow(
        SubtitleSettings(
            sizeFraction = prefs.getFloat("subtitle_size_fraction", SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 0.90f),
            bottomPaddingFraction = prefs.getFloat("subtitle_bottom_padding", 0.12f)
        )
    )

    fun getSubtitleSettings(): Flow<SubtitleSettings> = _subtitleSettings.asStateFlow()

    suspend fun saveSubtitleSettings(settings: SubtitleSettings) {
        prefs.edit()
            .putFloat("subtitle_size_fraction", settings.sizeFraction)
            .putFloat("subtitle_bottom_padding", settings.bottomPaddingFraction)
            .apply()
        _subtitleSettings.value = settings
    }

    private val _themeSelection = MutableStateFlow(prefs.getInt("theme_selection", 2)) // 0: Light, 1: Dark, 2: System
    val themeSelection: StateFlow<Int> = _themeSelection.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean("dynamic_color", true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setThemeSelection(selection: Int) {
        prefs.edit().putInt("theme_selection", selection).apply()
        _themeSelection.value = selection
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
        _dynamicColor.value = enabled
    }
}
