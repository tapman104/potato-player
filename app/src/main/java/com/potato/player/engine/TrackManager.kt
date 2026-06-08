package com.potato.player.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Domain-level component that owns the media track concern.
 *
 * Sits on top of a [MediaEngine] (e.g. [ExoPlayerEngine]) and exposes a
 * track-focused view of the engine state. It separates track management
 * from playback concerns, so the playback engine can stay focused on
 * transport (play / pause / seek / speed) while this component handles
 * the orchestration of audio, subtitle, and video track inventory and
 * selection.
 *
 * Responsibilities:
 *  - Project the engine's [PlaybackState] into a track-only [TrackState].
 *  - Publish the track inventory and current selection as a [StateFlow]
 *    suitable for direct binding to a ViewModel or UI layer.
 *  - Provide a thin, intent-revealing API for selecting and clearing
 *    audio and subtitle tracks.
 *
 * Non-responsibilities (delegated to the engine):
 *  - Loading, decoding, and rendering media.
 *  - Engine lifecycle (open / release).
 *  - Position, buffering, and playback-phase reporting.
 *
 * Threading: methods that mutate engine state delegate to the engine,
 * which follows the engine's own threading contract (typically the
 * main / application looper for ExoPlayer-backed engines). The
 * [trackState] flow is collected on the caller-provided [scope].
 *
 * Lifecycle: this class does not own a scope. The caller provides a
 * [CoroutineScope] (typically a ViewModel or application scope) and is
 * responsible for cancelling it. When the scope is cancelled, the
 * internal collection stops; the last value of [trackState] remains
 * available to existing collectors.
 */
class TrackManager(
    private val engine: MediaEngine,
    scope: CoroutineScope,
) {
    /**
     * Current track inventory and selection, derived from the engine's
     * [MediaEngine.playbackState].
     *
     * Backed by [SharingStarted.WhileSubscribed], so the upstream
     * engine flow is only collected while there is at least one
     * subscriber. A short grace period keeps the latest value warm
     * across configuration changes (e.g. screen rotation) without
     * holding the engine subscription indefinitely.
     */
    val trackState: StateFlow<TrackState> = engine.playbackState
        .map { state ->
            TrackState(
                audioTracks = state.audioTracks,
                subtitleTracks = state.subtitleTracks,
                videoTracks = state.videoTracks,
                selectedAudioTrackId = state.selectedAudioTrackId,
                selectedSubtitleTrackId = state.selectedSubtitleTrackId,
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = TrackState.Initial,
        )

    /**
     * Select an audio track by its engine-agnostic ID.
     *
     * Delegates to [MediaEngine.selectAudioTrack]. IDs that do not
     * match any entry in the current track inventory are ignored by
     * the engine.
     */
    fun selectAudioTrack(trackId: String) {
        engine.selectAudioTrack(trackId)
    }

    /**
     * Select a subtitle track by its engine-agnostic ID.
     *
     * Delegates to [MediaEngine.selectSubtitleTrack]. To disable
     * subtitles entirely, use [clearSubtitleTrack].
     */
    fun selectSubtitleTrack(trackId: String) {
        engine.selectSubtitleTrack(trackId)
    }

    /**
     * Clear the subtitle track override, effectively disabling
     * subtitles. Delegates to [MediaEngine.clearSubtitleTrack].
     */
    fun clearSubtitleTrack() {
        engine.clearSubtitleTrack()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Immutable snapshot of the track inventory and current selection.
 *
 * This is the single source of truth for track-related UI binding.
 * It deliberately omits playback-only fields (position, phase,
 * buffering, speed) so that consumers can observe track state
 * independently of the playback transport.
 *
 * @property audioTracks Available audio tracks. Empty for sources
 * without an audio stream.
 * @property subtitleTracks Available subtitle / text tracks. Empty
 * for sources without embedded text.
 * @property videoTracks Available video tracks / renditions. May
 * contain a single entry for non-adaptive sources.
 * @property selectedAudioTrackId ID of the currently active audio
 * track, or `null` if the engine is using its default selection.
 * @property selectedSubtitleTrackId ID of the currently active
 * subtitle track, or `null` if subtitles are disabled.
 */
data class TrackState(
    val audioTracks: List<AudioTrack>,
    val subtitleTracks: List<SubtitleTrack>,
    val videoTracks: List<VideoTrack>,
    val selectedAudioTrackId: String?,
    val selectedSubtitleTrackId: String?,
) {
    companion object {
        /** Default value used before any source is opened. */
        val Initial: TrackState = TrackState(
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
            videoTracks = emptyList(),
            selectedAudioTrackId = null,
            selectedSubtitleTrackId = null,
        )
    }
}
