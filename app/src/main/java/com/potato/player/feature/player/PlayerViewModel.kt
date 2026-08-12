package com.potato.player.feature.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.data.VideoHistory
import com.potato.player.data.VideoHistoryRepository
import com.potato.player.engine.MpvWrapper
import com.potato.player.engine.MpvEvent
import com.potato.player.engine.MpvEventId
import com.potato.player.engine.MpvProp
import com.potato.player.engine.TrackInfo
import com.potato.player.engine.TrackListParser
import com.potato.player.engine.TrackType

import com.potato.player.feature.player.toUiModel
import com.potato.player.util.MediaMetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.SurfaceHolder

enum class VideoFitMode { FIT, FILL, STRETCH }

class PlayerViewModel(
    private val appContext: Context,
    private val wrapper: MpvWrapper,
    private val historyRepository: VideoHistoryRepository
) : ViewModel() {

    private val prefsRepository by lazy { UserPreferencesRepository(appContext) }
    
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val isActive = java.util.concurrent.atomic.AtomicBoolean(true)

    private var lastSeekTime = 0L
    private var pendingSeekPosition: Long = 0L

    private var wasPlayingBeforePause: Boolean = false

    private var currentUri = ""
    private var currentTitle = ""
    
    private var normalPlaybackSpeed = 1.0
    private var isSliderSeeking = false
    private var autoSubApplied = false

    private val preferredSubLangState = prefsRepository.preferredSubLangFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "eng")

    private val eventProcessor = MpvEventProcessor(
        onPlaybackStarted = { _uiState.update { it.copy(isLoading = false, isPlaying = true) } },
        onPlaybackPaused = { _uiState.update { it.copy(isPlaying = false) } },
        onPlaybackRestart = {
            // Read the real MPV pause state instead of assuming playback is active.
            // PLAYBACK_RESTART fires on every seek (including seeks while paused), so
            // blindly setting isPlaying=true here would show the wrong icon.
            val isPaused = wrapper.getPropertyBoolean(MpvProp.PAUSE) ?: false
            _uiState.update { it.copy(isLoading = false, isPlaying = !isPaused) }
        },
        onDurationChanged = { ms -> _uiState.update { it.copy(progressState = it.progressState.copy(durationSec = ms / 1000.0)) } },
        onPositionChanged = { ms -> 
            if (!isSliderSeeking) _uiState.update { it.copy(progressState = it.progressState.copy(positionSec = ms / 1000.0)) } 
        },
        onTracksChanged = { json ->
            val tracks = TrackListParser.parse(json)
            if (tracks.isNotEmpty()) {
                val audioTracks = tracks.filter { it.type == TrackType.AUDIO }.map { it.toUiModel() }
                val subtitleTracks = tracks.filter { it.type == TrackType.SUBTITLE }.map { it.toUiModel() }
                _uiState.update { it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks) }
                viewModelScope.launch {
                    preferredSubLangState.first { true }
                    applyPreferredSubtitleTrack()
                }
            } else {
                loadTracks()
            }
        },
        onHwdecChanged = { value ->
            val hwdec = when {
                value == "no" || value.isEmpty() -> "SW"
                value.contains("copy") -> "HW+"
                else -> "HW"
            }
            _uiState.update { it.copy(hwdecCurrent = hwdec) }
        },
        onIdleEntered = { },
        onEndFileReached = {
            _uiState.update { it.copy(isPlaying = false) }
            saveHistoryIfNeeded()
            if (_uiState.value.isFastForwarding) {
                _uiState.update { it.copy(isFastForwarding = false) }
                wrapper.setSpeed(normalPlaybackSpeed)
            }
        },
        onFileLoaded = {
            val isPaused = wrapper.getPropertyBoolean(MpvProp.PAUSE) ?: false
            _uiState.update { it.copy(fileLoaded = true, isLoading = false, isPlaying = !isPaused, fitMode = VideoFitMode.FIT) }
            if (pendingSeekPosition > 0L) {
                wrapper.seekTo(pendingSeekPosition)
                pendingSeekPosition = 0L
            }
            loadTracks()
            viewModelScope.launch {
                // Wait until DataStore has emitted at least once before applying subtitle preference.
                // preferredSubLangState is Eagerly shared so first{true} returns immediately if the
                // upstream has already emitted, or suspends briefly until it does.
                preferredSubLangState.first { true }
                applyPreferredSubtitleTrack()
            }
        },
        onCacheTimeChanged = { sec -> _uiState.update { it.copy(progressState = it.progressState.copy(cachedSec = sec)) } },
        onCacheDurationChanged = { sec -> _uiState.update { it.copy(progressState = it.progressState.copy(cacheDurationSec = sec)) } },
        onSpeedChanged = { speed ->
            if (!_uiState.value.isFastForwarding) {
                _uiState.update { it.copy(playbackSpeed = speed) }
                normalPlaybackSpeed = speed
            }
        },
        onSubScaleChanged = { scale -> _uiState.update { it.copy(subScale = scale) } },
        onSubPosChanged = { pos -> _uiState.update { it.copy(subPos = pos) } },
        onVideoWidthChanged = { w -> _uiState.update { it.copy(videoWidth = w) } },
        onVideoHeightChanged = { h -> _uiState.update { it.copy(videoHeight = h) } },
        onVolumeChanged = { v -> _uiState.update { it.copy(volume = v) } }
    )

    init {
        viewModelScope.launch {
            wrapper.events.collect { event ->
                eventProcessor.process(event)
            }
        }

        viewModelScope.launch {
            combine(
                prefsRepository.subScaleFlow,
                prefsRepository.subPosFlow,
                prefsRepository.autoRotationFlow
            ) { scale, pos, autoRot ->
                Triple(scale, pos, autoRot)
            }.collect { (scale, pos, autoRot) ->
                wrapper.setSubScale(scale)
                wrapper.setSubPos(pos)
                _uiState.update { it.copy(subScale = scale, subPos = pos, isAutoRotation = autoRot) }
            }
        }
    }





    private fun loadTracks() {
        val count = wrapper.getPropertyInt(MpvProp.TRACK_LIST_COUNT) ?: 0
        val list = mutableListOf<TrackInfo>()
        for (i in 0 until count) {
            val trackType = when (wrapper.getPropertyString("track-list/$i/type")) {
                "audio" -> TrackType.AUDIO
                "sub"   -> TrackType.SUBTITLE
                else    -> continue
            }
            val id = wrapper.getPropertyInt("track-list/$i/id") ?: continue
            val title = wrapper.getPropertyString("track-list/$i/title")
            val lang = wrapper.getPropertyString("track-list/$i/lang")
            val extStr = wrapper.getPropertyString("track-list/$i/external")
            list.add(TrackInfo(id = id, type = trackType, title = title, lang = lang, isExternal = extStr == "yes" || extStr == "true"))
        }
        val aid = wrapper.getPropertyString(MpvProp.AID)?.toIntOrNull() ?: -1
        val sid = wrapper.getPropertyString(MpvProp.SID)?.toIntOrNull() ?: -1
        val audioTracks = list.filter { it.type == TrackType.AUDIO }.map { it.toUiModel() }
        val subtitleTracks = list.filter { it.type == TrackType.SUBTITLE }.map { it.toUiModel() }
        _uiState.update { it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks, currentAudioTrackId = aid, currentSubtitleTrackId = sid) }
    }

    private fun applyPreferredSubtitleTrack() {
        if (autoSubApplied || preferredSubLangState.value == "off") return
        val currentTracks = _uiState.value.subtitleTracks
        if (currentTracks.isEmpty()) return

        val prefLang = preferredSubLangState.value

        // Map both ISO 639-1 and ISO 639-2 codes for each supported language
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

        // Fallback: title contains language name (English only)
        if (match == null && ("en" in acceptedLangs || "eng" in acceptedLangs)) {
            match = currentTracks.find { it.title.contains("english", ignoreCase = true) }
        }

        if (match != null) {
            val sid = _uiState.value.currentSubtitleTrackId
            if (sid != match.id) {
                wrapper.setSubTrack(match.id)
                _uiState.update { it.copy(currentSubtitleTrackId = match.id) }
            }
            autoSubApplied = true
        }
    }

    val surfaceCallback: SurfaceHolder.Callback get() = wrapper.surfaceCallback

    fun onSurfaceReady(defaultUri: String, defaultTitle: String = "") {
        // Do not guard on fileLoaded/isLoading here. This function is only ever called
        // when the surface becomes available for a specific URI. If we are already
        // loading or loaded, we should still start a new load for the given URI so that
        // a reopen after navigating back to the same ViewModel instance works correctly.
        viewModelScope.launch(Dispatchers.IO) {
            val history = historyRepository.getByUri(defaultUri)
            val resumePos = if (history != null && history.lastPlayedPositionSec > 0)
                (history.lastPlayedPositionSec * 1000).toLong() else 0L
            withContext(Dispatchers.Main) {
                executeLoadFile(defaultUri, defaultTitle, resumePos)
            }
        }
    }



    fun setSurfaceReadyCallback(cb: (() -> Unit)?) {
        if (!isActive.get()) return
        if (cb == null) {
            wrapper.onSurfaceReady = null
        } else {
            wrapper.onSurfaceReady = {
                cb.invoke()
            }
            // If the SurfaceView already exists (its surfaceCreated already fired), the
            // callback registered above will never be triggered by the surface lifecycle.
            // Fire it immediately so the new URI is loaded without needing a surface
            // recreate — this is the common case when the user reopens the player.
            if (wrapper.isSurfaceAttached) {
                cb.invoke()
            }
        }
    }

    fun onSurfaceDestroyed() {
        if (!isActive.get()) return
        wrapper.onSurfaceReady = null
    }

    fun loadFile(uri: String, title: String = "", resumePosition: Long = 0L) {
        if (!isActive.get()) return
        executeLoadFile(uri, title, resumePosition)
    }

    private fun executeLoadFile(uri: String, title: String, resumePosition: Long) {
        currentUri = uri
        currentTitle = title
        autoSubApplied = false
        val initialName = if (title.isNotBlank()) title else "Video"
        _uiState.update { it.copy(fileName = initialName, isLoading = true, isPlaying = false, fileLoaded = false, error = null) }
        if (title.isBlank()) {
            viewModelScope.launch {
                val resolvedName = MediaMetadataRepository.resolveFileName(appContext, uri)
                _uiState.update { it.copy(fileName = resolvedName) }
            }
        }
        pendingSeekPosition = resumePosition
        wrapper.play(uri)
    }

    fun togglePlay() {
        if (!isActive.get()) return
        wrapper.togglePlay()
    }
    
    fun pause() {
        if (!isActive.get()) return
        wrapper.pause()
    }

    fun onPlayerPause() {
        wasPlayingBeforePause = _uiState.value.isPlaying
        wrapper.pause()
        saveHistoryIfNeeded()
    }

    fun onPlayerResume() {
        if (wasPlayingBeforePause) {
            wrapper.resume()
        }
    }

    fun toggleLock() {
        _uiState.update { it.copy(isLocked = !it.isLocked) }
    }

    fun cycleOrientationMode() {
        val next = when (_uiState.value.orientationMode) {
            OrientationMode.AUTO -> OrientationMode.LOCK_LANDSCAPE
            OrientationMode.LOCK_LANDSCAPE -> OrientationMode.LOCK_PORTRAIT
            OrientationMode.LOCK_PORTRAIT -> OrientationMode.AUTO
        }
        _uiState.update { it.copy(orientationMode = next) }
    }

    fun toggleAutoRotation() {
        val next = !_uiState.value.isAutoRotation
        _uiState.update { it.copy(isAutoRotation = next) }
        viewModelScope.launch { prefsRepository.setAutoRotation(next) }
    }

    fun cycleFitMode() {
        if (!isActive.get()) return
        val next = when (_uiState.value.fitMode) {
            VideoFitMode.FIT -> VideoFitMode.FILL
            VideoFitMode.FILL -> VideoFitMode.STRETCH
            VideoFitMode.STRETCH -> VideoFitMode.FIT
        }
        _uiState.update { it.copy(fitMode = next) }
        when (next) {
            VideoFitMode.FIT -> {
                wrapper.setPropertyString("video-aspect-override", "-1")
                wrapper.setPropertyString("panscan", "0.0")
            }
            VideoFitMode.FILL -> {
                wrapper.setPropertyString("panscan", "1.0")
                wrapper.setPropertyString("video-aspect-override", "-1")
            }
            VideoFitMode.STRETCH -> {
                wrapper.setPropertyString("panscan", "0.0")
                val metrics = appContext.resources.displayMetrics
                val screenWidth = metrics.widthPixels
                val screenHeight = metrics.heightPixels
                wrapper.setPropertyString("video-aspect-override", "${screenWidth}/${screenHeight}")
            }
        }
    }

    fun setDecoder(mode: String) {
        if (!isActive.get()) return
        val hwdec = when (mode) { "no" -> "SW"; "mediacodec" -> "HW"; else -> "HW+" }
        _uiState.update { it.copy(hwdecCurrent = hwdec) }
        wrapper.setDecoder(mode)
    }

    fun seekRelative(offsetSec: Double) {
        if (!isActive.get()) return
        val target = (_uiState.value.progressState.positionSec + offsetSec).coerceIn(0.0, _uiState.value.progressState.durationSec.takeIf { it > 0.0 } ?: Double.MAX_VALUE)
        wrapper.seekTo((target * 1000).toLong())
    }

    fun seekExactRelative(offsetSec: Int) {
        if (!isActive.get()) return
        wrapper.seekRelative(offsetSec.toDouble())
    }

    fun startFastForward() {
        if (!isActive.get()) return
        if (!_uiState.value.isFastForwarding) {
            normalPlaybackSpeed = _uiState.value.playbackSpeed
            _uiState.update { it.copy(isFastForwarding = true) }
            wrapper.setSpeed(2.0)
        }
    }

    fun stopFastForward() {
        if (!isActive.get()) return
        if (_uiState.value.isFastForwarding) {
            _uiState.update { it.copy(isFastForwarding = false) }
            wrapper.setSpeed(normalPlaybackSpeed)
            _uiState.update { it.copy(playbackSpeed = normalPlaybackSpeed) }
        }
    }

    fun onSliderDragStart(posSec: Double) {
        isSliderSeeking = true
        _uiState.update { it.copy(progressState = it.progressState.copy(dragPositionSec = posSec)) }
    }

    fun onSliderDragChange(posSec: Double) {
        if (!isActive.get()) return
        _uiState.update { it.copy(progressState = it.progressState.copy(dragPositionSec = posSec)) }
        // The old code used seekGesture which just stored a value, but since MPV has its own thread and queue
        // we could just use absolute+exact if we want to seek during drag. Since we deleted the debouncer,
        // it's better not to spam it, but actually `seekTo` should be fine. However, old `seekGesture`
        // was non-blocking. MPV natively drops rapidly queued seeks anyway. Let's do `seekTo`.
        wrapper.seekTo((posSec * 1000).toLong())
    }

    fun onSliderDragEnd(posSec: Double) {
        if (!isActive.get()) return
        isSliderSeeking = false
        wrapper.seekTo((posSec * 1000).toLong())
        _uiState.update { it.copy(progressState = it.progressState.copy(dragPositionSec = null)) }
    }

    fun onSwipeSeek(positionSec: Double) {
        if (!isActive.get()) return
        _uiState.update { it.copy(swipeSeekTargetSec = positionSec) }
        if (System.currentTimeMillis() - lastSeekTime >= 100L) {
            wrapper.seekTo((positionSec * 1000).toLong())
            lastSeekTime = System.currentTimeMillis()
        }
    }

    fun onSwipeSeekFinished() {
        if (!isActive.get()) return
        val target = _uiState.value.swipeSeekTargetSec
        _uiState.update { it.copy(swipeSeekTargetSec = null) }
        if (target != null) {
            wrapper.seekTo((target * 1000).toLong())
            lastSeekTime = System.currentTimeMillis()
        }
    }

    fun setSwipingVolumeOrBrightness(isSwiping: Boolean) {
        _uiState.update { it.copy(isSwipingVolumeOrBrightness = isSwiping) }
    }

    fun setPlaybackSpeed(speed: Double) {
        if (!isActive.get()) return
        val clamped = speed.coerceIn(0.25, 4.0)
        normalPlaybackSpeed = clamped
        if (!_uiState.value.isFastForwarding) {
            wrapper.setSpeed(clamped)
        }
    }

    fun onSelectAudioTrack(id: Int) {
        if (!isActive.get()) return
        wrapper.setAudioTrack(id)
        _uiState.update { it.copy(currentAudioTrackId = id) }
        dismissDialog()
    }

    fun onSelectSubtitleTrack(id: Int) {
        if (!isActive.get()) return
        wrapper.setSubTrack(id)
        _uiState.update { it.copy(currentSubtitleTrackId = id) }
        dismissDialog()
    }

    fun onLoadExternalSubtitle(uri: Uri, context: Context) {
        if (!isActive.get()) return
        viewModelScope.launch(Dispatchers.IO) {
            val path = MediaMetadataRepository.resolveSubtitlePath(context, uri) ?: uri.toString()
            wrapper.addExternalSubtitle(path)
            // Reload tracks to reflect new subtitle
            loadTracks()
        }
        dismissDialog()
    }

    fun setSubScale(scale: Double) { 
        if (!isActive.get()) return
        wrapper.setSubScale(scale) 
    }
    fun setSubPos(pos: Int) { 
        if (!isActive.get()) return
        wrapper.setSubPos(pos) 
    }

    fun setSubtitleAppearance(scale: Double, pos: Int) {
        if (!isActive.get()) return
        wrapper.setSubScale(scale)
        wrapper.setSubPos(pos)
        viewModelScope.launch {
            prefsRepository.setSubScale(scale)
            prefsRepository.setSubPos(pos)
        }
    }

    fun resetSubtitleAppearance() {
        if (!isActive.get()) return
        _uiState.update { it.copy(subScale = 1.0, subPos = 100) }
        wrapper.setSubScale(1.0)
        wrapper.setSubPos(100)
        viewModelScope.launch {
            prefsRepository.setSubScale(1.0)
            prefsRepository.setSubPos(100)
        }
    }

    fun showDialog(dialog: ActiveDialog) {
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(activeDialog = ActiveDialog.None) }
    }

    private fun saveHistoryIfNeeded() {
        if (currentUri.isEmpty() || _uiState.value.progressState.durationSec <= 0.0) return
        
        val currentPos = _uiState.value.progressState.positionSec
        val duration = _uiState.value.progressState.durationSec
        
        // If at the end of the file, the player might reset position to 0. 
        // We fallback to duration if it's near zero and not playing.
        val posToSave = if (currentPos < 1.0 && duration > 0.0 && !_uiState.value.isPlaying) duration else currentPos

        val entry = VideoHistory(
            uri = currentUri,
            title = currentTitle.ifEmpty { currentUri.substringAfterLast('/') },
            lastPlayedPositionSec = posToSave,
            durationSec = duration,
            lastAudioTrackId = _uiState.value.currentAudioTrackId,
            lastSubtitleTrackId = _uiState.value.currentSubtitleTrackId,
            lastPlayedTimestamp = System.currentTimeMillis()
        )
        viewModelScope.launch(Dispatchers.IO) { historyRepository.upsert(entry) }
    }

    override fun onCleared() {
        isActive.set(false)
        super.onCleared()
        saveHistoryIfNeeded()
        wrapper.reset()
    }

    fun setVolume(volume: Int) {
        if (!isActive.get()) return
        val clamped = volume.coerceIn(0, 150)
        wrapper.setPropertyInt("volume", clamped)
    }

    fun setZoom(zoom: Float) {
        setVideoZoom(zoom, uiState.value.videoPanX, uiState.value.videoPanY)
    }

    fun setPan(panX: Float, panY: Float) {
        setVideoZoom(uiState.value.videoZoom, panX, panY)
    }

    fun setVideoZoom(zoom: Float, panX: Float, panY: Float) {
        if (!isActive.get()) return
        val clampedZoom = zoom.coerceIn(1.0f, 4.0f)
        val finalPanX = if (clampedZoom == 1.0f) 0f else panX
        val finalPanY = if (clampedZoom == 1.0f) 0f else panY
        
        val mpvZoom = kotlin.math.ln(clampedZoom.toDouble()) / kotlin.math.ln(2.0)
        wrapper.setPropertyDouble("video-zoom", mpvZoom)
        wrapper.setPropertyDouble("video-pan-x", finalPanX.toDouble())
        wrapper.setPropertyDouble("video-pan-y", finalPanY.toDouble())
        
        _uiState.update { it.copy(videoZoom = clampedZoom, videoPanX = finalPanX, videoPanY = finalPanY) }
    }

    fun resetZoom() {
        setVideoZoom(1.0f, 0f, 0f)
    }

    // ── Playlist navigation ───────────────────────────────────────────────────

    fun setPlaylist(playlist: List<String>, playlistTitles: List<String>, currentUri: String) {
        val pairs = playlist.zip(playlistTitles)
        val index = pairs.indexOfFirst { it.first == currentUri }
        _uiState.update { it.copy(playlist = pairs, currentPlaylistIndex = index) }
    }

    fun playPrevious() {
        val state = _uiState.value
        val idx = state.currentPlaylistIndex
        if (idx > 0) {
            val (uri, title) = state.playlist[idx - 1]
            _uiState.update { it.copy(currentPlaylistIndex = idx - 1) }
            viewModelScope.launch(Dispatchers.IO) {
                val history = historyRepository.getByUri(uri)
                val resumePos = if (history != null && history.lastPlayedPositionSec > 0)
                    (history.lastPlayedPositionSec * 1000).toLong() else 0L
                withContext(Dispatchers.Main) { loadFile(uri, title, resumePos) }
            }
        }
    }

    fun playNext() {
        val state = _uiState.value
        val idx = state.currentPlaylistIndex
        if (idx >= 0 && idx < state.playlist.size - 1) {
            val (uri, title) = state.playlist[idx + 1]
            _uiState.update { it.copy(currentPlaylistIndex = idx + 1) }
            viewModelScope.launch(Dispatchers.IO) {
                val history = historyRepository.getByUri(uri)
                val resumePos = if (history != null && history.lastPlayedPositionSec > 0)
                    (history.lastPlayedPositionSec * 1000).toLong() else 0L
                withContext(Dispatchers.Main) { loadFile(uri, title, resumePos) }
            }
        }
    }
}
