package com.potato.player.feature.player.vm

import android.content.Context
import android.net.Uri
import com.potato.player.engine.MpvProp
import com.potato.player.engine.MpvWrapper
import com.potato.player.engine.TrackInfo
import com.potato.player.engine.TrackType
import com.potato.player.feature.player.PlayerUiState
import com.potato.player.feature.player.toUiModel
import com.potato.player.util.MediaMetadataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class TrackController(
    private val wrapper: MpvWrapper,
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val appContext: Context,
    private val isActive: AtomicBoolean,
    private val preferredSubLangState: StateFlow<String>,
    private val scope: CoroutineScope
) {
    var autoSubApplied: Boolean = false

    fun loadTracks() {
        val count = wrapper.getPropertyInt(MpvProp.TRACK_LIST_COUNT) ?: 0
        val list = mutableListOf<TrackInfo>()
        for (i in 0 until count) {
            val trackType = when (wrapper.getPropertyString("track-list/$i/${MpvProp.PROP_TRACK_LIST_TYPE}")) {
                "audio" -> TrackType.AUDIO
                "sub"   -> TrackType.SUBTITLE
                else    -> continue
            }
            val id = wrapper.getPropertyInt("track-list/$i/${MpvProp.PROP_TRACK_LIST_ID}") ?: continue
            val title = wrapper.getPropertyString("track-list/$i/${MpvProp.PROP_TRACK_LIST_TITLE}")
            val lang = wrapper.getPropertyString("track-list/$i/${MpvProp.PROP_TRACK_LIST_LANG}")
            val extStr = wrapper.getPropertyString("track-list/$i/${MpvProp.PROP_TRACK_LIST_EXTERNAL}")
            list.add(TrackInfo(id = id, type = trackType, title = title, lang = lang, isExternal = extStr == "yes" || extStr == "true"))
        }
        val aid = wrapper.getPropertyString(MpvProp.AID)?.toIntOrNull() ?: -1
        val sid = wrapper.getPropertyString(MpvProp.SID)?.toIntOrNull() ?: -1
        val audioTracks = list.filter { it.type == TrackType.AUDIO }.map { it.toUiModel(appContext) }
        val subtitleTracks = list.filter { it.type == TrackType.SUBTITLE }.map { it.toUiModel(appContext) }
        uiState.update { it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks, currentAudioTrackId = aid, currentSubtitleTrackId = sid) }
    }

    fun applyPreferredSubtitleTrack() {
        if (autoSubApplied || preferredSubLangState.value == "off") return
        val currentTracks = uiState.value.subtitleTracks
        if (currentTracks.isEmpty()) return

        val prefLang = preferredSubLangState.value
        val langAliases = mapOf(
            "eng" to setOf("eng", "en"),
            "en"  to setOf("eng", "en"),
            "jpn" to setOf("jpn", "ja"),
            "ja"  to setOf("jpn", "ja"),
            "kor" to setOf("kor", "ko"),
            "ko"  to setOf("kor", "ko")
        )

        val acceptedLangs = langAliases[prefLang.lowercase()] ?: setOf(prefLang.lowercase())

        var match = currentTracks.find { track ->
            track.language.lowercase() in acceptedLangs
        }

        if (match == null && ("en" in acceptedLangs || "eng" in acceptedLangs)) {
            match = currentTracks.find { it.title.contains("english", ignoreCase = true) }
        }

        if (match != null) {
            val sid = uiState.value.currentSubtitleTrackId
            if (sid != match.id) {
                wrapper.setSubTrack(match.id)
                uiState.update { it.copy(currentSubtitleTrackId = match.id) }
            }
            autoSubApplied = true
        }
    }

    fun onSelectAudioTrack(id: Int, dismissDialog: () -> Unit) {
        if (!isActive.get()) return
        wrapper.setAudioTrack(id)
        uiState.update { it.copy(currentAudioTrackId = id) }
        dismissDialog()
    }

    fun onSelectSubtitleTrack(id: Int, dismissDialog: () -> Unit) {
        if (!isActive.get()) return
        wrapper.setSubTrack(id)
        uiState.update { it.copy(currentSubtitleTrackId = id) }
        dismissDialog()
    }

    fun onLoadExternalSubtitle(uri: Uri, context: Context, dismissDialog: () -> Unit) {
        if (!isActive.get()) return
        scope.launch(Dispatchers.IO) {
            val path = MediaMetadataRepository.resolveSubtitlePath(context, uri) ?: uri.toString()
            wrapper.addExternalSubtitle(path)
            loadTracks()
        }
        dismissDialog()
    }
}
