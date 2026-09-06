package com.potato.player.feature.player

import android.content.Context
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.engine.MpvWrapper
import com.potato.player.feature.player.state.PlaybackProgressState
import com.potato.player.util.MediaMetadataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns all playback-session lifecycle state that was previously scattered
 * across PlayerViewModel:
 *   - current URI / title tracking
 *   - pending-URI handshake with surface lifecycle
 *   - pending seek-position on load
 *   - pause/resume memory
 *   - history save on end-file / pause / cleared
 *
 * Constructor callbacks let the ViewModel update _uiState without coupling
 * this class to MutableStateFlow.
 */
class PlaybackSessionManager(
    private val wrapper: MpvWrapper,
    private val prefsRepository: UserPreferencesRepository,
    private val historyManager: PlaybackHistoryManager,
    private val trackManager: TrackManager,
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val orientationManager: OrientationManager,
    /** Returns true when the render surface is currently attached. */
    private val hasSurface: () -> Boolean,
    /** Returns the current isPlaying flag from _uiState. */
    private val isPlaying: () -> Boolean,
    /** Returns the latest PlaybackProgressState snapshot. */
    private val getProgressState: () -> PlaybackProgressState,
    /**
     * Called at the start of a load with the display name.
     * First call: initialName (title or "Video").
     * Second call (async, only when title was blank): resolved metadata name.
     */
    private val onFileLoading: (fileName: String) -> Unit,
    /**
     * Called when MPV fires FileLoaded and prefs have been applied.
     * @param hwdecLabel current decoder label.
     * @param speed      playback speed from prefs.
     */
    private val onFileLoaded: (hwdecLabel: String, speed: Double) -> Unit,
    /**
     * Called when MPV fires EndFile.
     * @param reason raw MPV end-file reason code (0 = normal, 3 = error).
     */
    private val onEndFile: (reason: Int) -> Unit,
) {

    // ── Session state (moved from PlayerViewModel) ────────────────────────────
    private var currentUri: String = ""
    private var currentTitle: String = ""
    private var pendingUri: String? = null
    private var pendingSeekPosition: Long = 0L
    private var lastLoadedUri: String? = null
    private var wasPlayingBeforePause: Boolean = false

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Entry point — replaces PlayerViewModel.prepareUri().
     * Looks up history for a resume position on IO, then loads on Main.
     */
    fun load(uri: String, title: String = "") {
        if (lastLoadedUri == uri) return
        lastLoadedUri = uri

        scope.launch(Dispatchers.IO) {
            val history = historyManager.getByUri(uri)
            val resumePos = if (history != null && history.lastPlayedPositionSec > 0)
                (history.lastPlayedPositionSec * 1000).toLong() else 0L
            withContext(Dispatchers.Main) {
                executeLoadFile(uri, title, resumePos)
            }
        }
    }

    /**
     * Immediate load without history look-up (used by playlist navigation).
     */
    fun loadDirect(uri: String, title: String = "", resumePosition: Long = 0L) {
        executeLoadFile(uri, title, resumePosition)
    }

    /**
     * Returns and atomically clears the pending URI.
     * Called by PlayerViewModel.handleSurfaceReady after the surface is ready.
     */
    fun consumePendingUri(): String? {
        val uri = pendingUri
        pendingUri = null
        return uri
    }

    /** Called by PlayerViewModel when EngineEventHandler fires FileLoaded. */
    fun onFileLoaded() {
        handleFileLoaded()
    }

    /** Called by PlayerViewModel when EngineEventHandler fires EndFile. */
    fun onEndFile(reason: Int) {
        handleEndFile(reason)
    }

    fun onPlayerPause() {
        wasPlayingBeforePause = isPlaying()
        wrapper.pause()
        saveHistoryIfNeeded()
    }

    fun onPlayerResume() {
        if (wasPlayingBeforePause) {
            wrapper.resume()
        }
    }

    /** Persist history for the current session. Safe to call at any time. */
    fun saveHistoryIfNeeded() {
        val progress = getProgressState()
        historyManager.save(
            uri = currentUri,
            title = currentTitle,
            lastPlayedPositionSec = progress.positionSec,
            durationSec = progress.durationSec,
            lastAudioTrackId = trackManager.trackState.value.currentAudioTrackId,
            lastSubtitleTrackId = trackManager.trackState.value.currentSubtitleTrackId
        )
    }

    // ── Private implementation ────────────────────────────────────────────────

    private fun executeLoadFile(uri: String, title: String, resumePosition: Long) {
        trackManager.clearTracks()
        lastLoadedUri = uri
        currentUri = uri
        currentTitle = title
        orientationManager.clearDimensions()
        trackManager.resetAutoSubApplied()

        val initialName = if (title.isNotBlank()) title else "Video"
        onFileLoading(initialName)

        if (title.isBlank()) {
            scope.launch {
                val resolvedName = MediaMetadataRepository.resolveFileName(appContext, uri)
                onFileLoading(resolvedName)
            }
        }

        pendingSeekPosition = resumePosition

        if (hasSurface()) {
            wrapper.loadFile(uri)
        } else {
            pendingUri = uri
        }
    }

    private fun handleFileLoaded() {
        if (pendingSeekPosition > 0L) {
            wrapper.seekAccurate(pendingSeekPosition)
            pendingSeekPosition = 0L
        }

        scope.launch {
            // Apply default decoder and speed from prefs, then notify ViewModel.
            val mode = prefsRepository.defaultDecoderFlow.first()
            wrapper.setDecoder(mode)
            val speed = prefsRepository.defaultSpeedFlow.first()
            wrapper.setSpeed(speed)
            onFileLoaded(hwdecLabel(mode), speed)
        }

        scope.launch {
            trackManager.applyPreferred()
        }

        // Fix 2: Force track list reload after a short delay to ensure MPV has populated
        // track-list. This resolves the infinite spinner when MPV does not re-fire TRACK_LIST.
        scope.launch {
            delay(500)
            trackManager.requestTrackReload(appContext)
        }
    }

    private fun handleEndFile(reason: Int) {
        if (reason == 0) {
            saveHistoryIfNeeded()
        }
    }
}
