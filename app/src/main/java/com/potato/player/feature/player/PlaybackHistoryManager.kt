package com.potato.player.feature.player

import com.potato.player.data.VideoHistory
import com.potato.player.data.VideoHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlaybackHistoryManager(
    private val historyRepository: VideoHistoryRepository,
    private val scope: CoroutineScope
) {
    fun save(
        uri: String, 
        title: String, 
        lastPlayedPositionSec: Double, 
        durationSec: Double,
        lastAudioTrackId: Int,
        lastSubtitleTrackId: Int
    ) {
        if (uri.isEmpty() || durationSec <= 0.0) return
        val entry = VideoHistory(
            uri = uri,
            title = title,
            lastPlayedPositionSec = lastPlayedPositionSec,
            durationSec = durationSec,
            lastAudioTrackId = lastAudioTrackId,
            lastSubtitleTrackId = lastSubtitleTrackId,
            lastPlayedTimestamp = System.currentTimeMillis()
        )
        // Use GlobalScope to ensure the save completes even if viewModelScope is cancelled
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) { 
            try {
                historyRepository.upsert(entry) 
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getByUri(uri: String): VideoHistory? {
        return historyRepository.getByUri(uri)
    }
}
