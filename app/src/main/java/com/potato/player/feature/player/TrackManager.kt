package com.potato.player.feature.player

import android.content.Context
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.engine.TrackInfo
import com.potato.player.engine.TrackType
import com.potato.player.engine.MpvWrapper
import com.potato.player.engine.MpvProp
import com.potato.player.engine.TrackListParser
import com.potato.player.util.MediaMetadataRepository
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
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
    private val scope: CoroutineScope
) {
    private val _trackState = MutableStateFlow(TrackState())
    val trackState: StateFlow<TrackState> = _trackState.asStateFlow()

    private val preferredSubLangState = prefsRepository.preferredSubLangFlow
        .stateIn(scope, SharingStarted.Eagerly, "eng")
    
    private var autoSubApplied = false

    fun loadTracks(wrapper: MpvWrapper, context: Context) {
        scope.launch(Dispatchers.IO) {
            val count = wrapper.getPropertyInt(MpvProp.TRACK_LIST_COUNT) ?: 0
            val list = mutableListOf<TrackInfo>()
            for (i in 0 until count) {
                val trackType = when (wrapper.getPropertyString("track-list/$i/${MpvProp.TRACK_KEY_TYPE}")) {
                    "audio" -> TrackType.AUDIO
                    "sub"   -> TrackType.SUBTITLE
                    else    -> continue
                }
                val id = wrapper.getPropertyInt("track-list/$i/${MpvProp.TRACK_KEY_ID}") ?: continue
                val title = wrapper.getPropertyString("track-list/$i/${MpvProp.TRACK_KEY_TITLE}")
                val lang = wrapper.getPropertyString("track-list/$i/${MpvProp.TRACK_KEY_LANG}")
                val extStr = wrapper.getPropertyString("track-list/$i/${MpvProp.TRACK_KEY_EXTERNAL}")
                list.add(TrackInfo(id = id, type = trackType, title = title, lang = lang, isExternal = extStr == "yes" || extStr == "true"))
            }
            val aid = wrapper.getPropertyString(MpvProp.AID)?.toIntOrNull() ?: -1
            val sid = wrapper.getPropertyString(MpvProp.SID)?.toIntOrNull() ?: -1
            withContext(Dispatchers.Main) {
                val audioTracks = list.filter { it.type == TrackType.AUDIO }.map { it.toUiModel(context) }
                val subtitleTracks = list.filter { it.type == TrackType.SUBTITLE }.map { it.toUiModel(context) }
                _trackState.update { 
                    it.copy(
                        audioTracks = audioTracks, 
                        subtitleTracks = subtitleTracks, 
                        currentAudioTrackId = aid, 
                        currentSubtitleTrackId = sid
                    ) 
                }
            }
        }
    }

    /**
     * Parse the track list from the JSON string already delivered by MPV's
     * property observer (engineState.trackListJson). This avoids the N×5
     * individual JNI calls that [loadTracks] makes.
     *
     * On [TrackListParser.Result.Failure] the existing track state is preserved.
     * Aid/sid are still queried individually because they are not in the JSON.
     */
    fun loadTracksFromJson(json: String, wrapper: MpvWrapper, context: Context) {
        scope.launch(Dispatchers.IO) {
            val result = TrackListParser.parse(json)
            if (result is TrackListParser.Result.Failure) return@launch
            val list = (result as TrackListParser.Result.Success).tracks
            val aid = wrapper.getPropertyString(MpvProp.AID)?.toIntOrNull() ?: -1
            val sid = wrapper.getPropertyString(MpvProp.SID)?.toIntOrNull() ?: -1
            withContext(Dispatchers.Main) {
                val audioTracks    = list.filter { it.type == TrackType.AUDIO    }.map { it.toUiModel(context) }
                val subtitleTracks = list.filter { it.type == TrackType.SUBTITLE }.map { it.toUiModel(context) }
                _trackState.update {
                    it.copy(
                        audioTracks          = audioTracks,
                        subtitleTracks       = subtitleTracks,
                        currentAudioTrackId  = aid,
                        currentSubtitleTrackId = sid
                    )
                }
            }
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

    fun applyPreferred(setTrack: (Int) -> Unit) {
        val matchId = getPreferredSubtitleTrackId()
        if (matchId != null) {
            val sid = _trackState.value.currentSubtitleTrackId
            if (sid != matchId) {
                setTrack(matchId)
                selectSubtitle(matchId)
            }
            markAutoSubApplied()
        }
    }

    fun selectAudio(id: Int) {
        _trackState.update { it.copy(currentAudioTrackId = id) }
    }

    fun selectSubtitle(id: Int) {
        _trackState.update { it.copy(currentSubtitleTrackId = id) }
    }

    fun loadExternal(uri: Uri, context: Context, wrapper: MpvWrapper) {
        scope.launch(Dispatchers.IO) {
            val path = MediaMetadataRepository.resolveSubtitlePath(context, uri) ?: uri.toString()
            wrapper.addExternalSubtitle(path)
            loadTracks(wrapper, context)
        }
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
