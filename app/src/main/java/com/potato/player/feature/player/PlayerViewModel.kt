package com.potato.player.feature.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.data.VideoHistoryRepository
import com.potato.player.engine.MpvWrapper
import com.potato.player.engine.MpvEvent
import com.potato.player.engine.MpvProp
import com.potato.player.engine.PlayerEngineState

import com.potato.player.feature.player.state.*
import com.potato.player.util.MediaMetadataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun hwdecLabel(mode: String): String = when {
    mode == "no"                    -> "SW"
    mode == "mediacodec"            -> "HW"
    mode == "mediacodec-copy"       -> "HW+"
    mode.startsWith("mediacodec")   -> "HW+"
    mode.isEmpty()                  -> "HW+"
    else                            -> "HW"
}

enum class VideoFitMode { FIT, FILL, STRETCH }

class PlayerViewModel(
    private val appContext: Context,
    private val wrapper: MpvWrapper,
    private val historyRepository: VideoHistoryRepository
) : ViewModel() {

    private val prefsRepository by lazy { UserPreferencesRepository(appContext) }
    private val historyManager by lazy { PlaybackHistoryManager(historyRepository, viewModelScope) }
    
    private val _activeDialog = MutableStateFlow<ActiveDialog>(ActiveDialog.None)
    val activeDialog: StateFlow<ActiveDialog> = _activeDialog.asStateFlow()

    val playlistManager = PlaylistManager()
    val trackManager by lazy { TrackManager(prefsRepository, viewModelScope) }
    val geometryManager = VideoGeometryManager(wrapper)
    val sessionManager by lazy { PlaybackSessionManager(historyManager, trackManager, viewModelScope) }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(PlaybackProgressState())
    val progressState: StateFlow<PlaybackProgressState> = _progressState.asStateFlow()

    val isSeekingFlow: StateFlow<Boolean> = _progressState
        .map { it.dragPositionSec != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _gestureState = MutableStateFlow(PlayerGestureState())
    val gestureState: StateFlow<PlayerGestureState> = _gestureState.asStateFlow()

    private val isActive = java.util.concurrent.atomic.AtomicBoolean(true)
    private var mySurface: android.view.Surface? = null

    private var wasPlayingBeforePause: Boolean = false
    private var myPlaybackGeneration: Int = -1
    
    private var normalPlaybackSpeed = 1.0

    // Fix 2 — Drag round-trip suppression.
    // During active slider drag, local dragFraction in PlayerBottomControls drives the UI.
    // We must NOT emit to _progressState on every frame — that causes a redundant recomposition.
    // We only commit once, on drag end.
    private var isDragging = false
    private var lastDragPositionSec = 0.0

    private val engineEventHandler by lazy { EngineEventHandler(wrapper, prefsRepository, viewModelScope) }

    init {
        myPlaybackGeneration = wrapper.nextGeneration()
        engineEventHandler.start(
            onLifecycleEvent = { handleLifecycleEvent(it) },
            onEngineState = { handleEngineState(it) },
            onPrefsChanged = { applyPrefs(it) }
        )
    }

    private fun handleLifecycleEvent(event: MpvEvent.Lifecycle) {
        when (event) {
            is MpvEvent.Lifecycle.FileLoaded -> handleFileLoaded()
            is MpvEvent.Lifecycle.EndFile -> handleEndFile(event.reason)
            is MpvEvent.Lifecycle.PlaybackRestart -> handlePlaybackRestart()
            is MpvEvent.Lifecycle.Unknown -> Unit
        }
    }

    private var lastTrackListJson: String = ""

    private fun handleEngineState(state: PlayerEngineState) {
        updatePlaybackState(state)
        updateProgressState(state)
        if (state.trackListJson.isNotBlank() && state.trackListJson != lastTrackListJson) {
            lastTrackListJson = state.trackListJson
            trackManager.loadTracksFromJson(state.trackListJson, wrapper, appContext)
        }
    }

    private fun updatePlaybackState(state: PlayerEngineState) {
        val isBuffering = state.pausedForCache || state.cacheBufferingState < 100
        _uiState.update { it.copy(
            isPlaying = !state.paused,
            isLoading = it.fileLoaded && isBuffering,
            hwdecCurrent = if (state.hwdecActive.isNotEmpty()) hwdecLabel(state.hwdecActive) else it.hwdecCurrent,
            videoWidth = state.videoWidth.toInt(),
            videoHeight = state.videoHeight.toInt(),
            playbackSpeed = state.speed,
            subScale = state.subScale,
            subPos = state.subPos.toInt()
        ) }
    }

    private fun updateProgressState(state: PlayerEngineState) {
        if (_progressState.value.dragPositionSec == null) {
            _progressState.update { it.copy(
                positionSec = state.positionMs / 1000.0,
                durationSec = state.durationMs / 1000.0,
                cachedSec = state.cacheTimeMs / 1000.0,
                cacheDurationSec = state.cacheDurMs / 1000.0
            ) }
        } else {
            _progressState.update { it.copy(
                durationSec = state.durationMs / 1000.0,
                cachedSec = state.cacheTimeMs / 1000.0,
                cacheDurationSec = state.cacheDurMs / 1000.0
            ) }
        }
    }

    private fun applyPrefs(prefs: PlayerPrefs) {
        wrapper.setSubtitleScale(prefs.subScale)
        wrapper.setSubtitlePosition(prefs.subPos)
        _uiState.update { it.copy(
            subScale          = prefs.subScale,
            subPos            = prefs.subPos,
            isAutoRotation    = prefs.autoRotation,
            gesturesEnabled   = prefs.gesturesEnabled,
            lockButtonEnabled = prefs.lockButtonEnabled,
            controlsHideDelay = prefs.controlsHideDelay
        )}
        // default decoder and speed applied on file load, not here
    }





    private fun applyPreferredSubtitleTrack() = trackManager.applyPreferred { id -> wrapper.setSubTrack(id) }

    fun setSurfaceSize(width: Int, height: Int) {
        wrapper.setPropertyString(MpvProp.ANDROID_SURFACE_SIZE, "${width}x${height}")
    }

    fun handleSurfaceReady(surface: android.view.Surface) {
        mySurface = surface
        wrapper.attachSurface(surface)
        val uri = sessionManager.pendingUri ?: return
        sessionManager.pendingUri = null
        wrapper.loadFile(uri)
    }

    fun handleSurfaceDestroyed() {
        if (mySurface != null) {
            wrapper.detachSurface()
            mySurface = null
        }
    }

    fun prepareUri(defaultUri: String, defaultTitle: String = "") {
        sessionManager.prepareUri(defaultUri, defaultTitle) { u, t, pos ->
            executeLoadFile(u, t, pos)
        }
    }

    fun loadFile(uri: String, title: String = "", resumePosition: Long = 0L) {
        if (!isActive.get()) return
        executeLoadFile(uri, title, resumePosition)
    }

    private fun executeLoadFile(uri: String, title: String, resumePosition: Long) {
        sessionManager.executeLoadFile(
            uri = uri,
            title = title,
            resumePosition = resumePosition,
            hasSurface = mySurface != null,
            onStateUpdate = { _, _ -> },
            onUiInitial = { initialName ->
                _uiState.update { it.copy(fileName = initialName, isLoading = true, isPlaying = false, fileLoaded = false, error = null) }
            },
            onResolveFileName = { u ->
                viewModelScope.launch {
                    val resolvedName = MediaMetadataRepository.resolveFileName(appContext, u)
                    _uiState.update { it.copy(fileName = resolvedName) }
                }
            },
            onLoadFile = { u -> wrapper.loadFile(u) }
        )
    }

    fun togglePlay() {
        if (!isActive.get()) return
        wrapper.togglePlay()
    }
    
    private fun handleFileLoaded() {
        sessionManager.handleFileLoaded(
            onUiUpdate = {
                _uiState.update { it.copy(fileLoaded = true, isLoading = false, fitMode = VideoFitMode.FIT) }
            },
            onSeekIfNeeded = { pos -> wrapper.seekAccurate(pos) },
            onTracksLoaded = {
                // Track list is driven by engineState.trackListJson observer (handleEngineState).
                // We only need to apply the preferred subtitle once the tracks arrive.
                viewModelScope.launch {
                    // Apply default decoder and speed from prefs
                    prefsRepository.defaultDecoderFlow.first().let { mode ->
                        wrapper.setDecoder(mode)
                        _uiState.update { it.copy(hwdecCurrent = hwdecLabel(mode)) }
                    }
                    prefsRepository.defaultSpeedFlow.first().let { speed ->
                        wrapper.setSpeed(speed)
                        _uiState.update { it.copy(playbackSpeed = speed) }
                    }
                }

                viewModelScope.launch {
                    applyPreferredSubtitleTrack()
                }
            }
        )
    }

    private fun handleEndFile(reason: Int) {
        sessionManager.handleEndFile(
            reason = reason,
            onUiError = {
                _uiState.update { it.copy(isPlaying = false, error = "Playback error") }
            },
            onUiNormalEnd = {
                _uiState.update { it.copy(isPlaying = false) }
            },
            onSaveHistory = { saveHistoryIfNeeded() },
            onUiOther = {
                _uiState.update { it.copy(isPlaying = false) }
            },
            onResetFastForward = {
                if (_uiState.value.isFastForwarding) {
                    _uiState.update { it.copy(isFastForwarding = false) }
                    wrapper.setSpeed(normalPlaybackSpeed)
                }
            }
        )
    }

    private fun handlePlaybackRestart() {
        _uiState.update { it.copy(isLoading = false) }
    }

    private fun pause() {
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
        val metrics = appContext.resources.displayMetrics
        val nextMode = geometryManager.cycleFitMode(
            _uiState.value.fitMode,
            metrics.widthPixels,
            metrics.heightPixels
        )
        _uiState.update { it.copy(fitMode = nextMode) }
    }

    fun setDecoder(mode: String) {
        if (!isActive.get()) return
        val hwdec = hwdecLabel(mode)
        _uiState.update { it.copy(hwdecCurrent = hwdec) }
        wrapper.setDecoder(mode)
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
            wrapper.setSpeed(normalPlaybackSpeed)
            _uiState.update { it.copy(isFastForwarding = false, playbackSpeed = normalPlaybackSpeed) }
        }
    }

    fun onSliderDragStart(posSec: Double) {
        isDragging = true
        lastDragPositionSec = posSec
        _progressState.update { it.copy(dragPositionSec = posSec) }
    }

    fun onSliderDragChange(posSec: Double) {
        if (!isActive.get()) return
        lastDragPositionSec = posSec
        // Fix 2: do NOT emit to _progressState during active drag.
        // Local dragFraction state in PlayerBottomControls already drives the slider display.
        // Emitting here would cause a redundant recomposition on every drag frame.
    }

    fun onSliderDragEnd(posSec: Double) {
        if (!isActive.get()) return
        isDragging = false
        lastDragPositionSec = posSec
        val ms = (posSec * 1000).toLong()
        _progressState.update { it.copy(dragPositionSec = null) }
        wrapper.seekFast(ms)
    }

    fun onSwipeSeek(positionSec: Double) {
        if (!isActive.get()) return
        _gestureState.update { it.copy(swipeSeekTargetSec = positionSec) }
    }

    fun onSwipeSeekFinished() {
        if (!isActive.get()) return
        val target = _gestureState.value.swipeSeekTargetSec
        _gestureState.update { it.copy(swipeSeekTargetSec = null) }
        if (target != null) {
            val ms = (target * 1000).toLong()
            wrapper.seekFast(ms)
        }
    }


    fun setSwipingVolumeOrBrightness(isSwiping: Boolean) {
        _gestureState.update { it.copy(isSwipingVolumeOrBrightness = isSwiping) }
    }

    fun setPlaybackSpeed(speed: Double) {
        if (!isActive.get()) return
        val clamped = speed.coerceIn(0.25, 4.0)
        normalPlaybackSpeed = clamped
        if (!_uiState.value.isFastForwarding) {
            wrapper.setSpeed(clamped)
        }
    }

    fun onSelectAudioTrack(id: Int) { trackManager.selectAudio(id); wrapper.setAudioTrack(id) }
    fun onSelectSubtitleTrack(id: Int) { trackManager.selectSubtitle(id); wrapper.setSubTrack(id) }
    fun onLoadExternalSubtitle(uri: Uri, context: Context) = trackManager.loadExternal(uri, context, wrapper)

    fun previewSubtitleAppearance(scale: Double, pos: Int) {
        wrapper.setSubtitleScale(scale)
        wrapper.setSubtitlePosition(pos)
    }

    fun setSubtitleAppearance(scale: Double, pos: Int) {
        wrapper.setSubtitleScale(scale)
        wrapper.setSubtitlePosition(pos)
        viewModelScope.launch { prefsRepository.saveSubtitleAppearance(scale, pos) }
    }

    fun resetSubtitleAppearance() = setSubtitleAppearance(
        scale = UserPreferencesRepository.DEFAULT_SUB_SCALE,
        pos = UserPreferencesRepository.DEFAULT_SUB_POS
    )

    fun showDialog(dialog: ActiveDialog) { _activeDialog.value = dialog }
    fun dismissDialog() { _activeDialog.value = ActiveDialog.None }

    private fun saveHistoryIfNeeded() = historyManager.save(
        uri = sessionManager.currentUri,
        title = sessionManager.currentTitle,
        lastPlayedPositionSec = _progressState.value.positionSec,
        durationSec = _progressState.value.durationSec,
        lastAudioTrackId = trackManager.trackState.value.currentAudioTrackId,
        lastSubtitleTrackId = trackManager.trackState.value.currentSubtitleTrackId
    )

    override fun onCleared() {
        isActive.set(false)
        super.onCleared()
        saveHistoryIfNeeded()
        wrapper.stopIfGeneration(myPlaybackGeneration)
    }

    fun setVolume(volume: Int) {
        if (!isActive.get()) return
        val clamped = volume.coerceIn(0, 150)
        wrapper.setVolume(clamped)
    }

    fun setVideoZoom(zoom: Float, panX: Float, panY: Float) {
        if (!isActive.get()) return
        geometryManager.setVideoZoom(zoom, panX, panY) { pX, pY, z ->
            _gestureState.update { it.copy(videoZoom = z, videoPanX = pX, videoPanY = pY) }
        }
    }

    fun resetZoom() {
        geometryManager.resetZoom { pX, pY, z ->
            _gestureState.update { it.copy(videoZoom = z, videoPanX = pX, videoPanY = pY) }
        }
    }

    // ── Playlist navigation ───────────────────────────────────────────────────

    fun setPlaylist(playlist: List<String>, playlistTitles: List<String>, currentUri: String) {
        val items = playlist.zip(playlistTitles)
        val startIndex = playlist.indexOf(currentUri).coerceAtLeast(0)
        playlistManager.setPlaylist(items, startIndex)
        _uiState.update { it.copy(currentPlaylistIndex = playlistManager.currentIndex.value) }
    }

    fun playNext() {
        playlistManager.moveNext()?.let { (uri, title) -> loadFile(uri, title) }
    }

    fun playPrevious() {
        playlistManager.movePrevious()?.let { (uri, title) -> loadFile(uri, title) }
    }



}
