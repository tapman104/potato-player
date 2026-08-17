package com.potato.player.feature.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.data.VideoHistoryRepository
import com.potato.player.engine.MpvProp
import com.potato.player.engine.MpvWrapper
import com.potato.player.feature.player.vm.*
import com.potato.player.util.MediaMetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class PlayerViewModel(
    private val appContext: Context,
    private val wrapper: MpvWrapper,
    private val historyRepository: VideoHistoryRepository
) : ViewModel() {

    private val prefsRepository by lazy { UserPreferencesRepository(appContext) }
    
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val isActive = AtomicBoolean(true)
    private var mySurface: android.view.Surface? = null
    private var myPlaybackGeneration: Int = -1
    private var pendingUri: String? = null
    private var currentUri = ""
    private var currentTitle = ""
    private var normalPlaybackSpeed = 1.0
    private var pendingSeekPosition: Long = 0L

    private val preferredSubLangState = prefsRepository.preferredSubLangFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "eng")

    private val playbackController = PlaybackController(wrapper, _uiState, isActive)
    private val seekController = SeekController(wrapper, _uiState, isActive)
    private val trackController = TrackController(wrapper, _uiState, appContext, isActive, preferredSubLangState, viewModelScope)
    private val gestureController = GestureController(wrapper, _uiState, appContext, isActive)
    private val subtitleController = SubtitleController(wrapper, _uiState, isActive, prefsRepository, viewModelScope)
    private val historyController = HistoryController(historyRepository, _uiState, viewModelScope)

    private val eventProcessor = MpvEventProcessor(
        onPlaybackStarted = { _uiState.update { it.copy(isLoading = false, isPlaying = true) } },
        onPlaybackPaused = { _uiState.update { it.copy(isPlaying = false) } },
        onPlaybackRestart = {
            val isPaused = wrapper.getPropertyBoolean(MpvProp.PAUSE) ?: false
            _uiState.update { it.copy(isLoading = false, isPlaying = !isPaused) }
        },
        onDurationChanged = { ms -> _uiState.update { it.copy(progressState = it.progressState.copy(durationSec = ms / 1000.0)) } },
        onPositionChanged = { ms -> 
            if (!seekController.isSliderSeeking) _uiState.update { it.copy(progressState = it.progressState.copy(positionSec = ms / 1000.0)) } 
        },
        onTracksChanged = { json ->
            trackController.loadTracks()
            viewModelScope.launch {
                preferredSubLangState.first { true }
                trackController.applyPreferredSubtitleTrack()
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
            historyController.saveHistoryIfNeeded(currentUri, currentTitle)
            playbackController.stopFastForward(normalPlaybackSpeed) { normalPlaybackSpeed = it }
        },
        onFileLoaded = {
            val isPaused = wrapper.getPropertyBoolean(MpvProp.PAUSE) ?: false
            _uiState.update { it.copy(fileLoaded = true, isLoading = false, isPlaying = !isPaused, fitMode = VideoFitMode.FIT) }
            if (pendingSeekPosition > 0L) {
                wrapper.seekTo(pendingSeekPosition)
                pendingSeekPosition = 0L
            }
            trackController.loadTracks()
            viewModelScope.launch {
                preferredSubLangState.first { true }
                trackController.applyPreferredSubtitleTrack()
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
        myPlaybackGeneration = wrapper.play()
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

    fun setSurfaceSize(width: Int, height: Int) {
        wrapper.setPropertyString(MpvProp.PROP_ANDROID_SURFACE_SIZE, "${width}x${height}")
    }

    fun handleSurfaceReady(surface: android.view.Surface) {
        mySurface = surface
        wrapper.attachSurface(surface)
        val uri = pendingUri ?: return
        pendingUri = null
        wrapper.loadFile(uri)
    }

    fun handleSurfaceDestroyed() {
        if (mySurface != null) {
            wrapper.detachSurface()
            mySurface = null
        }
    }

    fun prepareUri(defaultUri: String, defaultTitle: String = "") {
        if (playbackController.lastLoadedUri == defaultUri) return
        playbackController.lastLoadedUri = defaultUri

        viewModelScope.launch(Dispatchers.IO) {
            val resumePos = historyController.resolveResumePosition(defaultUri)
            withContext(Dispatchers.Main) {
                executeLoadFile(defaultUri, defaultTitle, resumePos)
            }
        }
    }

    fun loadFile(uri: String, title: String = "", resumePosition: Long = 0L) {
        if (!isActive.get()) return
        executeLoadFile(uri, title, resumePosition)
    }

    private fun executeLoadFile(uri: String, title: String, resumePosition: Long) {
        playbackController.lastLoadedUri = uri
        currentUri = uri
        currentTitle = title
        trackController.autoSubApplied = false
        val initialName = if (title.isNotBlank()) title else "Video"
        _uiState.update { it.copy(fileName = initialName, isLoading = true, isPlaying = false, fileLoaded = false, error = null) }
        if (title.isBlank()) {
            viewModelScope.launch {
                val resolvedName = MediaMetadataRepository.resolveFileName(appContext, uri)
                _uiState.update { it.copy(fileName = resolvedName) }
            }
        }
        pendingSeekPosition = resumePosition
        if (mySurface != null) {
            wrapper.loadFile(uri)
        } else {
            pendingUri = uri
        }
    }

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
                val resumePos = historyController.resolveResumePosition(uri)
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
                val resumePos = historyController.resolveResumePosition(uri)
                withContext(Dispatchers.Main) { loadFile(uri, title, resumePos) }
            }
        }
    }

    override fun onCleared() {
        isActive.set(false)
        super.onCleared()
        historyController.saveHistoryIfNeeded(currentUri, currentTitle)
        wrapper.stopIfGeneration(myPlaybackGeneration)
    }

    fun onPlayerPause() {
        playbackController.onPlayerPause()
        historyController.saveHistoryIfNeeded(currentUri, currentTitle)
    }

    fun onPlayerResume() = playbackController.onPlayerResume()
    
    fun togglePlay() = playbackController.togglePlay()
    fun pause() = playbackController.pause()
    fun setDecoder(mode: String) = playbackController.setDecoder(mode)
    fun setPlaybackSpeed(speed: Double) = playbackController.setPlaybackSpeed(speed, _uiState.value.isFastForwarding) { normalPlaybackSpeed = it }
    fun showDialog(dialog: ActiveDialog) = playbackController.showDialog(dialog)
    fun dismissDialog() = playbackController.dismissDialog()
    fun cycleOrientationMode() = playbackController.cycleOrientationMode()
    fun toggleAutoRotation() = playbackController.toggleAutoRotation(prefsRepository, viewModelScope)
    
    fun seekRelative(offsetSec: Double) = seekController.seekRelative(offsetSec)
    fun seekExactRelative(offsetSec: Int) = seekController.seekExactRelative(offsetSec)
    fun startFastForward() = playbackController.startFastForward(normalPlaybackSpeed)
    fun stopFastForward() = playbackController.stopFastForward(normalPlaybackSpeed) { normalPlaybackSpeed = it }
    fun onSliderDragStart(posSec: Double) = seekController.onSliderDragStart(posSec)
    fun onSliderDragChange(posSec: Double) = seekController.onSliderDragChange(posSec)
    fun onSliderDragEnd(posSec: Double) = seekController.onSliderDragEnd(posSec)
    fun onSwipeSeek(positionSec: Double) = seekController.onSwipeSeek(positionSec)
    fun onSwipeSeekFinished() = seekController.onSwipeSeekFinished()
    fun setSwipingVolumeOrBrightness(isSwiping: Boolean) = seekController.setSwipingVolumeOrBrightness(isSwiping)
    
    fun onSelectAudioTrack(id: Int) = trackController.onSelectAudioTrack(id, ::dismissDialog)
    fun onSelectSubtitleTrack(id: Int) = trackController.onSelectSubtitleTrack(id, ::dismissDialog)
    fun onLoadExternalSubtitle(uri: Uri, context: Context) = trackController.onLoadExternalSubtitle(uri, context, ::dismissDialog)
    
    fun cycleFitMode() = gestureController.cycleFitMode()
    fun setZoom(zoom: Float) = gestureController.setZoom(zoom)
    fun setPan(panX: Float, panY: Float) = gestureController.setPan(panX, panY)
    fun setVideoZoom(zoom: Float, panX: Float, panY: Float) = gestureController.setVideoZoom(zoom, panX, panY)
    fun resetZoom() = gestureController.resetZoom()
    fun setVolume(volume: Int) = gestureController.setVolume(volume)
    fun toggleLock() = gestureController.toggleLock()
    
    fun setSubScale(scale: Double) = subtitleController.setSubScale(scale)
    fun setSubPos(pos: Int) = subtitleController.setSubPos(pos)
    fun setSubtitleAppearance(scale: Double, pos: Int) = subtitleController.setSubtitleAppearance(scale, pos)
    fun resetSubtitleAppearance() = subtitleController.resetSubtitleAppearance()
}
