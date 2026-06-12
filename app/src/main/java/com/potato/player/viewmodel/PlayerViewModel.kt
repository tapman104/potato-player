package com.potato.player.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.potato.player.player.ui.state.OrientationMode
import com.potato.player.player.ui.state.PlayerControlsState
import com.potato.player.engine.AudioTrack
import com.potato.player.engine.ExoPlayerEngine
import com.potato.player.engine.MediaEngine
import com.potato.player.engine.MediaEvent
import com.potato.player.engine.MediaPhase
import com.potato.player.engine.SubtitleTrack
import com.potato.player.engine.VideoTrack

/**
 * Bridges [MediaEngine] to the player UI.
 *
 * Responsibilities:
 *  - Expose a single [PlayerUiState] flow the UI can bind to directly.
 *  - Expose [controlsState] for overlay-specific UI concerns (rotation lock,
 *    controls visibility) that are independent of the engine.
 *  - Forward user intents (play, pause, seek, track selection) to the engine.
 *  - Handle one-time [MediaEvent]s (errors, completion) via the events flow.
 *
 * The ViewModel does not hold a reference to any Android View or
 * Composable. It owns no coroutine scope beyond [viewModelScope].
 *
 * @param engine The engine to drive. In production, inject via a
 *               factory or Hilt. The engine is released in [onCleared].
 */
class PlayerViewModel(
    private val engine: MediaEngine,
) : ViewModel() {

    val playerViewPlayer: Player? = (engine as? ExoPlayerEngine)?.playerViewPlayer

    /**
     * Single source of truth for all player UI.
     *
     * Derived from [MediaEngine.playbackState] so the ViewModel stays
     * thin: it maps the engine's domain state to a UI-shaped model
     * rather than duplicating state.
     *
     * [SharingStarted.WhileSubscribed] with a 5-second stop timeout
     * keeps the upstream subscription alive across configuration changes
     * without leaking it indefinitely when the screen is gone.
     */
    val uiState: StateFlow<PlayerUiState> = engine.playbackState
        .map { state ->
            PlayerUiState(
                isPlaying = state.isPlaying,
                isLoading = state.phase is MediaPhase.Loading,
                isEnded = state.phase is MediaPhase.Ended,
                playbackSpeed = state.playbackSpeed,
                canPlay = state.phase.canPlay(),
                audioTracks = state.audioTracks,
                subtitleTracks = state.subtitleTracks,
                videoTracks = state.videoTracks,
                selectedAudioTrackId = state.selectedAudioTrackId,
                selectedSubtitleTrackId = state.selectedSubtitleTrackId,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_TIMEOUT_MS),
            initialValue = PlayerUiState.Initial,
        )

    val positionState: StateFlow<PlayerPositionState> = engine.playbackState
        .map { state ->
            PlayerPositionState(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                bufferedPositionMs = state.bufferedPositionMs,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_TIMEOUT_MS),
            initialValue = PlayerPositionState(0L, 0L, 0L),
        )

    /**
     * UI-only overlay state (controls visibility, rotation lock, orientation
     * mode). Backed by a [MutableStateFlow] because these fields are driven
     * entirely by user intent rather than engine output.
     *
     * [PlayerScreen] observes this to:
     *  - Show or hide the controls overlay.
     *  - Fire a [LaunchedEffect] that calls
     *    `Activity.requestedOrientation` whenever [PlayerControlsState.rotationLocked]
     *    or [PlayerControlsState.orientationMode] changes.
     */
    private val _controlsState = MutableStateFlow(PlayerControlsState.Initial)
    val controlsState: StateFlow<PlayerControlsState> = _controlsState.asStateFlow()

    // [CHANGE 1] Video dimensions inferred from the first VideoTrack in playbackState.
    // NOTE: VideoSizeChanged is not yet in the MediaEvent sealed interface — skip event
    // collection for now. Dimensions are read lazily in applyAutoOrientation().
    private val _videoSize = MutableStateFlow(Pair(0, 0)) // [CHANGE 2]

    // [CHANGE 3] Last screen size cached by the UI so toggleRotationLock can re-orient.
    private var lastScreenW: Int = 0 // [CHANGE 4]
    private var lastScreenH: Int = 0 // [CHANGE 5]

    /**
     * Raw event stream. The UI layer (PlayerScreen) collects this with
     * [LaunchedEffect] and handles one-time effects like showing a
     * Snackbar on error or navigating away on completion.
     */
    val events = engine.events

    // region Intents

    /** Open a URI for playback. Replaces any currently loaded source. */
    fun open(uri: Uri) {
        engine.open(uri)
    }

    /** Toggle between play and pause. When playback has ended, restarts from the beginning. */
    fun togglePlayPause() {
        val state = uiState.value
        when {
            state.isEnded   -> engine.play() // replay — engine seeks to 0 and re-prepares
            state.isPlaying -> engine.pause()
            else            -> engine.play()
        }
    }

    /** Start playback explicitly. */
    fun play() {
        engine.play()
    }

    /** Pause playback explicitly. */
    fun pause() {
        engine.pause()
    }

    /**
     * Seek to [positionMs].
     *
     * Called on every scrub gesture tick. The engine debounces
     * internally and only emits [MediaEvent.SeekCompleted] for the
     * final landing position.
     */
    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
    }

    /** Set the playback speed multiplier. Values <= 0 or NaN are ignored. */
    fun setPlaybackSpeed(speed: Float) {
        engine.setPlaybackSpeed(speed)
    }

    /**
     * Sets the engine's position polling rate.
     * Call with 100L during seek scrub, 1000L on release.
     */
    fun setPositionUpdateRate(intervalMs: Long) {
        (engine as? ExoPlayerEngine)?.setPositionUpdateRate(intervalMs)
    }

    /**
     * Notifies the engine whether the controls overlay is currently visible.
     *
     * When controls are hidden the seek bar is invisible, so the engine
     * reduces its position-polling frequency from 1 s to 2 s, halving
     * the number of coroutine wake-ups during undisturbed playback.
     */
    fun setControlsVisible(visible: Boolean) {
        (engine as? ExoPlayerEngine)?.setControlsVisible(visible)
    }

    /**
     * Seek forward by [SEEK_INTERVAL_MS] (10 seconds), clamped to the end
     * of the source so the engine never receives an out-of-range position.
     */
    fun seekForward10() {
        engine.seekRelative(SEEK_INTERVAL_MS)
    }

    /**
     * Seek backward by [SEEK_INTERVAL_MS] (10 seconds), clamped to 0
     * so the engine never receives a negative position.
     */
    fun seekBackward10() {
        engine.seekRelative(-SEEK_INTERVAL_MS)
    }

    /**
     * Select an audio track by its engine-agnostic [id].
     * Delegates directly to [MediaEngine.selectAudioTrack]; unknown IDs
     * are silently ignored by the engine.
     */
    fun selectAudioTrack(id: String) {
        engine.selectAudioTrack(id)
    }

    /**
     * Select a subtitle track by its engine-agnostic [id].
     * To turn subtitles off, call [disableSubtitles] instead.
     */
    fun selectSubtitleTrack(id: String) {
        engine.selectSubtitleTrack(id)
    }

    /**
     * Disable subtitles by clearing the engine's subtitle track override.
     * Mirrors the "Off" option shown by
     * [com.potato.player.player.ui.dialog.SubtitleTrackDialog].
     */
    fun disableSubtitles() {
        engine.clearSubtitleTrack()
    }

    /**
     * Toggle the rotation-lock flag in [controlsState].
     *
     * This is a **pure state mutation** â€” no Android API is called here.
     * [PlayerScreen] observes [controlsState] and fires a
     * `LaunchedEffect` that calls `Activity.requestedOrientation` with the
     * appropriate constant whenever the flag changes.
     */
    fun cycleRotationMode() {
        _controlsState.update { current ->
            val nextMode = when (current.orientationMode) {
                OrientationMode.AUTO             -> OrientationMode.LOCKED_LANDSCAPE
                OrientationMode.LOCKED_LANDSCAPE -> OrientationMode.LOCKED_PORTRAIT
                OrientationMode.LOCKED_PORTRAIT  -> OrientationMode.AUTO
            }
            current.copy(
                orientationMode = nextMode,
                rotationLocked = nextMode != OrientationMode.AUTO,
            )
        }
    }

    /**
     * Store the current screen dimensions so [toggleRotationLock] can call
     * [applyAutoOrientation] without a screen-size parameter when unlocking.
     *
     * Called from [PlayerScreen] inside the `LaunchedEffect(uri)` block.
     */
    fun cacheScreenSize(w: Int, h: Int) { // [CHANGE 12]
        lastScreenW = w // [CHANGE 13]
        lastScreenH = h // [CHANGE 14]
    }

    /**
     * Cycles the video resize mode (FIT -> FILL -> ZOOM -> FIT).
     */
    fun cycleResizeMode() {
        _controlsState.update { current ->
            current.copy(resizeMode = current.resizeMode.next())
        }
    }

    /**
     * Compare the loaded video's aspect ratio against the screen and update
     * [controlsState] to the matching [OrientationMode].
     *
     * - Portrait video  → [OrientationMode.LOCKED_PORTRAIT]
     * - Landscape video → [OrientationMode.LOCKED_LANDSCAPE]
     * - Square / unknown → no change
     *
     * No-ops when either screen dimension is 0, video dimensions are unknown,
     * or the user has manually locked rotation ([PlayerControlsState.rotationLocked]).
     *
     * NOTE: VideoSizeChanged is not yet in the MediaEvent sealed interface.
     * Dimensions are read from the first [VideoTrack] in [playbackState] instead.
     */
    fun applyAutoOrientation(screenW: Int, screenH: Int, defaultOrientation: OrientationMode = OrientationMode.AUTO) { // [CHANGE 15]
        if (defaultOrientation != OrientationMode.AUTO) {
            _controlsState.update { it.copy(orientationMode = defaultOrientation, rotationLocked = true) }
            return
        }

        if (_controlsState.value.rotationLocked) return // [CHANGE 16]

        if (screenW == 0 || screenH == 0) return // [CHANGE 17]
        // [CHANGE 18] Prefer _videoSize if populated; fall back to first VideoTrack.
        val (rawW, rawH) = _videoSize.value.let { (w, h) -> // [CHANGE 19]
            if (w > 0 && h > 0) Pair(w, h) // [CHANGE 20]
            else { // [CHANGE 21]
                val first = engine.playbackState.value.videoTracks.firstOrNull() // [CHANGE 22]
                Pair(first?.width ?: 0, first?.height ?: 0) // [CHANGE 23]
            }
        }
        if (rawW == 0 || rawH == 0) return // [CHANGE 24]
        val mode = when { // [CHANGE 25]
            rawW > rawH -> OrientationMode.LOCKED_LANDSCAPE // [CHANGE 26]
            rawH > rawW -> OrientationMode.LOCKED_PORTRAIT  // [CHANGE 27]
            else        -> return // square — leave current orientation // [CHANGE 28]
        }
        _controlsState.update { it.copy(orientationMode = mode) } // [CHANGE 29]
    }

    // endregion

    fun release() {
        engine.release()
    }

    override fun onCleared() {
        super.onCleared()
        release()
    }

    private var wasPlayingBeforeBackground = false

    /**
     * Called from [MainActivity.onStop].
     *
     * If [backgroundPlayback] is enabled the engine continues running and audio
     * keeps playing (the service / MediaSession keeps the session alive).
     * Otherwise playback is paused immediately to conserve battery.
     *
     * Surface lifecycle is managed entirely by PlayerView via its own
     * SurfaceHolder.Callback — do not touch the surface here.
     */
    fun onBackground(backgroundPlayback: Boolean) {
        wasPlayingBeforeBackground = uiState.value.isPlaying
        if (!backgroundPlayback) {
            engine.pause()
        }
    }

    /**
     * Called from [MainActivity.onStart]. Resumes playback if the source
     * has not yet ended AND we paused on background. If background playback
     * is enabled the engine never paused, so we simply do nothing.
     */
    fun onForeground(backgroundPlayback: Boolean) {
        if (!backgroundPlayback && !uiState.value.isEnded && wasPlayingBeforeBackground) {
            engine.play()
        }
    }

    private companion object {
        const val SUBSCRIPTION_STOP_TIMEOUT_MS = 5_000L
        /** Fixed seek-step used by [seekForward10] and [seekBackward10], in ms. */
        const val SEEK_INTERVAL_MS = 10_000L
    }
}

// ---------------------------------------------------------------------------------------
// Private extension
// ---------------------------------------------------------------------------------------

/**
 * Returns true when the phase indicates the engine can begin or
 * resume playback immediately (sufficient buffer available).
 */
private fun MediaPhase.canPlay(): Boolean = when (this) {
    is MediaPhase.Ready -> canPlay
    else -> false
}

// ---------------------------------------------------------------------------------------
// UI model
// ---------------------------------------------------------------------------------------

/**
 * Immutable UI model consumed by the player Composables.
 *
 * Only carries what the UI actually needs. Business logic (track
 * selection, error codes) is handled separately; this model stays
 * shallow so recomposition is cheap.
 *
 * @property isPlaying True when the engine is actively rendering.
 * @property isLoading True while the source is preparing.
 * @property isEnded True when playback reached end-of-file.
 * @property playbackSpeed Current speed multiplier.
 * @property canPlay True when the engine can start playing immediately.
 * @property audioTracks Available audio tracks for the current source.
 * @property subtitleTracks Available subtitle tracks for the current source.
 * @property videoTracks Available video tracks for the current source.
 * @property selectedAudioTrackId ID of the active audio track, or `null` for default.
 * @property selectedSubtitleTrackId ID of the active subtitle track, or `null` if disabled.
 */
data class PlayerUiState(
    val isPlaying: Boolean,
    val isLoading: Boolean,
    val isEnded: Boolean,
    val playbackSpeed: Float,
    val canPlay: Boolean,
    val audioTracks: List<AudioTrack>,
    val subtitleTracks: List<SubtitleTrack>,
    val videoTracks: List<VideoTrack>,
    val selectedAudioTrackId: String?,
    val selectedSubtitleTrackId: String?,
) {
    companion object {
        val Initial = PlayerUiState(
            isPlaying = false,
            isLoading = false,
            isEnded = false,
            playbackSpeed = 1.0f,
            canPlay = false,
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
            videoTracks = emptyList(),
            selectedAudioTrackId = null,
            selectedSubtitleTrackId = null,
        )
    }
}

data class PlayerPositionState(
    val positionMs: Long,
    val durationMs: Long,
    val bufferedPositionMs: Long,
) {
    /** Progress ratio [0f, 1f], or null if duration is unknown. */
    val progress: Float?
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else null

    /** Buffered-progress ratio [0f, 1f], or null if duration is unknown. */
    val bufferedProgress: Float?
        get() = if (durationMs > 0L) {
            (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else null
}
