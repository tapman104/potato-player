package com.potato.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val SUB_SCALE    = doublePreferencesKey("sub_scale")
        val SUB_POS      = intPreferencesKey("sub_pos")
        val AUTO_ROTATION = booleanPreferencesKey("auto_rotation")
        val PREFERRED_SUB_LANG = stringPreferencesKey("preferred_sub_lang")
        val DEFAULT_DECODER     = stringPreferencesKey("default_decoder")
        val DEFAULT_SPEED       = doublePreferencesKey("default_speed")
        val CONTROLS_HIDE_DELAY = intPreferencesKey("controls_hide_delay")
        val GESTURES_ENABLED    = booleanPreferencesKey("gestures_enabled")
        val LOCK_BUTTON_ENABLED = booleanPreferencesKey("lock_button_enabled")

        const val DEFAULT_SUB_SCALE = 1.0
        const val DEFAULT_SUB_POS = 100
        const val DEFAULT_DECODER_VALUE      = "mediacodec-copy"   // HW+
        const val DEFAULT_SPEED_VALUE        = 1.0
        const val DEFAULT_HIDE_DELAY_MS      = 3000                // 3 seconds
        const val DEFAULT_GESTURES_ENABLED   = true
        const val DEFAULT_LOCK_BUTTON        = true
    }

    val subScaleFlow: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[SUB_SCALE] ?: 1.0
    }

    val subPosFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SUB_POS] ?: 100
    }

    val autoRotationFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_ROTATION] ?: false
    }

    val preferredSubLangFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PREFERRED_SUB_LANG] ?: "eng"
    }

    val defaultDecoderFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_DECODER] ?: DEFAULT_DECODER_VALUE
    }

    val defaultSpeedFlow: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_SPEED] ?: DEFAULT_SPEED_VALUE
    }

    val controlsHideDelayFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CONTROLS_HIDE_DELAY] ?: DEFAULT_HIDE_DELAY_MS
    }

    val gesturesEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[GESTURES_ENABLED] ?: DEFAULT_GESTURES_ENABLED
    }

    val lockButtonEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LOCK_BUTTON_ENABLED] ?: DEFAULT_LOCK_BUTTON
    }

    suspend fun setSubScale(scale: Double) {
        context.dataStore.edit { preferences ->
            preferences[SUB_SCALE] = scale
        }
    }

    suspend fun setSubPos(pos: Int) {
        context.dataStore.edit { preferences ->
            preferences[SUB_POS] = pos
        }
    }

    suspend fun saveSubtitleAppearance(scale: Double, pos: Int) {
        context.dataStore.edit { preferences ->
            preferences[SUB_SCALE] = scale
            preferences[SUB_POS] = pos
        }
    }

    suspend fun setAutoRotation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_ROTATION] = enabled
        }
    }

    suspend fun setPreferredSubLang(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[PREFERRED_SUB_LANG] = lang
        }
    }

    suspend fun setDefaultDecoder(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_DECODER] = mode
        }
    }

    suspend fun setDefaultSpeed(speed: Double) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_SPEED] = speed
        }
    }

    suspend fun setControlsHideDelay(delayMs: Int) {
        context.dataStore.edit { preferences ->
            preferences[CONTROLS_HIDE_DELAY] = delayMs
        }
    }

    suspend fun setGesturesEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[GESTURES_ENABLED] = enabled
        }
    }

    suspend fun setLockButtonEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LOCK_BUTTON_ENABLED] = enabled
        }
    }
}
