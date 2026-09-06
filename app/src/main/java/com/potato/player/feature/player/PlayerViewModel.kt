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
import com.potato.player.feature.player.state.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
    val orientationManager = OrientationManager()

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
    private var myPlaybackGeneration: Int = -1

    private val seekController = SeekController(
        wrapper = wrapper,
        isActive = isActive,
        onDragPositionChanged = { pos -> _progressState.update { it.copy(dragPositionSec = pos) } },
        onFastForwardChanged = { ff -> _uiState.update { it.copy(isFastForwarding = ff) } },
        onSpeedChanged = { spd -> _uiState.update { it.copy(playbackSpeed = spd) } }
    )

    private val engineEventHandler by lazy { EngineEventHandler(wrapper, prefsRepository, viewModelScope) }

    val sessionManager by lazy {
        PlaybackSessionManager(
            wrapper = wrapper,
            prefsRepository = prefsRepository,
            historyManager = historyManager,
            trackManager = trackManager,
            appContext = appContext,
            scope = viewModelScope,
            orientationManager = orientationManager,
            hasSurface = { mySurface != null },
            isPlaying = { _uiState.value.isPlaying },
            getProgressState = { _progressState.value },
            onFileLoading = { fileName ->
                _uiState.update {
                    it.copy(
                        fileName = fileName,
                        isLoading = true,
                        isPlaying = false,
                        fileLoaded = false,
                        error = null,
                        orientationMode = OrientationMode.AUTO
                    )
                }
            },
            onFileLoaded = { hwdec, speed ->
                _uiState.update {
                    it.copy(
                        fileLoaded = true,
                        isLoading = false,
                        fitMode = VideoFitMode.FIT,
                        hwdecCurrent = hwdec,
                        playbackSpeed = speed
                    )
                }
            },
            onEndFile = { reason ->
                if (reason == 3) {
                    _uiState.update { it.copy(isPlaying = false, error = "Playback error") }
                } else {
                    _uiState.update { it.copy(isPlaying = false) }
                }
                if (reason == 0) {
                    seekController.resetFastForward()
                }
            }
        )
    }

    init {
        myPlaybackGeneration = wrapper.nextGeneration()
        engineEventHandler.start(
            onLifecycleEvent = { handleLifecycleEvent(it) },
            onEngineState = { uiUpdate, progressUpdate ->
                _uiState.update { it.copy(
                    isPlaying = uiUpdate.isPlaying,
                    isLoading = it.fileLoaded && uiUpdate.isBuffering,
                    hwdecCurrent = if (uiUpdate.hwdecActive.isNotEmpty())
                        hwdecLabel(uiUpdate.hwdecActive) else it.hwdecCurrent,
                    videoWidth = uiUpdate.videoWidth,
                    videoHeight = uiUpdate.videoHeight,
                    videoRotate = uiUpdate.videoRotate,
                    playbackSpeed = uiUpdate.playbackSpeed,
                    subScale = uiUpdate.subScale,
                    subPos = uiUpdate.subPos
                ) }

                orientationManager.onDimensionsChanged(
                    width = uiUpdate.videoWidth,
                    height = uiUpdate.videoHeight,
                    rotate = uiUpdate.videoRotate,
                    orientationMode = _uiState.value.orientationMode,
                    videoOrientation = _uiState.value.videoOrientation
                )

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
            is MpvEvent.Lifecycle.FileLoaded -> sessionManager.onFileLoaded()
            is MpvEvent.Lifecycle.EndFile -> sessionManager.onEndFile(event.reason)
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
            videoOrientation  = prefs.videoOrientation,
            gesturesEnabled   = prefs.gesturesEnabled,
            lockButtonEnabled = prefs.lockButtonEnabled,
            controlsHideDelay = prefs.controlsHideDelay
        )}
        // default decoder and speed applied on file load, not here
        // Re-apply orientation — prefs may arrive after first engineState update
        orientationManager.apply(
            orientationMode = _uiState.value.orientationMode,
            videoOrientation = prefs.videoOrientation,
            videoRotate = _uiState.value.videoRotate
        )
    }

    fun setSurfaceSize(width: Int, height: Int) {
        wrapper.setPropertyString(MpvProp.ANDROID_SURFACE_SIZE, "${width}x${height}")
    }

    fun handleSurfaceReady(surface: android.view.Surface) {
        mySurface = surface
        wrapper.attachSurface(surface)
        val uri = sessionManager.consumePendingUri() ?: return
        wrapper.loadFile(uri)
    }

    fun handleSurfaceDestroyed() {
        if (mySurface != null) {
            wrapper.detachSurface()
            mySurface = null
        }
    }

    fun prepareUri(defaultUri: String, defaultTitle: String = "") {
        sessionManager.load(defaultUri, defaultTitle)
    }

    fun loadFile(uri: String, title: String = "", resumePosition: Long = 0L) {
        if (!isActive.get()) return
        sessionManager.loadDirect(uri, title, resumePosition)
    }

    fun togglePlay() {
        if (!isActive.get()) return
        wrapper.togglePlay()
    }

    private fun handlePlaybackRestart() {
        _uiState.update { it.copy(isLoading = false) }
    }

    fun onPlayerPause() {
        sessionManager.onPlayerPause()
    }

    fun onPlayerResume() {
        sessionManager.onPlayerResume()
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
        orientationManager.apply(next, _uiState.value.videoOrientation, _uiState.value.videoRotate)
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

    override fun onCleared() {
        isActive.set(false)
        orientationManager.reset()
        orientationManager.activity = null
        super.onCleared()
        sessionManager.saveHistoryIfNeeded()
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
