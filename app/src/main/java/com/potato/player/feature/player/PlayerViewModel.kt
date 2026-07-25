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
import com.potato.player.feature.player.PlayerUiConstants
import com.potato.player.feature.player.toUiModel
import com.potato.player.util.MediaMetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.SurfaceHolder

class PlayerDialogStateHolder {
    private val _activeSheet = MutableStateFlow(ActiveSheet.NONE)
    val activeSheet: StateFlow<ActiveSheet> = _activeSheet.asStateFlow()

    fun onMoreMenuToggle() {
        _activeSheet.update { if (it == ActiveSheet.MORE_MENU) ActiveSheet.NONE else ActiveSheet.MORE_MENU }
    }
    fun onMoreMenuDismiss() { _activeSheet.value = ActiveSheet.NONE }
    fun onShowAudioDialog() { _activeSheet.value = ActiveSheet.AUDIO }
    fun onDismissAudioDialog() { _activeSheet.value = ActiveSheet.NONE }
    fun onShowSubtitleDialog() { _activeSheet.value = ActiveSheet.SUBTITLE }
    fun onDismissSubtitleDialog() { _activeSheet.value = ActiveSheet.NONE }
    fun onShowSpeedDialog() { _activeSheet.value = ActiveSheet.SPEED }
    fun onDismissSpeedDialog() { _activeSheet.value = ActiveSheet.NONE }
    fun onShowDecoderDialog() { _activeSheet.value = ActiveSheet.DECODER }
    fun onDismissDecoderDialog() { _activeSheet.value = ActiveSheet.NONE }
}

class PlayerViewModel(
    private val appContext: Context,
    private val wrapper: MpvWrapper,
    private val historyRepository: VideoHistoryRepository
) : ViewModel() {

    private val prefsRepository by lazy { UserPreferencesRepository(appContext) }
    
    val dialogs = PlayerDialogStateHolder()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(PlaybackProgressState())
    val progressState: StateFlow<PlaybackProgressState> = _progressState.asStateFlow()

    private var currentUri = ""
    private var currentTitle = ""
    
    private var normalPlaybackSpeed = 1.0
    private var isSliderSeeking = false
    private var pendingResumePosition: Long = 0L

    private val eventProcessor = MpvEventProcessor(
        onPlaybackStarted = { _uiState.update { it.copy(isLoading = false, isPlaying = true) } },
        onPlaybackPaused = { _uiState.update { it.copy(isPlaying = false) } },
        onDurationChanged = { ms -> _progressState.update { it.copy(durationSec = ms / 1000.0) } },
        onPositionChanged = { ms -> 
            if (!isSliderSeeking) _progressState.update { it.copy(positionSec = ms / 1000.0) } 
        },
        onTracksChanged = { json ->
            val tracks = TrackListParser.parse(json)
            if (tracks.isNotEmpty()) {
                val audioTracks = tracks.filter { it.type == TrackType.AUDIO }.map { it.toUiModel() }
                val subtitleTracks = tracks.filter { it.type == TrackType.SUBTITLE }.map { it.toUiModel() }
                _uiState.update { it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks) }
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
            _uiState.update { it.copy(fileLoaded = true, isLoading = false) }
            loadTracks()
            if (pendingResumePosition > 0L) {
                wrapper.seekTo(pendingResumePosition)
                pendingResumePosition = 0L
            }
        },
        onCacheTimeChanged = { sec -> _progressState.update { it.copy(cachedSec = sec) } },
        onCacheDurationChanged = { sec -> _progressState.update { it.copy(cacheDurationSec = sec) } },
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

    val surfaceCallback: SurfaceHolder.Callback get() = wrapper.surfaceCallback

    fun onSurfaceReady(uri: String, title: String = "") {
        if (currentUri != uri) {
            viewModelScope.launch(Dispatchers.IO) {
                val history = historyRepository.getByUri(uri)
                val resumePos = if (history != null && history.lastPlayedPositionSec > 0)
                    (history.lastPlayedPositionSec * 1000).toLong() else 0L
                withContext(Dispatchers.Main) {
                    loadFile(uri, title, resumePos)
                }
            }
        }
    }

    fun onSurfaceReattached() {
        if (!_uiState.value.fileLoaded) return
        if (_uiState.value.isPlaying) {
            wrapper.resume()
        }
    }

    fun setSurfaceReadyCallback(cb: (() -> Unit)?) {
        wrapper.onSurfaceReady = cb
    }

    fun onSurfaceDestroyed() {
        wrapper.onSurfaceReady = null
    }

    fun loadFile(uri: String, title: String = "", resumePosition: Long = 0L) {
        currentUri = uri
        currentTitle = title
        pendingResumePosition = resumePosition
        val initialName = if (title.isNotBlank()) title else "Video"
        _uiState.update { it.copy(fileName = initialName, isLoading = true, isPlaying = false, fileLoaded = false, error = null) }
        if (title.isBlank()) {
            viewModelScope.launch {
                val resolvedName = MediaMetadataRepository.resolveFileName(appContext, uri)
                _uiState.update { it.copy(fileName = resolvedName) }
            }
        }
        wrapper.play(uri)
    }

    fun togglePlay() {
        wrapper.togglePlay()
    }
    
    fun pause() {
        wrapper.pause()
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

    fun setDecoder(mode: String) {
        val hwdec = when (mode) { "no" -> "SW"; "mediacodec" -> "HW"; else -> "HW+" }
        _uiState.update { it.copy(hwdecCurrent = hwdec) }
        wrapper.setDecoder(mode)
    }

    fun seekRelative(offsetSec: Double) {
        val target = (_progressState.value.positionSec + offsetSec).coerceIn(0.0, _progressState.value.durationSec.takeIf { it > 0.0 } ?: Double.MAX_VALUE)
        wrapper.seekTo((target * 1000).toLong())
    }

    fun seekExactRelative(offsetSec: Int) {
        wrapper.seekRelative(offsetSec.toDouble())
    }

    fun startFastForward() {
        if (!_uiState.value.isFastForwarding) {
            normalPlaybackSpeed = _uiState.value.playbackSpeed
            _uiState.update { it.copy(isFastForwarding = true) }
            wrapper.setSpeed(2.0)
        }
    }

    fun stopFastForward() {
        if (_uiState.value.isFastForwarding) {
            _uiState.update { it.copy(isFastForwarding = false) }
            wrapper.setSpeed(normalPlaybackSpeed)
            _uiState.update { it.copy(playbackSpeed = normalPlaybackSpeed) }
        }
    }

    fun onSliderDragStart(posSec: Double) {
        isSliderSeeking = true
        _progressState.update { it.copy(dragPositionSec = posSec) }
    }

    fun onSliderDragChange(posSec: Double) {
        _progressState.update { it.copy(dragPositionSec = posSec) }
        // The old code used seekGesture which just stored a value, but since MPV has its own thread and queue
        // we could just use absolute+exact if we want to seek during drag. Since we deleted the debouncer,
        // it's better not to spam it, but actually `seekTo` should be fine. However, old `seekGesture`
        // was non-blocking. MPV natively drops rapidly queued seeks anyway. Let's do `seekTo`.
        wrapper.seekTo((posSec * 1000).toLong())
    }

    fun onSliderDragEnd(posSec: Double) {
        isSliderSeeking = false
        wrapper.seekTo((posSec * 1000).toLong())
        _progressState.update { it.copy(dragPositionSec = null) }
    }

    fun setPlaybackSpeed(speed: Double) {
        val clamped = speed.coerceIn(0.25, 4.0)
        normalPlaybackSpeed = clamped
        if (!_uiState.value.isFastForwarding) {
            _uiState.update { it.copy(playbackSpeed = clamped) }
            wrapper.setSpeed(clamped)
        }
    }

    fun onSelectAudioTrack(id: Int) {
        wrapper.setAudioTrack(id)
        _uiState.update { it.copy(currentAudioTrackId = id) }
        dialogs.onDismissAudioDialog()
    }

    fun onSelectSubtitleTrack(id: Int) {
        wrapper.setSubTrack(id)
        _uiState.update { it.copy(currentSubtitleTrackId = id) }
        dialogs.onDismissSubtitleDialog()
    }

    fun onLoadExternalSubtitle(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = MediaMetadataRepository.resolveSubtitlePath(context, uri) ?: uri.toString()
            wrapper.addExternalSubtitle(path)
            // Reload tracks to reflect new subtitle
            loadTracks()
        }
        dialogs.onDismissSubtitleDialog()
    }

    fun setSubScale(scale: Double) { wrapper.setSubScale(scale) }
    fun setSubPos(pos: Int) { wrapper.setSubPos(pos) }

    fun setSubtitleAppearance(scale: Double, pos: Int) {
        wrapper.setSubScale(scale)
        wrapper.setSubPos(pos)
        viewModelScope.launch {
            prefsRepository.setSubScale(scale)
            prefsRepository.setSubPos(pos)
        }
    }

    fun resetSubtitleAppearance() {
        wrapper.setSubScale(PlayerUiConstants.DEFAULT_SUBTITLE_SCALE)
        wrapper.setSubPos(PlayerUiConstants.DEFAULT_SUBTITLE_POSITION)
        viewModelScope.launch {
            prefsRepository.setSubScale(PlayerUiConstants.DEFAULT_SUBTITLE_SCALE)
            prefsRepository.setSubPos(PlayerUiConstants.DEFAULT_SUBTITLE_POSITION)
        }
    }

    private fun saveHistoryIfNeeded() {
        if (currentUri.isEmpty() || _progressState.value.durationSec <= 0.0) return
        val entry = VideoHistory(
            uri = currentUri,
            title = currentTitle.ifEmpty { currentUri.substringAfterLast('/') },
            lastPlayedPositionSec = _progressState.value.positionSec,
            durationSec = _progressState.value.durationSec,
            lastAudioTrackId = _uiState.value.currentAudioTrackId,
            lastSubtitleTrackId = _uiState.value.currentSubtitleTrackId,
            lastPlayedTimestamp = System.currentTimeMillis()
        )
        viewModelScope.launch(Dispatchers.IO) { historyRepository.upsert(entry) }
    }

    override fun onCleared() {
        super.onCleared()
        saveHistoryIfNeeded()
        wrapper.destroy()
    }

    fun setVolume(volume: Int) {
        val clamped = volume.coerceIn(0, 150)
        wrapper.setPropertyInt("volume", clamped)
        _uiState.update { it.copy(volume = clamped) }
    }

    fun setVideoZoom(zoom: Float, panX: Float, panY: Float) {
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
}
