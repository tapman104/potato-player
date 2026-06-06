package mediaengine

import android.net.Uri
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Engine-agnostic abstraction for a media playback engine.
 *
 * Implementations are expected to be safe to invoke from the main thread,
 * matching the convention used by Android media APIs. Long-running work
 * (source preparation, decoding warm-up, etc.) is performed internally;
 * consumers should observe [playbackState] for progress and [events] for
 * one-time signals.
 *
 * Typical lifecycle:
 * ```
 * open(uri)
 *   -> play() / pause() / seekTo() / setPlaybackSpeed()
 *   -> selectXxxTrack() / clearXxxTrack()
 *   -> release()
 * ```
 *
 * The contract is intentionally framework-neutral: ExoPlayer is used in
 * Phase 1, but a later FFmpeg-backed implementation must be drop-in.
 */
interface MediaEngine {

    /**
     * Latest playback snapshot. Always replays the current value to
     * new collectors, making it safe to bind directly to a ViewModel.
     *
     * Implementations decide the sampling cadence for [PlaybackState.positionMs]
     * and [PlaybackState.bufferedPositionMs]; consumers should not assume
     * a fixed update frequency.
     */
    val playbackState: StateFlow<PlaybackState>

    /**
     * Stream of one-time side effects: completion, errors, track changes.
     *
     * Each event is delivered exactly once. Implementations should use a
     * `replay = 0` [SharedFlow] and an `extraBufferCapacity` that is
     * large enough to absorb short-lived collector gaps so that events
     * such as [MediaEvent.PlaybackCompleted] are not lost.
     */
    val events: SharedFlow<MediaEvent>

    // region Lifecycle

    /**
     * Prepare a media source for playback.
     *
     * Transitions [playbackState] to [MediaPhase.Loading], then to
     * [MediaPhase.Ready] once the source is ready. On failure, a
     * [MediaEvent.Error] is emitted and the phase returns to
     * [MediaPhase.Idle] (or remains in [MediaPhase.Ended] if applicable).
     *
     * Calling this while a source is already open replaces the current
     * source and resets the playback position.
     */
    fun open(uri: Uri)

    /**
     * Start or resume playback. No-op if no source is open or if
     * playback has already ended.
     */
    fun play()

    /**
     * Pause playback. Position is preserved; the next [play] call
     * resumes from the same position.
     */
    fun pause()

    /**
     * Seek to an absolute position, in milliseconds.
     *
     * Out-of-range values should be clamped to the valid range
     * `[0, durationMs]`. A [MediaEvent.SeekCompleted] is emitted when
     * the seek actually lands in the player; transient seeks that are
     * superseded by newer ones should not emit completion events.
     */
    fun seekTo(positionMs: Long)

    /**
     * Set the playback speed multiplier. `1.0f` is normal speed,
     * `2.0f` is double speed, `0.5f` is half speed. Values `<= 0`
     * or `NaN` are invalid and must be ignored.
     */
    fun setPlaybackSpeed(speed: Float)

    /**
     * Release all resources held by the engine. After this call the
     * engine must not be used again; doing so is undefined behaviour.
     *
     * Implementations must be tolerant of repeated calls.
     */
    fun release()

    // endregion

    // region Track Management

    /**
     * Select an audio track by its engine-agnostic ID (see [AudioTrack.id]).
     *
     * If [trackId] does not match any entry in [PlaybackState.audioTracks]
     * the call is ignored. On success, [MediaEvent.TrackChanged] is emitted
     * and [PlaybackState.selectedAudioTrackId] is updated.
     */
    fun selectAudioTrack(trackId: String)

    /**
     * Select a subtitle track by its engine-agnostic ID. To disable
     * subtitles entirely, use [clearSubtitleTrack].
     */
    fun selectSubtitleTrack(trackId: String)

    /**
     * Select a video track by its engine-agnostic ID. Primarily useful
     * for overriding adaptive bitrate selection; pass `null` or call
     * [clearVideoTrack] to let the engine decide.
     */
    fun selectVideoTrack(trackId: String)

    /**
     * Clear the audio track override, falling back to the engine's
     * default track selection.
     */
    fun clearAudioTrack()

    /**
     * Clear the subtitle track override, effectively disabling subtitles.
     */
    fun clearSubtitleTrack()

    /**
     * Clear the video track override, re-enabling the engine's default
     * selection (typically adaptive bitrate).
     */
    fun clearVideoTrack()

    // endregion
}

// ---------------------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------------------

/**
 * High-level playback phase. Modelled as a sealed hierarchy so consumers
 * can exhaustively match on it.
 */
sealed interface MediaPhase {
    /** Engine exists but holds no source. Initial state and post-release state. */
    data object Idle : MediaPhase

    /** Source is being prepared (e.g. opening, fetching manifests, pre-rolling). */
    data object Loading : MediaPhase

    /**
     * Source is ready to play.
     *
     * @param canPlay `true` when the engine can play immediately on
     * `play()` (i.e. sufficient buffer). `false` when still buffering
     * in paused state.
     */
    data class Ready(val canPlay: Boolean) : MediaPhase

    /** Playback reached the end of the current source. */
    data object Ended : MediaPhase
}

/**
 * Immutable snapshot of the engine's playback state.
 *
 * Consumers should treat this as the single source of truth for UI binding.
 * Position-related fields are sampled by the implementation; treat them as
 * eventually consistent.
 *
 * @property phase Current high-level phase.
 * @property isPlaying `true` when the engine is actively rendering frames
 * (not just Ready). Reflects ExoPlayer's `isPlaying` semantic.
 * @property positionMs Current playback position, in milliseconds.
 * @property durationMs Total duration of the source, or `0` if unknown
 * (e.g. live streams without a fixed end).
 * @property bufferedPositionMs Position up to which media is buffered, in
 * milliseconds. `0` if buffering has not started.
 * @property playbackSpeed Current speed multiplier. Always `>= 0`.
 * @property audioTracks Available audio tracks. Stable while the source is
 * loaded; may be empty for audio-only sources.
 * @property subtitleTracks Available subtitle / text tracks. May be empty
 * for sources without embedded text.
 * @property videoTracks Available video tracks. May contain a single entry
 * for non-adaptive sources.
 * @property selectedAudioTrackId ID of the currently active audio track, or
 * `null` if the engine is using its default selection.
 * @property selectedSubtitleTrackId ID of the currently active subtitle
 * track, or `null` if subtitles are disabled.
 * @property selectedVideoTrackId ID of the currently active video track, or
 * `null` if the engine is using its default (e.g. ABR) selection.
 */
data class PlaybackState(
    val phase: MediaPhase,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val bufferedPositionMs: Long,
    val playbackSpeed: Float,
    val audioTracks: List<AudioTrack>,
    val subtitleTracks: List<SubtitleTrack>,
    val videoTracks: List<VideoTrack>,
    val selectedAudioTrackId: String?,
    val selectedSubtitleTrackId: String?,
    val selectedVideoTrackId: String?,
) {
    companion object {
        /** Default value used before any source is opened. */
        val Initial: PlaybackState = PlaybackState(
            phase = MediaPhase.Idle,
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            bufferedPositionMs = 0L,
            playbackSpeed = 1.0f,
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
            videoTracks = emptyList(),
            selectedAudioTrackId = null,
            selectedSubtitleTrackId = null,
            selectedVideoTrackId = null,
        )
    }
}

// ---------------------------------------------------------------------------------------
// Track domain models
// ---------------------------------------------------------------------------------------

/**
 * Engine-agnostic description of an audio track.
 *
 * @property id Stable, opaque identifier. Must be unique within a given
 * source. Derived from stable track metadata by the implementation and
 * must not be relied on by consumers beyond equality checks.
 * @property label Human-readable label, suitable for direct display.
 * Falls back to a synthesised description if the source does not provide one.
 * @property language BCP-47 language tag, or `null` if unknown.
 * @property codec Short codec name (e.g. `mp4a.40.2`, `opus`), or `null`.
 * @property bitrate Average bitrate in bits-per-second, or `null` if unknown.
 * @property sampleRate Sample rate in Hz, or `null` if unknown.
 * @property channelCount Number of channels (1 = mono, 2 = stereo), or `null`.
 */
data class AudioTrack(
    val id: String,
    val label: String,
    val language: String?,
    val codec: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val channelCount: Int?,
)

/**
 * Engine-agnostic description of a subtitle / text track.
 *
 * @property id Stable, opaque identifier. See [AudioTrack.id].
 * @property label Human-readable label.
 * @property language BCP-47 language tag, or `null`.
 * @property codec Codec or text format (e.g. `wvtt`, `ttml`), or `null`.
 * @property isForced `true` for forced/auto-displayed subtitles
 * (e.g. foreign-language sections of an otherwise translated track).
 * @property isDefault `true` when the source marks this track as the
 * default selection.
 */
data class SubtitleTrack(
    val id: String,
    val label: String,
    val language: String?,
    val codec: String?,
    val isForced: Boolean,
    val isDefault: Boolean,
)

/**
 * Engine-agnostic description of a video track / rendition.
 *
 * @property id Stable, opaque identifier. See [AudioTrack.id].
 * @property label Human-readable label.
 * @property language BCP-47 language tag, or `null` (rare for video).
 * @property codec Short codec name (e.g. `avc1.640028`, `vp9`), or `null`.
 * @property width Encoded frame width in pixels.
 * @property height Encoded frame height in pixels.
 * @property bitrate Average bitrate in bits-per-second, or `null` if unknown.
 * @property frameRate Frame rate in frames-per-second, or `null` if unknown.
 */
data class VideoTrack(
    val id: String,
    val label: String,
    val language: String?,
    val codec: String?,
    val width: Int,
    val height: Int,
    val bitrate: Int?,
    val frameRate: Float?,
)

// ---------------------------------------------------------------------------------------
// One-time events
// ---------------------------------------------------------------------------------------

/**
 * One-time side effects emitted by the engine. Modelled as a sealed
 * interface so consumers can exhaustively match and the compiler can
 * verify handling.
 *
 * All events are delivered on the engine's internal scope; collectors
 * should hop to the appropriate dispatcher if they perform UI work.
 */
sealed interface MediaEvent {

    /** Playback has transitioned from paused to playing for the first time. */
    data object PlaybackStarted : MediaEvent

    /** Playback has transitioned from playing to paused. */
    data object PlaybackPaused : MediaEvent

    /** Playback reached the end of the source. */
    data object PlaybackCompleted : MediaEvent

    /**
     * A seek operation has completed.
     *
     * @property fromMs Position before the seek, in milliseconds.
     * @property toMs Position after the seek has landed, in milliseconds.
     */
    data class SeekCompleted(val fromMs: Long, val toMs: Long) : MediaEvent

    /**
     * The active track for [type] has changed.
     *
     * @property type The track category that changed.
     * @property trackId ID of the newly selected track, or `null` if the
     * track was cleared (e.g. subtitles disabled).
     */
    data class TrackChanged(val type: TrackType, val trackId: String?) : MediaEvent

    /**
     * An error occurred. The engine is no longer usable for playback
     * of the current source; callers should observe [PlaybackState.phase]
     * and may need to call [MediaEngine.open] again with a new source.
     *
     * @property throwable The underlying cause.
     * @property code Classified error category, useful for analytics and
     * for deciding whether retry is sensible.
     */
    data class Error(val throwable: Throwable, val code: ErrorCode = ErrorCode.Unknown) : MediaEvent

    /**
     * Optional metadata extracted from the source (title, artist, album
     * for audio; show / movie title for video). Emitted once the source
     * has been parsed.
     */
    data class MetadataLoaded(
        val title: String?,
        val artist: String?,
        val album: String? = null,
    ) : MediaEvent
}

/** Category of a track referenced by [MediaEvent.TrackChanged]. */
enum class TrackType { Audio, Subtitle, Video }

/**
 * Coarse-grained error classification. Implementations should map their
 * internal error codes to one of these values so that consumers do not
 * need to know the underlying engine.
 */
enum class ErrorCode {
    /** Failed to open or read the source (e.g. invalid URI, 404, parse error). */
    Source,

    /** Network-level failure (DNS, connection, timeout). */
    Network,

    /** The underlying decoder rejected the stream or ran out of resources. */
    Decoder,

    /** The renderer / surface failed to display frames. */
    Renderer,

    /** Could not classify the failure. Inspect [MediaEvent.Error.throwable]. */
    Unknown,
}
