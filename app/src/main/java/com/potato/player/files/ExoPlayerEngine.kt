package mediaengine

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import com.potato.player.media.settings.EngineSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * AndroidX Media3 [ExoPlayer] backed implementation of [MediaEngine].
 *
 * All ExoPlayer-specific types, listeners, and configuration are confined
 * to this file. Callers interact exclusively with the engine-agnostic
 * [MediaEngine] surface so the underlying engine can be swapped (e.g. for
 * an FFmpeg-backed implementation) without changing call sites.
 *
 * Threading: ExoPlayer must be touched on the application looper
 * (typically the main thread). Public methods follow that convention.
 * Long-running work is scheduled on [scope] which is bound to
 * [Dispatchers.Main.immediate], so coroutine continuations resume on the
 * same thread that launched them.
 */
class ExoPlayerEngine(
    context: Context,
    private val settings: EngineSettings = EngineSettings(),
) : MediaEngine {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    val playerViewPlayer: Player
        get() = player

    /**
     * Caption style applied to ExoPlayer's SubtitleView at the engine level.
     * PlayerScreen applies the same style to the PlayerView's SubtitleView so both
     * routes (embedded + sidecar) honour it universally.
     */
    val captionStyle: CaptionStyleCompat = CaptionStyleCompat(
        Color.WHITE,
        Color.TRANSPARENT,
        Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
        Color.BLACK,
        null,
    )

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playbackState = MutableStateFlow(PlaybackState.Initial)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _events = MutableSharedFlow<MediaEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
    )
    override val events: SharedFlow<MediaEvent> = _events.asSharedFlow()

    private var positionJob: Job? = null
    private var pendingSeekJob: Job? = null
    private var released: Boolean = false
    private val trackKeyById = mutableMapOf<String, TrackKey>()
    // REF-3: Mutable polling rate; default 250ms, reduced to 100ms during scrub.
    private var positionUpdateIntervalMs: Long = 250L


    private data class TrackKey(
        val type: TrackType,
        val group: TrackGroup,
        val trackIndex: Int,
    )

    private val playerListener: Player.Listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = handleIsPlayingChanged(isPlaying)
        override fun onPlaybackStateChanged(playbackState: Int) =
            handlePlaybackStateChanged(playbackState)
        override fun onPlayerError(error: PlaybackException) = handlePlayerError(error)
        override fun onTracksChanged(tracks: Tracks) = handleTracksChanged(tracks)
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _playbackState.update { it.copy(playbackSpeed = playbackParameters.speed) }
        }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = emitEvent(
            MediaEvent.MetadataLoaded(
                title = mediaMetadata.title?.toString(),
                artist = mediaMetadata.artist?.toString(),
                album = mediaMetadata.albumTitle?.toString(),
            )
        )
    }

    init {
        // Change 2: Auto-select English (or settings-specified) subtitles by default.
        // setSelectUndeterminedTextLanguage ensures a track is chosen even when the
        // container does not tag a language. Parameters survive open() calls because
        // open() only replaces the media item, not track selection parameters.
        val preferredLang = settings.preferredSubtitleLanguage ?: "en"
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setPreferredTextLanguage(preferredLang)
            .setSelectUndeterminedTextLanguage(true)
            .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE)
            .build()

        player.addListener(playerListener)
    }

    // region Lifecycle

    override fun open(uri: Uri) {
        if (released) return
        cancelPendingSeek()
        stopPositionUpdates()
        _playbackState.update { it.copy(phase = MediaPhase.Loading, isPlaying = false) }
        // REF-4: Fully exit previous session before loading a new URI.
        if (player.playbackState != Player.STATE_IDLE) player.stop()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
    }

    override fun play() {
        if (released) return
        when (player.playbackState) {
            Player.STATE_IDLE,
            Player.STATE_ENDED -> return
        }
        player.playWhenReady = true
    }

    override fun pause() {
        if (released) return
        // Stop polling immediately and synchronously, rather than waiting for the
        // async onIsPlayingChanged(false) callback — important for battery on background.
        stopPositionUpdates()
        player.playWhenReady = false
    }

    override fun seekTo(positionMs: Long) {
        if (released) return
        if (player.playbackState == Player.STATE_IDLE) return
        val duration = player.duration
        val target = if (duration <= 0L) {
            positionMs.coerceAtLeast(0L)
        } else {
            positionMs.coerceIn(0L, duration)
        }
        val from = player.currentPosition.coerceAtLeast(0L)
        cancelPendingSeek()
        pendingSeekJob = scope.launch {
            player.seekTo(target)
            delay(SEEK_LANDING_DELAY_MS)
            if (isActive) emitEvent(MediaEvent.SeekCompleted(from, target))
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (released) return
        // REF-1: Clamp to safe range before any other guard.
        val clamped = speed.coerceIn(0.25f, 4.0f)
        if (clamped <= 0f || clamped.isNaN()) return
        // REF-2: Avoid redundant ExoPlayer state churn on long-press hold.
        if (player.playbackParameters.speed == clamped) return
        player.playbackParameters = PlaybackParameters(clamped)
    }

    override fun release() {
        if (released) return
        released = true
        cancelPendingSeek()
        positionJob?.cancel()
        positionJob = null
        player.removeListener(playerListener)
        player.release()
        scope.cancel()
        _playbackState.value = PlaybackState.Initial
    }

    // endregion

    // region Track Management

    override fun selectAudioTrack(trackId: String) =
        selectTrackOverride(trackId, TrackType.Audio) { key ->
            addOverride(TrackSelectionOverride(key.group, key.trackIndex))
        }

    override fun selectSubtitleTrack(trackId: String) =
        selectTrackOverride(trackId, TrackType.Subtitle) { key ->
            setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            addOverride(TrackSelectionOverride(key.group, key.trackIndex))
        }

    override fun selectVideoTrack(trackId: String) =
        selectTrackOverride(trackId, TrackType.Video) { key ->
            addOverride(TrackSelectionOverride(key.group, key.trackIndex))
        }

    override fun clearAudioTrack() =
        clearTrackOverride(TrackType.Audio) {
            clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        }

    override fun clearSubtitleTrack() =
        clearTrackOverride(TrackType.Subtitle) {
            clearOverridesOfType(C.TRACK_TYPE_TEXT)
            setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        }

    override fun clearVideoTrack() =
        clearTrackOverride(TrackType.Video) {
            clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        }

    // endregion

    // region Internal helpers

    private inline fun selectTrackOverride(
        trackId: String,
        type: TrackType,
        configure: TrackSelectionParameters.Builder.(TrackKey) -> Unit,
    ) {
        if (released) return
        val key = trackKeyById[trackId] ?: return
        if (key.type != type) return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .apply { configure(key) }
            .build()
        emitEvent(MediaEvent.TrackChanged(type, trackId))
    }

    private inline fun clearTrackOverride(
        type: TrackType,
        configure: TrackSelectionParameters.Builder.() -> Unit,
    ) {
        if (released) return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .apply(configure)
            .build()
        emitEvent(MediaEvent.TrackChanged(type, null))
    }

    private fun handleIsPlayingChanged(isPlaying: Boolean) {
        _playbackState.update { it.copy(isPlaying = isPlaying) }
        if (isPlaying) {
            emitEvent(MediaEvent.PlaybackStarted)
            startPositionUpdates()
        } else {
            emitEvent(MediaEvent.PlaybackPaused)
            stopPositionUpdates()
            publishPosition()
        }
    }

    private fun handlePlaybackStateChanged(playbackState: Int) {
        val phase: MediaPhase = when (playbackState) {
            Player.STATE_IDLE -> MediaPhase.Idle
            Player.STATE_BUFFERING ->
                if (player.playWhenReady) MediaPhase.Loading
                else MediaPhase.Ready(canPlay = false)
            Player.STATE_READY -> MediaPhase.Ready(canPlay = true)
            Player.STATE_ENDED -> MediaPhase.Ended
            else -> return
        }
        _playbackState.update { it.copy(phase = phase, durationMs = playerDurationMs()) }
        publishPosition()
        if (playbackState == Player.STATE_ENDED) {
            stopPositionUpdates()
            publishPosition()
            emitEvent(MediaEvent.PlaybackCompleted)
        }
        // Change 5 (battery): During buffering while the user has not requested play,
        // do not run the position loop — it would spin at 250ms with no visual benefit.
        // onIsPlayingChanged will (re)start it when isPlaying flips to true.
        if (playbackState == Player.STATE_BUFFERING && !player.playWhenReady) {
            stopPositionUpdates()
        }
    }

    private fun handlePlayerError(error: PlaybackException) {
        emitEvent(MediaEvent.Error(error, error.toErrorCode()))
        _playbackState.update { it.copy(phase = MediaPhase.Idle, isPlaying = false) }
        stopPositionUpdates()
    }

    private fun handleTracksChanged(tracks: Tracks) {
        trackKeyById.clear()
        val params = player.trackSelectionParameters
        val overriddenByType: Map<Int, Set<Pair<TrackGroup, Int>>> = params.overrides.values
            .groupBy { it.mediaTrackGroup.type }
            .mapValues { (_, overrides) ->
                overrides
                    .flatMap { o -> o.trackIndices.map { o.mediaTrackGroup to it } }
                    .toSet()
            }

        val audio = mutableListOf<AudioTrack>()
        val subtitle = mutableListOf<SubtitleTrack>()
        val video = mutableListOf<VideoTrack>()
        var selectedAudioId: String? = null
        var selectedSubtitleId: String? = null
        var selectedVideoId: String? = null

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            val trackGroup = group.mediaTrackGroup
            val overridden = overriddenByType[group.type].orEmpty()
            for (i in 0 until trackGroup.length) {
                val format = trackGroup.getFormat(i)
                val id = buildTrackId(
                    groupType = group.type,
                    groupIndex = groupIndex,
                    format = format,
                    trackIndex = i,
                )
                val type = group.type.toTrackType()
                trackKeyById[id] = TrackKey(type, trackGroup, i)
                val isOverridden = (trackGroup to i) in overridden
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> {
                        audio += buildAudio(id, format)
                        if (isOverridden && selectedAudioId == null) selectedAudioId = id
                    }
                    C.TRACK_TYPE_TEXT -> {
                        subtitle += buildSubtitle(id, format)
                        if (isOverridden && selectedSubtitleId == null) selectedSubtitleId = id
                    }
                    C.TRACK_TYPE_VIDEO -> {
                        video += buildVideo(id, format)
                        if (isOverridden && selectedVideoId == null) selectedVideoId = id
                    }
                }
            }
        }

        // Second pass: if no explicit override was found for a track type, fall back to
        // whichever track ExoPlayer is actually rendering. This covers preference-based
        // auto-selections (e.g. setPreferredTextLanguage) that never produce an override
        // entry in TrackSelectionParameters but still result in a track being active.
        if (selectedAudioId == null || selectedSubtitleId == null || selectedVideoId == null) {
            for ((groupIndex, group) in tracks.groups.withIndex()) {
                if (!group.isSelected) continue
                val trackGroup = group.mediaTrackGroup
                for (i in 0 until trackGroup.length) {
                    if (!group.isTrackSelected(i)) continue
                    val format = trackGroup.getFormat(i)
                    val id = buildTrackId(
                        groupType = group.type,
                        groupIndex = groupIndex,
                        format = format,
                        trackIndex = i,
                    )
                    when (group.type) {
                        C.TRACK_TYPE_AUDIO  -> if (selectedAudioId    == null) selectedAudioId    = id
                        C.TRACK_TYPE_TEXT   -> if (selectedSubtitleId == null) selectedSubtitleId = id
                        C.TRACK_TYPE_VIDEO  -> if (selectedVideoId    == null) selectedVideoId    = id
                    }
                }
            }
        }

        _playbackState.update {
            it.copy(
                audioTracks = audio,
                subtitleTracks = subtitle,
                videoTracks = video,
                selectedAudioTrackId = selectedAudioId,
                selectedSubtitleTrackId = selectedSubtitleId,
                selectedVideoTrackId = selectedVideoId,
            )
        }
    }

    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (true) {
                publishPosition()
                delay(positionUpdateIntervalMs)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    // REF-3: Allows callers to adjust polling rate (e.g., 100ms during scrub, 250ms idle).
    fun setPositionUpdateRate(intervalMs: Long) {
        if (intervalMs == positionUpdateIntervalMs) return // no-op: rate unchanged
        positionUpdateIntervalMs = intervalMs
        if (positionJob?.isActive == true) {
            stopPositionUpdates()
            startPositionUpdates()
        }
    }

    private fun publishPosition() {
        if (released) return
        _playbackState.update {
            it.copy(
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = playerDurationMs(),
                bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
            )
        }
    }

    private fun playerDurationMs(): Long {
        val raw = player.duration
        return if (raw == C.TIME_UNSET) 0L else raw
    }

    private fun cancelPendingSeek() {
        pendingSeekJob?.cancel()
        pendingSeekJob = null
    }

    private fun emitEvent(event: MediaEvent) {
        _events.tryEmit(event)
    }

    private fun PlaybackException.toErrorCode(): ErrorCode = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> ErrorCode.Network

        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> ErrorCode.Source

        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> ErrorCode.Decoder

        PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED -> ErrorCode.Renderer

        else -> ErrorCode.Unknown
    }

    private fun Int.toTrackType(): TrackType = when (this) {
        C.TRACK_TYPE_AUDIO -> TrackType.Audio
        C.TRACK_TYPE_TEXT -> TrackType.Subtitle
        C.TRACK_TYPE_VIDEO -> TrackType.Video
        else -> TrackType.Video
    }

    private fun buildTrackId(
        groupType: Int,
        groupIndex: Int,
        format: Format,
        trackIndex: Int,
    ): String = buildString {
        append(groupType)
        append('_')
        append(groupIndex)
        append('_')
        append(format.id ?: "")
        append('_')
        append(format.language ?: "")
        append('_')
        append(format.sampleMimeType ?: "")
        append('_')
        append(trackIndex)
    }.replace(' ', '_')

    private fun buildAudio(id: String, format: Format): AudioTrack = AudioTrack(
        id = id,
        label = format.label
            ?: format.language?.let { "Audio ($it)" }
            ?: "Audio",
        language = format.language,
        codec = format.codecs ?: format.sampleMimeType,
        bitrate = format.bitrate.takeIf { it > 0 },
        sampleRate = format.sampleRate.takeIf { it > 0 },
        channelCount = format.channelCount.takeIf { it > 0 },
    )

    private fun buildSubtitle(id: String, format: Format): SubtitleTrack = SubtitleTrack(
        id = id,
        label = format.label
            ?: format.language?.let { "Subtitle ($it)" }
            ?: "Subtitle",
        language = format.language,
        codec = format.codecs ?: format.sampleMimeType,
        isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
        isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
    )

    private fun buildVideo(id: String, format: Format): VideoTrack = VideoTrack(
        id = id,
        label = format.label ?: buildString {
            append(format.width.coerceAtLeast(0))
            append('x')
            append(format.height.coerceAtLeast(0))
            if (format.frameRate > 0f) {
                append(" @ ")
                append(format.frameRate.toInt())
                append("fps")
            }
        },
        language = format.language,
        codec = format.codecs ?: format.sampleMimeType,
        width = format.width.coerceAtLeast(0),
        height = format.height.coerceAtLeast(0),
        bitrate = format.bitrate.takeIf { it > 0 },
        frameRate = format.frameRate.takeIf { it > 0f },
    )

    // endregion

    private companion object {
        // REF-5: Centralised log tag; replaces bare string literals.
        private const val TAG = "ExoPlayerEngine"
        // REF-3: Mutable so callers can adjust the rate via setPositionUpdateRate().
        private const val SEEK_LANDING_DELAY_MS = 100L
        private const val EVENT_BUFFER_CAPACITY = 16
    }
}
