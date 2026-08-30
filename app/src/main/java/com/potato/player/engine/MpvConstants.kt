package com.potato.player.engine

// ---------------------------------------------------------------------------
// MpvProp — all MPV property name strings in one place.
// Rule: every raw string that goes to MPVLib lives here, nowhere else.
// ---------------------------------------------------------------------------
internal object MpvProp {

    // ── Playback ─────────────────────────────────────────────────────────────
    const val PAUSE    = "pause"
    const val TIME_POS = "time-pos"
    const val DURATION = "duration"
    const val SPEED    = "speed"

    // ── Cache ─────────────────────────────────────────────────────────────────
    const val DEMUXER_CACHE_TIME     = "demuxer-cache-time"
    const val DEMUXER_CACHE_DURATION = "demuxer-cache-duration"

    // ── Decoder ───────────────────────────────────────────────────────────────
    const val HWDEC         = "hwdec"
    const val HWDEC_CURRENT = "hwdec-current"

    // ── Track selection ───────────────────────────────────────────────────────
    const val AID = "aid"
    const val SID = "sid"

    // ── Track list (JSON property observed as a string) ───────────────────────
    const val TRACK_LIST       = "track-list"
    const val TRACK_LIST_COUNT = "track-list/count"

    // Track-list JSON object keys — used by TrackListParser
    const val TRACK_KEY_TYPE     = "type"
    const val TRACK_KEY_ID       = "id"
    const val TRACK_KEY_TITLE    = "title"
    const val TRACK_KEY_LANG     = "lang"
    const val TRACK_KEY_EXTERNAL = "external"

    // ── Subtitles ─────────────────────────────────────────────────────────────
    const val SUB_SCALE = "sub-scale"
    const val SUB_POS   = "sub-pos"

    // ── Video params ──────────────────────────────────────────────────────────
    const val VIDEO_PARAMS_W = "video-params/w"
    const val VIDEO_PARAMS_H = "video-params/h"

    // ── Video transform ───────────────────────────────────────────────────────
    const val VIDEO_ASPECT_OVERRIDE = "video-aspect-override"
    const val VIDEO_ZOOM            = "video-zoom"
    const val VIDEO_PAN_X           = "video-pan-x"
    const val VIDEO_PAN_Y           = "video-pan-y"
    const val PANSCAN               = "panscan"

    // ── Audio ─────────────────────────────────────────────────────────────────
    const val VOLUME = "volume"

    // ── Rendering / surface ───────────────────────────────────────────────────
    const val ANDROID_SURFACE_SIZE = "android-surface-size"
    const val FORCE_WINDOW         = "force-window"
    const val VO                   = "vo"
}

// ---------------------------------------------------------------------------
// MpvEventId — verified against libmpv client.h mpv_event_id enum.
// PLAYBACK_RESTART_21 removed — 21 is not a standard MPV event id.
// ---------------------------------------------------------------------------
internal object MpvEventId {
    const val END_FILE         = 7
    const val FILE_LOADED      = 8
    const val PLAYBACK_RESTART = 15
}
