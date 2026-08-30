package com.potato.player.feature.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.data.VideoHistory
import com.potato.player.engine.MpvWrapper
import com.potato.player.engine.MpvEvent
import com.potato.player.engine.MpvEventId
import com.potato.player.engine.MpvProp
import com.potato.player.engine.TrackInfo
import com.potato.player.engine.TrackListParser
import com.potato.player.engine.TrackType
import com.potato.player.engine.PlayerEngineState

import com.potato.player.feature.player.state.*
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
    private val historyManager: PlaybackHistoryManager
) : ViewModel() {

    private val prefsRepository by lazy { UserPreferencesRepository(appContext) }
    
    val dialogState = DialogStateHolder()
    val playlistManager = PlaylistManager()
    val trackManager by lazy { TrackManager(prefsRepository, viewModelScope) }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(PlaybackProgressState())
    val progressState: StateFlow<PlaybackProgressState> = _progressState.asStateFlow()

    private val _gestureState = MutableStateFlow(PlayerGestureState())
    val gestureState: StateFlow<PlayerGestureState> = _gestureState.asStateFlow()

    private val isActive = java.util.concurrent.atomic.AtomicBoolean(true)
    private var pendingUri: String? = null
    private var mySurface: android.view.Surface? = null

    private var pendingSeekPosition: Long = 0L

    private var wasPlayingBeforePause: Boolean = false

    private var currentUri = ""
    private var currentTitle = ""
    private var myPlaybackGeneration: Int = -1
    
    private var normalPlaybackSpeed = 1.0
    private var isSliderSeeking = false

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

    private fun handleEngineState(state: PlayerEngineState) {
        val isBuffering = state.pausedForCache || state.cacheBufferingState < 100
        _uiState.update { it.copy(
            isPlaying = !state.paused,
            isLoading = it.fileLoaded && isBuffering,
            hwdecCurrent = state.hwdecActive,
            videoWidth = state.videoWidth.toInt(),
            videoHeight = state.videoHeight.toInt(),
            playbackSpeed = state.speed,
            subScale = state.subScale,
            subPos = state.subPos.toInt()
        ) }
        
        if (!isSliderSeeking) {
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

    private fun applyPrefs(prefs: Triple<Double, Int, Boolean>) {
        val (scale, pos, autoRot) = prefs
        wrapper.setSubScale(scale)
        wrapper.setSubPos(pos)
        _uiState.update { it.copy(subScale = scale, subPos = pos, isAutoRotation = autoRot) }
    }





    private fun loadTracks() = trackManager.loadTracks(wrapper, appContext)
    private fun applyPreferredSubtitleTrack() = trackManager.applyPreferred { id -> wrapper.setSubTrack(id) }

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

    private var lastLoadedUri: String? = null

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
        loadTracks()
        viewModelScope.launch {
            applyPreferredSubtitleTrack()
        }
    }

    private fun handleEndFile(reason: Int) {
        if (reason == 3) {
            _uiState.update { it.copy(isPlaying = false, error = "Playback error") }
        } else if (reason == 0) {
            _uiState.update { it.copy(isPlaying = false) }
            saveHistoryIfNeeded()
            if (_uiState.value.isFastForwarding) {
                _uiState.update { it.copy(isFastForwarding = false) }
                wrapper.setSpeed(normalPlaybackSpeed)
            }
        } else {
            _uiState.update { it.copy(isPlaying = false) }
        }
    }

    private fun handlePlaybackRestart() {
        _uiState.update { it.copy(isLoading = false) }
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
                wrapper.setPropertyString(MpvProp.VIDEO_ASPECT_OVERRIDE, "-1")
                wrapper.setPropertyString(MpvProp.PANSCAN, "0.0")
            }
            VideoFitMode.FILL -> {
                wrapper.setPropertyString(MpvProp.PANSCAN, "1.0")
                wrapper.setPropertyString(MpvProp.VIDEO_ASPECT_OVERRIDE, "-1")
            }
            VideoFitMode.STRETCH -> {
                wrapper.setPropertyString(MpvProp.PANSCAN, "0.0")
                val metrics = appContext.resources.displayMetrics
                val screenWidth = metrics.widthPixels
                val screenHeight = metrics.heightPixels
                wrapper.setPropertyString(MpvProp.VIDEO_ASPECT_OVERRIDE, "${screenWidth}/${screenHeight}")
            }
        }
    }

    fun setDecoder(mode: String) {
        if (!isActive.get()) return
        val hwdec = when (mode) { "no" -> "SW"; "mediacodec" -> "HW"; else -> "HW+" }
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
        isSliderSeeking = true
        _progressState.update { it.copy(dragPositionSec = posSec) }
    }

    fun onSliderDragChange(posSec: Double) {
        if (!isActive.get()) return
        _progressState.update { it.copy(dragPositionSec = posSec) }
    }

    fun onSliderDragEnd(posSec: Double) {
        if (!isActive.get()) return
        isSliderSeeking = false
        wrapper.seekAccurate((posSec * 1000).toLong())
        _progressState.update { it.copy(dragPositionSec = null) }
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
            wrapper.seekAccurate((target * 1000).toLong())
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
        wrapper.setSubScale(scale)
        wrapper.setSubPos(pos)
    }

    fun setSubtitleAppearance(scale: Double, pos: Int) {
        wrapper.setSubScale(scale)
        wrapper.setSubPos(pos)
        viewModelScope.launch { prefsRepository.saveSubtitleAppearance(scale, pos) }
    }

    fun resetSubtitleAppearance() = setSubtitleAppearance(
        scale = UserPreferencesRepository.DEFAULT_SUB_SCALE,
        pos = UserPreferencesRepository.DEFAULT_SUB_POS
    )

    fun showDialog(dialog: ActiveDialog) = dialogState.show(dialog)
    fun dismissDialog() = dialogState.dismiss()

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
        super.onCleared()
        saveHistoryIfNeeded()
        wrapper.stopIfGeneration(myPlaybackGeneration)
    }

    fun setVolume(volume: Int) {
        if (!isActive.get()) return
        val clamped = volume.coerceIn(0, 150)
        wrapper.setPropertyInt(MpvProp.VOLUME, clamped)
    }

    fun setVideoZoom(zoom: Float, panX: Float, panY: Float) {
        if (!isActive.get()) return
        val clampedZoom = zoom.coerceIn(1.0f, 4.0f)
        val finalPanX = if (clampedZoom == 1.0f) 0f else panX
        val finalPanY = if (clampedZoom == 1.0f) 0f else panY
        
        val mpvZoom = kotlin.math.ln(clampedZoom.toDouble()) / kotlin.math.ln(2.0)
        wrapper.setPropertyDouble(MpvProp.VIDEO_ZOOM, mpvZoom)
        wrapper.setPropertyDouble(MpvProp.VIDEO_PAN_X, finalPanX.toDouble())
        wrapper.setPropertyDouble(MpvProp.VIDEO_PAN_Y, finalPanY.toDouble())
        
        _gestureState.update { it.copy(videoZoom = clampedZoom, videoPanX = finalPanX, videoPanY = finalPanY) }
    }

    fun resetZoom() {
        setVideoZoom(1.0f, 0f, 0f)
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
