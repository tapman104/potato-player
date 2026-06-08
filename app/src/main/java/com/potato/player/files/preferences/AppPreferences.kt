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
    private val positionPrefs: SharedPreferences = context.getSharedPreferences("playback_positions", Context.MODE_PRIVATE)

    private val _showVideoFiles = MutableStateFlow(prefs.getBoolean("show_video_files", true))
    val showVideoFiles: StateFlow<Boolean> = _showVideoFiles.asStateFlow()

    fun setShowVideoFiles(enabled: Boolean) {
        prefs.edit().putBoolean("show_video_files", enabled).apply()
        _showVideoFiles.value = enabled
    }

    private val KEY_SAVED_VOLUME = "saved_volume"
    private val KEY_SAVED_BRIGHTNESS = "saved_brightness"

    fun saveVolume(fraction: Float) {
        prefs.edit().putFloat(KEY_SAVED_VOLUME, fraction).apply()
    }

    fun getSavedVolume(): Float {
        return prefs.getFloat(KEY_SAVED_VOLUME, -1f)
    }

    fun saveBrightness(fraction: Float) {
        prefs.edit().putFloat(KEY_SAVED_BRIGHTNESS, fraction).apply()
    }

    fun getSavedBrightness(): Float {
        return prefs.getFloat(KEY_SAVED_BRIGHTNESS, -1f)
    }

    private val _resumePlayback = MutableStateFlow(prefs.getBoolean("resume_playback", true))
    val resumePlayback: StateFlow<Boolean> = _resumePlayback.asStateFlow()

    private val _defaultPlaybackSpeed = MutableStateFlow(prefs.getFloat("default_playback_speed", 1.0f))
    val defaultPlaybackSpeed: StateFlow<Float> = _defaultPlaybackSpeed.asStateFlow()

    private val _backgroundPlayback = MutableStateFlow(prefs.getBoolean("background_playback", false))
    val backgroundPlayback: StateFlow<Boolean> = _backgroundPlayback.asStateFlow()

    private val _autoPlayNext = MutableStateFlow(prefs.getBoolean("auto_play_next", false))
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext.asStateFlow()

    private val _doubleTapToSeek = MutableStateFlow(prefs.getBoolean("double_tap_to_seek", true))
    val doubleTapToSeek: StateFlow<Boolean> = _doubleTapToSeek.asStateFlow()

    private val _longPressForSpeed = MutableStateFlow(prefs.getBoolean("long_press_for_speed", true))
    val longPressForSpeed: StateFlow<Boolean> = _longPressForSpeed.asStateFlow()

    private val _swipeForVolume = MutableStateFlow(prefs.getBoolean("swipe_for_volume", true))
    val swipeForVolume: StateFlow<Boolean> = _swipeForVolume.asStateFlow()

    private val _enablePlaybackService = MutableStateFlow(prefs.getBoolean("enable_playback_service", true))
    val enablePlaybackService: StateFlow<Boolean> = _enablePlaybackService.asStateFlow()


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

    private val _defaultOrientation = MutableStateFlow(
        prefs.getString("default_orientation", "AUTO") ?: "AUTO"
    )
    val defaultOrientation: StateFlow<String> = _defaultOrientation.asStateFlow()

    fun setDefaultOrientation(modeName: String) {
        prefs.edit().putString("default_orientation", modeName).apply()
        _defaultOrientation.value = modeName
    }

    fun setResumePlayback(enabled: Boolean) {
        prefs.edit().putBoolean("resume_playback", enabled).apply()
        _resumePlayback.value = enabled
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat("default_playback_speed", speed).apply()
        _defaultPlaybackSpeed.value = speed
    }

    fun setBackgroundPlayback(enabled: Boolean) {
        prefs.edit().putBoolean("background_playback", enabled).apply()
        _backgroundPlayback.value = enabled
    }

    fun setAutoPlayNext(enabled: Boolean) {
        prefs.edit().putBoolean("auto_play_next", enabled).apply()
        _autoPlayNext.value = enabled
    }

    fun setDoubleTapToSeek(enabled: Boolean) {
        prefs.edit().putBoolean("double_tap_to_seek", enabled).apply()
        _doubleTapToSeek.value = enabled
    }

    fun setLongPressForSpeed(enabled: Boolean) {
        prefs.edit().putBoolean("long_press_for_speed", enabled).apply()
        _longPressForSpeed.value = enabled
    }

    fun setSwipeForVolume(enabled: Boolean) {
        prefs.edit().putBoolean("swipe_for_volume", enabled).apply()
        _swipeForVolume.value = enabled
    }

    fun setEnablePlaybackService(enabled: Boolean) {
        prefs.edit().putBoolean("enable_playback_service", enabled).apply()
        _enablePlaybackService.value = enabled
    }

    fun getPlaybackPosition(uri: String): Long {
        return positionPrefs.getLong(uri, 0L)
    }

    fun savePlaybackPosition(uri: String, position: Long) {
        if (position > 0) {
            positionPrefs.edit().putLong(uri, position).apply()
        } else {
            positionPrefs.edit().remove(uri).apply()
        }
    }

    fun getAllSavedPositionUris(): Set<String> {
        return positionPrefs.all.keys
    }
}
