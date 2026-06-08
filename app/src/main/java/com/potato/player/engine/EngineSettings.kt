package com.potato.player.media.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ================================================================================================
// Domain Enums
// ================================================================================================

/**
 * Defines the decoder selection strategy for the underlying playback engine.
 *
 * This abstraction allows the same UI and ViewModel code to drive both ExoPlayer
 * (MediaCodec) and future FFmpeg-based backends without modification.
 */
enum class DecoderMode {
    /** Prefer hardware-accelerated codecs (e.g., MediaCodec, OMX, VideoToolbox). */
    HARDWARE,

    /** Force software decoding (e.g., FFmpeg libavcodec, libdav1d). */
    SOFTWARE,

    /** Let the engine inspect device capability and media format to decide. */
    AUTO
}

/**
 * Semantic subtitle size tiers.
 *
 * The actual pixel/text size is determined by the UI layer (Compose/TextView) or the
 * rendering backend (ExoPlayer CaptionStyle / FFmpeg ASS renderer). This layer only
 * communicates user intent.
 */
enum class SubtitleSize {
    SMALL,
    MEDIUM,
    LARGE,
    EXTRA_LARGE
}

// ================================================================================================
// Immutable Settings Aggregate
// ================================================================================================

/**
 * Immutable snapshot of all user-tunable media engine preferences.
 *
 * This aggregate is the contract between the UI/ViewModel and any playback backend.
 * All fields have safe defaults so that the object can be instantiated with no arguments.
 *
 * @property playbackSpeed Multiplier applied to natural playback rate. Clamped to
 *                         [[MIN_PLAYBACK_SPEED], [MAX_PLAYBACK_SPEED]].
 * @property preferredAudioLanguage ISO 639-1 or BCP 47 language code used to auto-select
 *                                  the best matching audio track on [MediaEngine.open].
 *                                  Null means "no preference".
 * @property preferredSubtitleLanguage ISO 639-1 or BCP 47 language code used to auto-select
 *                                     the best matching subtitle track on [MediaEngine.open].
 *                                     Null means "no preference".
 * @property subtitlesEnabled Global toggle for subtitle rendering. When false, no subtitle
 *                           track should be rendered regardless of selection state.
 * @property subtitleSize User-selected subtitle text size tier.
 * @property decoderMode Hint to the engine for codec selection.
 */
data class EngineSettings(
    val playbackSpeed: Float = DEFAULT_PLAYBACK_SPEED,
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val subtitlesEnabled: Boolean = DEFAULT_SUBTITLES_ENABLED,
    val subtitleSize: SubtitleSize = DEFAULT_SUBTITLE_SIZE,
    val decoderMode: DecoderMode = DEFAULT_DECODER_MODE,
) {
    init {
        require(playbackSpeed in MIN_PLAYBACK_SPEED..MAX_PLAYBACK_SPEED) {
            "playbackSpeed must be in [$MIN_PLAYBACK_SPEED, $MAX_PLAYBACK_SPEED], was $playbackSpeed"
        }
    }

    companion object {
        const val MIN_PLAYBACK_SPEED = 0.25f
        const val MAX_PLAYBACK_SPEED = 4.0f
        const val DEFAULT_PLAYBACK_SPEED = 1.0f
        const val DEFAULT_SUBTITLES_ENABLED = true
        val DEFAULT_SUBTITLE_SIZE = SubtitleSize.MEDIUM
        val DEFAULT_DECODER_MODE = DecoderMode.AUTO
    }
}

// ================================================================================================
// Persistence Abstraction
// ================================================================================================

/**
 * Port for persisting and retrieving [EngineSettings].
 *
 * Implementations are provided at the outer layer (e.g., an Android DataStore wrapper,
 * SharedPreferences adapter, or in-memory fake for tests). This keeps the settings domain
 * free of framework-specific serialization code.
 */
interface SettingsStore {

    /**
     * Reads the previously persisted settings.
     *
     * @return The stored [EngineSettings], or defaults if no record exists yet.
     * @throws Exception Implementations may throw on disk corruption or IO failure.
     */
    suspend fun load(): EngineSettings

    /**
     * Atomically persists [settings].
     *
     * @param settings The snapshot to save. Implementations must overwrite any prior data.
     * @throws Exception Implementations may throw on disk failure.
     */
    suspend fun save(settings: EngineSettings)
}

// ================================================================================================
// Settings Manager
// ================================================================================================

/**
 * Single source of truth for playback preferences.
 *
 * Responsibilities:
 * 1. Hold the canonical in-memory [EngineSettings] state as a hot [StateFlow].
 * 2. Hydrate state from [SettingsStore] on creation.
 * 3. Accept granular or batched updates and reflect them immediately in the flow.
 * 4. Persist changes asynchronously without blocking the caller.
 *
 * Threading: All public methods are safe to call from any thread. StateFlow emissions are
 * serialized. Persistence runs in [externalScope], which should use a dispatcher suited
 * for IO (e.g., `Dispatchers.IO`).
 *
 * @param store The persistence port.
 * @param externalScope A [CoroutineScope] that outlives individual UI sessions. The manager
 *                    will launch fire-and-forget save operations in this scope.
 */
class EngineSettingsManager(
    private val store: SettingsStore,
    private val externalScope: CoroutineScope,
) {

    private val _settings = MutableStateFlow(EngineSettings())

    /**
     * Reactive stream of the current settings.
     *
     * Emits the current value immediately upon collection, then on every subsequent update.
     * Safe to collect from the main thread for direct Compose consumption.
     */
    val settings: StateFlow<EngineSettings> = _settings.asStateFlow()

    init {
        externalScope.launch {
            _settings.value = try {
                store.load()
            } catch (_: Exception) {
                // If the backing store is unreadable (corruption, migration mismatch, etc.),
                // fall back to defaults and allow the next user-driven update to overwrite.
                EngineSettings()
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // Batch Update API
    // --------------------------------------------------------------------------------------------

    /**
     * Atomically transforms the current settings using [transform].
     *
     * The in-memory state updates immediately (synchronously). Persistence is dispatched
     * asynchronously in [externalScope]. If persistence fails, the in-memory state is still
     * retained so the UI never rolls back.
     *
     * @param transform A pure function that receives the current [EngineSettings] and returns
     *                  the next state. Invalid states (e.g., playback speed out of bounds)
     *                  will cause [IllegalArgumentException] to be thrown and the update
     *                  will be rejected.
     */
    fun update(transform: (EngineSettings) -> EngineSettings) {
        _settings.update { current ->
            val next = transform(current)
            externalScope.launch {
                try {
                    store.save(next)
                } catch (_: Exception) {
                    // Swallow persistence failures for settings. A production app may wish to
                    // emit to a monitoring channel or retry with exponential backoff.
                }
            }
            next
        }
    }

    // --------------------------------------------------------------------------------------------
    // Granular Convenience Mutators
    // --------------------------------------------------------------------------------------------

    /** Sets the playback speed, clamped to the valid domain range. */
    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(EngineSettings.MIN_PLAYBACK_SPEED, EngineSettings.MAX_PLAYBACK_SPEED)
        update { it.copy(playbackSpeed = clamped) }
    }

    /** Sets the preferred audio language tag (e.g., "en", "ja", null). */
    fun setPreferredAudioLanguage(language: String?) {
        update { it.copy(preferredAudioLanguage = language) }
    }

    /** Sets the preferred subtitle language tag (e.g., "en", "fr", null). */
    fun setPreferredSubtitleLanguage(language: String?) {
        update { it.copy(preferredSubtitleLanguage = language) }
    }

    /** Toggles global subtitle visibility. */
    fun setSubtitlesEnabled(enabled: Boolean) {
        update { it.copy(subtitlesEnabled = enabled) }
    }

    /** Sets the subtitle size tier. */
    fun setSubtitleSize(size: SubtitleSize) {
        update { it.copy(subtitleSize = size) }
    }

    /** Sets the decoder selection strategy. */
    fun setDecoderMode(mode: DecoderMode) {
        update { it.copy(decoderMode = mode) }
    }

    /** Resets every field to its factory default. */
    fun resetToDefaults() {
        update { EngineSettings() }
    }
}
