package com.potato.player.engine

import android.util.Log

object TrackListParser {
    fun parse(raw: String): List<TrackInfo> {
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val typeStr = obj.optString("type", "")
                val type = when (typeStr) {
                    "audio" -> TrackType.AUDIO
                    "sub" -> TrackType.SUBTITLE
                    else -> return@mapNotNull null
                }
                TrackInfo(
                    id = obj.getInt("id"),
                    type = type,
                    title = obj.optString("title", "").takeIf { it.isNotBlank() },
                    lang = obj.optString("lang", "").takeIf { it.isNotBlank() },
                    isExternal = obj.optBoolean("external", false)
                )
            }
        } catch (e: Exception) { 
            Log.e("TrackListParser", "Failed to parse track list", e)
            emptyList() 
        }
    }
}
