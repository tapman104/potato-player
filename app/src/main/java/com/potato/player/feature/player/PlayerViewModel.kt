package com.potato.player.feature.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.data.VideoHistoryRepository
import com.potato.player.engine.MpvWrapper
import com.potato.player.engine.MpvEvent
import com.potato.player.feature.player.state.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VideoFitMode { FIT, FILL, STRETCH }

class PlayerViewModel(
    private val appContext: Context,
    private val historyRepository: VideoHistoryRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val wrapper: MpvWrapper

    init {
        val audioLang = kotlinx.coroutines.runBlocking { prefsRepository.preferredAudioLangFlow.first() }
        val subLang   = kotlinx.coroutines.runBlocking { prefsRepository.preferredSubLangFlow.first() }
        wrapper = MpvWrapper(appContext, audioLang, subLang)
    }

    private val historyManager by lazy { PlaybackHistoryManager(historyRepository, viewModelScope) }

    private val _activeDialog = MutableStateFlow<ActiveDialog>(ActiveDialog.None)
    val activeDialog: StateFlow<ActiveDialog> = _activeDialog.asStateFlow()

    val playlistManager = PlaylistManager()
    val trackManager by lazy { TrackManager(prefsRepository, viewModelScope, wrapper) }
    val geometryManager = VideoGeometryManager(wrapper)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(PlaybackProgressState())
    val progressState: StateFlow<PlaybackProgressState> = _progressState.asStateFlow()

    val isSeekingFlow: StateFlow<Boolean> = _progressState
        .map { it.dragPositionSec != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val isActive = java.util.concurrent.atomic.AtomicBoolean(true)
    private var myPlaybackGeneration: Int = -1

    private val seekController = SeekController(
        wrapper = wrapper,
        isActive = isActive,
        onFastForwardChanged = { ff -> _uiState.update { it.copy(isFastForwarding = ff) } },
        onSpeedChanged = { spd -> _uiState.update { it.copy(playbackSpeed = spd) } }
    )

    private val engineEventHandler by lazy { EngineEventHandler(wrapper, prefsRepository, viewModelScope) }

    var hasSurface = false
        private set

    private var currentUri: String = ""
    private var currentTitle: String = ""
    private var pendingUri: String? = null
    private var pendingSeekPosition: Long = 0L
    private var lastLoadedUri: String? = null
    private var wasPlayingBeforePause: Boolean = false

    fun consumePendingUri(): String? {
        val uri = pendingUri
        pendingUri = null
        return uri
    }

    private fun handleFileLoaded() {
        if (pendingSeekPosition > 0L) {
            wrapper.seekAccurate(pendingSeekPosition)
            pendingSeekPosition = 0L
        }
        viewModelScope.launch {
            val mode = prefsRepository.defaultDecoderFlow.first()
            wrapper.setDecoder(mode)
            val speed = prefsRepository.defaultSpeedFlow.first()
            wrapper.setSpeed(speed)
            _uiState.update {
                it.copy(
                    fileLoaded = true,
                    isLoading = false,
                    fitMode = VideoFitMode.FIT,
                    hwdecCurrent = hwdecLabel(mode),
                    playbackSpeed = speed
                )
            }
        }
        viewModelScope.launch {
            trackManager.applyPreferred()
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            trackManager.requestTrackReload(appContext)
        }
    }

    private fun handleEndFile(reason: Int) {
        if (reason == 3) {
            _uiState.update { it.copy(isPlaying = false, error = "Playback error") }
        } else {
            _uiState.update { it.copy(isPlaying = false) }
        }
        if (reason == 0) {
            seekController.resetFastForward()
            saveHistoryIfNeeded()
        }
    }

    fun saveHistoryIfNeeded() {
        val progress = _progressState.value
        historyManager.save(
            uri = currentUri,
            title = currentTitle,
            lastPlayedPositionSec = progress.positionSec,
            durationSec = progress.durationSec,
            lastAudioTrackId = trackManager.trackState.value.currentAudioTrackId,
            lastSubtitleTrackId = trackManager.trackState.value.currentSubtitleTrackId
        )
    }

    private fun executeLoadFile(uri: String, title: String, resumePosition: Long) {
        trackManager.clearTracks()
        lastLoadedUri = uri
        currentUri = uri
        currentTitle = title
        trackManager.resetAutoSubApplied()

        val initialName = if (title.isNotBlank()) title else "Video"
        _uiState.update {
            it.copy(
                fileName = initialName,
                isLoading = true,
                isPlaying = false,
                fileLoaded = false,
                error = null,
                videoWidth = 0,
                videoHeight = 0
            )
        }

        if (title.isBlank()) {
            viewModelScope.launch {
                val resolvedName = com.potato.player.util.MediaMetadataRepository.resolveFileName(appContext, uri)
                _uiState.update { it.copy(fileName = resolvedName) }
            }
        }

        pendingSeekPosition = resumePosition

        if (hasSurface) {
            wrapper.loadFile(uri)
        } else {
            pendingUri = uri
        }
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
            gesturesEnabled   = prefs.gesturesEnabled,
            lockButtonEnabled = prefs.lockButtonEnabled,
            controlsHideDelay = prefs.controlsHideDelay
        )}
        // default decoder and speed applied on file load, not here
    }

    fun attachSurface(surface: android.view.Surface) {
        hasSurface = true
        wrapper.attachSurface(surface)
        val uri = consumePendingUri()
        if (uri != null) wrapper.loadFile(uri)
    }

    fun detachSurface() {
        hasSurface = false
        wrapper.detachSurface()
    }

    fun prepareUri(defaultUri: String, defaultTitle: String = "") {
        if (lastLoadedUri == defaultUri) return
        lastLoadedUri = defaultUri

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val history = historyManager.getByUri(defaultUri)
            val resumePos = if (history != null && history.lastPlayedPositionSec > 0)
                (history.lastPlayedPositionSec * 1000).toLong() else 0L
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                executeLoadFile(defaultUri, defaultTitle, resumePos)
            }
        }
    }

    fun loadFile(uri: String, title: String = "", resumePosition: Long = 0L) {
        if (!isActive.get()) return
        executeLoadFile(uri, title, resumePosition)
    }

    fun togglePlay() {
        if (!isActive.get()) return
        wrapper.togglePlay()
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
        val locked = !_uiState.value.isLocked
        _uiState.update { it.copy(isLocked = locked) }
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

    fun startFastForward() = seekController.startFastForward(_uiState.value.playbackSpeed)
    fun stopFastForward()  = seekController.stopFastForward()

    fun onSliderDragStart(posSec: Double) {
        _progressState.update { it.copy(dragPositionSec = posSec) }
    }
    fun onSliderDragChange(posSec: Double) {
        if (!isActive.get()) return
    }
    fun onSliderDragEnd(posSec: Double) {
        if (!isActive.get()) return
        val ms = (posSec * 1000).toLong()
        _progressState.update { it.copy(dragPositionSec = null) }
        wrapper.seekFast(ms)
    }
    fun seekTo(positionSec: Double) {
        if (!isActive.get()) return
        val ms = (positionSec * 1000).toLong()
        wrapper.seekFast(ms)
    }

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
        saveHistoryIfNeeded()
        wrapper.stopIfGeneration(myPlaybackGeneration)
        wrapper.destroy()
        super.onCleared()
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
