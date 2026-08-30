package com.potato.player.engine

// ---------------------------------------------------------------------------
// MpvEvent — typed engine event model.
//
// Two tiers:
//
//   1. Lifecycle  — important, must never be dropped (END_FILE, FILE_LOADED…)
//   2. Property   — high-frequency, safe to coalesce/miss (time-pos, speed…)
//
// The upper layer (MpvEventProcessor) consumes these. It should never need to
// match raw property name strings — add a new typed subclass instead.
// ---------------------------------------------------------------------------

/**
 * High-frequency properties coalesced into a single state object.
 * Collectors always see the latest value; stale intermediate values are never processed.
 */
data class PlayerEngineState(
    val positionMs:    Long    = 0L,
    val durationMs:    Long    = 0L,
    val cacheTimeMs:   Long    = 0L,
    val cacheDurMs:    Long    = 0L,
    val pausedForCache: Boolean = false,
    val cacheBufferingState: Int = 100,
    val paused:        Boolean = false,
    val speed:         Double  = 1.0,
    val subScale:      Double  = 1.0,
    val subPos:        Long    = 100L,
    val videoWidth:    Long    = 0L,
    val videoHeight:   Long    = 0L,
    val hwdecActive:   String  = "",
    val trackListJson: String  = ""
)

sealed class MpvEvent {

    // ── Lifecycle events (from MPV event IDs) ─────────────────────────────────
    sealed class Lifecycle : MpvEvent() {
        data object FileLoaded      : Lifecycle()
        data class EndFile(val reason: Int) : Lifecycle()
        data object PlaybackRestart : Lifecycle()
        /** Any event id not explicitly handled above. */
        data class  Unknown(val id: Int) : Lifecycle()
    }
}
