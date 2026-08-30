package com.potato.player.feature.player

import android.content.Context
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.engine.TrackInfo
import com.potato.player.engine.TrackType
import com.potato.player.feature.player.state.TrackUiModel
import com.potato.player.feature.player.state.toUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class TrackState(
    val audioTracks: List<TrackUiModel> = emptyList(),
    val subtitleTracks: List<TrackUiModel> = emptyList(),
    val currentAudioTrackId: Int = -1,
    val currentSubtitleTrackId: Int = -1
)

class TrackManager(
    prefsRepository: UserPreferencesRepository,
    scope: CoroutineScope
) {
    private val _trackState = MutableStateFlow(TrackState())
    val trackState: StateFlow<TrackState> = _trackState.asStateFlow()

    private val preferredSubLangState = prefsRepository.preferredSubLangFlow
        .stateIn(scope, SharingStarted.Eagerly, "eng")
    
    private var autoSubApplied = false

    fun loadTracks(tracks: List<TrackInfo>, currentAudioId: Int, currentSubtitleId: Int, context: Context) {
        val audioTracks = tracks.filter { it.type == TrackType.AUDIO }.map { it.toUiModel(context) }
        val subtitleTracks = tracks.filter { it.type == TrackType.SUBTITLE }.map { it.toUiModel(context) }
        _trackState.update { 
            it.copy(
                audioTracks = audioTracks, 
                subtitleTracks = subtitleTracks, 
                currentAudioTrackId = currentAudioId, 
                currentSubtitleTrackId = currentSubtitleId
            ) 
        }
    }

    fun getPreferredSubtitleTrackId(): Int? {
        if (autoSubApplied || preferredSubLangState.value == "off") return null
        val currentTracks = _trackState.value.subtitleTracks
        if (currentTracks.isEmpty()) return null

        val prefLang = preferredSubLangState.value
        val acceptedLangs = LANG_ALIASES[prefLang.lowercase()] ?: setOf(prefLang.lowercase())

        var match = currentTracks.find { track ->
            track.language.lowercase() in acceptedLangs
        }

        if (match == null && ("en" in acceptedLangs || "eng" in acceptedLangs)) {
            match = currentTracks.find { it.title.contains("english", ignoreCase = true) }
        }

        return match?.id
    }

    fun markAutoSubApplied() {
        autoSubApplied = true
    }
    
    fun resetAutoSubApplied() {
        autoSubApplied = false
    }

    fun setAudioTrack(id: Int) {
        _trackState.update { it.copy(currentAudioTrackId = id) }
    }

    fun setSubtitleTrack(id: Int) {
        _trackState.update { it.copy(currentSubtitleTrackId = id) }
    }

    companion object {
        private val LANG_ALIASES = mapOf(
            "eng" to setOf("eng", "en"),
            "en"  to setOf("eng", "en"),
            "jpn" to setOf("jpn", "ja"),
            "ja"  to setOf("jpn", "ja"),
            "kor" to setOf("kor", "ko"),
            "ko"  to setOf("kor", "ko")
        )
    }
}
