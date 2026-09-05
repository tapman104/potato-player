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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.withContext

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
    val trackManager by lazy { TrackManager(prefsRepository, viewModelScope, wrapper) }
    val geometryManager = VideoGeometryManager(wrapper)

    // ── Fields merged from PlaybackSessionManager ─────────────────────────────
    private var currentUri: String = ""
    private var currentTitle: String = ""
    private var pendingUri: String? = null
    private var pendingSeekPosition: Long = 0L
    private var lastLoadedUri: String? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(PlaybackProgressState())
    val progressState: StateFlow<PlaybackProgressState> = _progressState.asStateFlow()

    val isSeekingFlow: StateFlow<Boolean> = _progressState
        .map { it.dragPositionSec != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val isActive = java.util.concurrent.atomic.AtomicBoolean(true)
    private var mySurface: android.view.Surface? = null

    private var wasPlayingBeforePause: Boolean = false
    private var myPlaybackGeneration: Int = -1

    private var lastVideoWidth: Int = 0
    private var lastVideoHeight: Int = 0
    var activity: android.app.Activity? = null

    /**
     * Sole owner of activity.requestedOrientation.
     * Valid call sites (add no others without updating this list):
     *   1. onEngineState      — AUTO mode only, after real dimensions arrive
     *   2. cycleOrientationMode — user taps the orientation cycle button
     *   3. toggleAutoRotation — user toggles the auto-rotation pref
     *   4. DisposableEffect init in PlayerLifecycleEffect — once per activity assignment
     */
    fun applyOrientationFromUiState() {
        val state = _uiState.value
        if (state.isAutoRotation) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            return
        }
        when (state.orientationMode) {
            OrientationMode.LOCK_LANDSCAPE ->
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationMode.LOCK_PORTRAIT ->
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationMode.AUTO -> {
                if (lastVideoWidth > 0 && lastVideoHeight > 0) {
                    val orientation = if (lastVideoWidth >= lastVideoHeight)
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    activity?.requestedOrientation = orientation
                } else {
                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }



    private val seekController = SeekController(
        wrapper = wrapper,
        isActive = isActive,
        onDragPositionChanged = { pos -> _progressState.update { it.copy(dragPositionSec = pos) } },
        onFastForwardChanged = { ff -> _uiState.update { it.copy(isFastForwarding = ff) } },
        onSpeedChanged = { spd -> _uiState.update { it.copy(playbackSpeed = spd) } }
    )

    private val engineEventHandler by lazy { EngineEventHandler(wrapper, prefsRepository, viewModelScope) }

    init {
        myPlaybackGeneration = wrapper.nextGeneration()
        engineEventHandler.start(
            onLifecycleEvent = { handleLifecycleEvent(it) },
            onEngineState = { uiUpdate, progressUpdate ->
                val prevWidth = lastVideoWidth
                val prevHeight = lastVideoHeight
                lastVideoWidth = uiUpdate.videoWidth
                lastVideoHeight = uiUpdate.videoHeight
                
                if (lastVideoWidth > 0 && lastVideoHeight > 0 &&
                   (lastVideoWidth != prevWidth || lastVideoHeight != prevHeight) &&
                   _uiState.value.orientationMode == OrientationMode.AUTO) {
                    applyOrientationFromUiState()
                }

                _uiState.update { it.copy(
                    isPlaying = uiUpdate.isPlaying,
                    isLoading = it.fileLoaded && uiUpdate.isBuffering,
                    hwdecCurrent = if (uiUpdate.hwdecActive.isNotEmpty())
                        hwdecLabel(uiUpdate.hwdecActive) else it.hwdecCurrent,
                    videoWidth = uiUpdate.videoWidth,
                    videoHeight = uiUpdate.videoHeight,
                    playbackSpeed = uiUpdate.playbackSpeed,
                    subScale = uiUpdate.subScale,
                    subPos = uiUpdate.subPos
                ) }
                _progressState.update { it.copy(
                    positionSec = progressUpdate.positionSec ?: it.positionSec,
                    durationSec = progressUpdate.durationSec,
                    cachedSec = progressUpdate.cachedSec,
                    cacheDurationSec = progressUpdate.cacheDurationSec
                ) }
            },
            isDragging = { _progressState.value.dragPositionSec != null },
            onPrefsChanged = { applyPrefs(it) },
            onTrackListChanged = { json ->
                trackManager.loadTracksFromJson(json, appContext)
            }
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

    fun setSurfaceSize(width: Int, height: Int) {
        wrapper.setPropertyString(MpvProp.ANDROID_SURFACE_SIZE, "${width}x${height}")
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
        if (lastLoadedUri == defaultUri) return
        lastLoadedUri = defaultUri

        viewModelScope.launch(Dispatchers.IO) {
            val history = historyManager.getByUri(defaultUri)
            val resumePos = if (history != null && history.lastPlayedPositionSec > 0)
                (history.lastPlayedPositionSec * 1000).toLong() else 0L
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
        trackManager.clearTracks()
        lastLoadedUri = uri
        currentUri = uri
        currentTitle = title
        trackManager.resetAutoSubApplied()
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

    fun togglePlay() {
        if (!isActive.get()) return
        wrapper.togglePlay()
    }

    private fun handleFileLoaded() {
        _uiState.update { it.copy(fileLoaded = true, isLoading = false, fitMode = VideoFitMode.FIT) }
        if (pendingSeekPosition > 0L) {
            wrapper.seekAccurate(pendingSeekPosition)
            pendingSeekPosition = 0L
        }
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
            trackManager.applyPreferred()
        }

        // Fix 2: Force track list reload after a short delay to ensure MPV has populated
        // track-list. This resolves the infinite spinner when MPV does not re-fire TRACK_LIST.
        viewModelScope.launch {
            delay(500)
            trackManager.requestTrackReload(appContext)
        }
    }

    private fun handleEndFile(reason: Int) {
        if (reason == 3) {
            _uiState.update { it.copy(isPlaying = false, error = "Playback error") }
        } else if (reason == 0) {
            _uiState.update { it.copy(isPlaying = false) }
            saveHistoryIfNeeded()
            seekController.resetFastForward()
        } else {
            _uiState.update { it.copy(isPlaying = false) }
        }
    }

    private fun handlePlaybackRestart() {
        _uiState.update { it.copy(isLoading = false) }
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
        
        applyOrientationFromUiState()
    }

    fun toggleAutoRotation() {
        val next = !_uiState.value.isAutoRotation
        _uiState.update { it.copy(isAutoRotation = next) }
        applyOrientationFromUiState()
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

    fun seekExactRelative(offsetSec: Int) = seekController.seekExactRelative(offsetSec)

    fun startFastForward() = seekController.startFastForward(_uiState.value.playbackSpeed)
    fun stopFastForward()  = seekController.stopFastForward()

    fun onSliderDragStart(posSec: Double) = seekController.onSliderDragStart(posSec)
    fun onSliderDragChange(posSec: Double) = seekController.onSliderDragChange(posSec)
    fun onSliderDragEnd(posSec: Double) = seekController.onSliderDragEnd(posSec)
    fun seekTo(positionSec: Double) = seekController.seekTo(positionSec)

    fun setPlaybackSpeed(speed: Double) = seekController.setPlaybackSpeed(speed)

    fun onSelectAudioTrack(id: Int) = trackManager.selectAudio(id)
    fun onSelectSubtitleTrack(id: Int) = trackManager.selectSubtitle(id)
    fun onLoadExternalSubtitle(uri: Uri, context: Context) = trackManager.loadExternal(uri, context)

    fun previewSubtitleAppearance(scale: Double, pos: Int) = trackManager.previewSubtitleAppearance(scale, pos)
    fun setSubtitleAppearance(scale: Double, pos: Int) = trackManager.setSubtitleAppearance(scale, pos)
    fun resetSubtitleAppearance() = trackManager.resetSubtitleAppearance()

    fun showDialog(dialog: ActiveDialog) { _activeDialog.value = dialog }
    fun dismissDialog() { _activeDialog.value = ActiveDialog.None }

    private fun saveHistoryIfNeeded() = historyManager.save(
        uri = currentUri,
        title = currentTitle,
        lastPlayedPositionSec = _progressState.value.positionSec,
        durationSec = _progressState.value.durationSec,
        lastAudioTrackId = trackManager.trackState.value.currentAudioTrackId,
        lastSubtitleTrackId = trackManager.trackState.value.currentSubtitleTrackId
    )

    override fun onCleared() {
        isActive.set(false)
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity = null
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
        geometryManager.setVideoZoom(zoom, panX, panY)
    }

    fun resetZoom() {
        geometryManager.resetZoom()
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
