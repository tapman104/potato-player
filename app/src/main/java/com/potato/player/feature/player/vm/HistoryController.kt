package com.potato.player.feature.player.vm

import com.potato.player.data.VideoHistory
import com.potato.player.data.VideoHistoryRepository
import com.potato.player.feature.player.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryController(
    private val historyRepository: VideoHistoryRepository,
    private val uiState: StateFlow<PlayerUiState>,
    private val scope: CoroutineScope
) {
    fun saveHistoryIfNeeded(currentUri: String, currentTitle: String) {
        if (currentUri.isEmpty() || uiState.value.progressState.durationSec <= 0.0) return
        
        val currentPos = uiState.value.progressState.positionSec
        val duration = uiState.value.progressState.durationSec
        
        val posToSave = if (currentPos < 1.0 && duration > 0.0 && !uiState.value.isPlaying) duration else currentPos

        val entry = VideoHistory(
            uri = currentUri,
            title = currentTitle.ifEmpty { currentUri.substringAfterLast('/') },
            lastPlayedPositionSec = posToSave,
            durationSec = duration,
            lastAudioTrackId = uiState.value.currentAudioTrackId,
            lastSubtitleTrackId = uiState.value.currentSubtitleTrackId,
            lastPlayedTimestamp = System.currentTimeMillis()
        )
        scope.launch(Dispatchers.IO) {
            historyRepository.upsert(entry)
        }
    }

    suspend fun resolveResumePosition(uri: String): Long {
        val history = historyRepository.getByUri(uri)
        return if (history != null && history.lastPlayedPositionSec > 0)
            (history.lastPlayedPositionSec * 1000).toLong() else 0L
    }
}
