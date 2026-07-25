package com.potato.player.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.potato.player.engine.MpvWrapper
import com.potato.player.data.VideoHistoryRepository

class PlayerViewModelFactory(
    private val appContext: Context,
    private val wrapper: MpvWrapper,
    private val historyRepository: VideoHistoryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PlayerViewModel(appContext, wrapper, historyRepository) as T
    }
}
