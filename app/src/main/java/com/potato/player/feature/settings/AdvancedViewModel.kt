package com.potato.player.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.AppDatabase
import com.potato.player.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class AdvancedViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

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

    fun setRecentlyPlayedEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setRecentlyPlayedEnabled(enabled) }
    }

    fun setVerboseLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setVerboseLoggingEnabled(enabled) }
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
