package com.potato.player.engine

import android.util.Log

// ---------------------------------------------------------------------------
// TrackListParser — converts the MPV "track-list" JSON string to TrackInfo.
//
// MPV delivers the full track list as a JSON array whenever it changes.
// We filter to audio + subtitle only; video tracks are not surfaced to the UI.
//
// Result type distinguishes:
//   Success(tracks) — parsed OK, tracks may be empty (file has no A/S tracks)
//   Failure         — JSON was malformed; caller should not replace track state
// ---------------------------------------------------------------------------
object TrackListParser {

    sealed class Result {
        data class Success(val tracks: List<TrackInfo>) : Result()
        data object Failure : Result()
    }

    fun parse(raw: String): Result {
        if (raw.isBlank()) return Result.Success(emptyList())
        return try {
            val arr    = org.json.JSONArray(raw)
            val tracks = (0 until arr.length()).mapNotNull { i ->
                val obj     = arr.getJSONObject(i)
                val typeStr = obj.optString(MpvProp.TRACK_KEY_TYPE, "")
                val type    = when (typeStr) {
                    "audio" -> TrackType.AUDIO
                    "sub"   -> TrackType.SUBTITLE
                    else    -> return@mapNotNull null   // skip video and unknown
                }
                val id = obj.optInt(MpvProp.TRACK_KEY_ID, -1)
                if (id == -1) return@mapNotNull null    // malformed entry; skip

                TrackInfo(
                    id         = id,
                    type       = type,
                    title      = obj.optString(MpvProp.TRACK_KEY_TITLE, "").takeIf { it.isNotBlank() },
                    lang       = obj.optString(MpvProp.TRACK_KEY_LANG,  "").takeIf { it.isNotBlank() },
                    isExternal = obj.optBoolean(MpvProp.TRACK_KEY_EXTERNAL, false)
                )
            }
            Result.Success(tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse track list", e)
            Result.Failure
        }
    }

    private const val TAG = "TrackListParser"
}
