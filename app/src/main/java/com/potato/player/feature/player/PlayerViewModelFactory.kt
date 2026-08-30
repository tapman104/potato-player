package com.potato.player.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.potato.player.engine.MpvWrapper
import com.potato.player.data.VideoHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PlayerViewModelFactory(
    private val appContext: Context,
    private val wrapper: MpvWrapper,
    private val historyRepository: VideoHistoryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val historyManager = PlaybackHistoryManager(historyRepository, saveScope)
        @Suppress("UNCHECKED_CAST")
        return PlayerViewModel(appContext, wrapper, historyManager) as T
    }
}
