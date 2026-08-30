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
sealed class MpvEvent {

    // ── Lifecycle events (from MPV event IDs) ─────────────────────────────────
    sealed class Lifecycle : MpvEvent() {
        data object FileLoaded      : Lifecycle()
        data object EndFile         : Lifecycle()
        data object PlaybackRestart : Lifecycle()
        /** Any event id not explicitly handled above. */
        data class  Unknown(val id: Int) : Lifecycle()
    }

    // ── Typed property changes ────────────────────────────────────────────────
    sealed class Property : MpvEvent() {
        data class Position(val ms: Long)           : Property()
        data class Duration(val ms: Long)           : Property()
        data class Paused(val paused: Boolean)      : Property()
        data class Speed(val value: Double)         : Property()
        data class CacheTime(val ms: Long)          : Property()
        data class TrackList(val json: String)      : Property()
        data class HwdecActive(val name: String)    : Property()
        data class SubScale(val value: Double)      : Property()
        data class SubPos(val value: Long)          : Property()
        data class VideoWidth(val px: Long)         : Property()
        data class VideoHeight(val px: Long)        : Property()
        /** Property change not mapped to a typed subclass yet. */
        data class Unknown(val name: String)        : Property()
    }
}
